/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.core5.http2.nio.pool;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.StaleCheckCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.impl.nio.H2PoolSessionSupport;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 stream-capacity-aware connection pool. Tracks active and
 * reserved streams per connection, honours the peer's
 * {@code MAX_CONCURRENT_STREAMS}, opens additional connections when
 * existing ones are saturated, and drains connections on
 * {@code GOAWAY}. Routes blocked by {@code maxTotal} are queued in
 * a global FIFO to avoid scanning all routes on every release.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.SAFE)
class H2MultiplexingConnPool implements H2RequesterConnPool {

    private final ConnectionInitiator connectionInitiator;
    private final Resolver<HttpHost, InetSocketAddress> addressResolver;
    private final TlsStrategy tlsStrategy;

    private final ConcurrentHashMap<HttpHost, RoutePool> routePools;
    private final ConcurrentLinkedQueue<QueuedRoute> globallyBlockedRoutes;

    private final AtomicBoolean closed;
    private final AtomicInteger totalConnectionCount;
    private final ReentrantReadWriteLock lifecycleLock;

    private final int defaultMaxPerRoute;
    private final int maxTotal;
    private volatile TimeValue validateAfterInactivity;

    private static final int DEFAULT_MAX_PER_ROUTE = 3;
    private static final int DEFAULT_MAX_TOTAL = 25;

    H2MultiplexingConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy) {
        this(connectionInitiator, addressResolver, tlsStrategy, DEFAULT_MAX_PER_ROUTE, DEFAULT_MAX_TOTAL, null);
    }

    H2MultiplexingConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final int defaultMaxPerRoute,
            final int maxTotal) {
        this(connectionInitiator, addressResolver, tlsStrategy, defaultMaxPerRoute, maxTotal, null);
    }

    H2MultiplexingConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final int defaultMaxPerRoute,
            final int maxTotal,
            final TimeValue validateAfterInactivity) {
        this.connectionInitiator = Args.notNull(connectionInitiator, "Connection initiator");
        this.addressResolver = addressResolver != null ? addressResolver : DefaultAddressResolver.INSTANCE;
        this.tlsStrategy = tlsStrategy;
        this.routePools = new ConcurrentHashMap<>();
        this.globallyBlockedRoutes = new ConcurrentLinkedQueue<>();
        this.closed = new AtomicBoolean();
        this.totalConnectionCount = new AtomicInteger(0);
        this.lifecycleLock = new ReentrantReadWriteLock(true);
        this.defaultMaxPerRoute = defaultMaxPerRoute > 0 ? defaultMaxPerRoute : DEFAULT_MAX_PER_ROUTE;
        this.maxTotal = maxTotal > 0 ? maxTotal : DEFAULT_MAX_TOTAL;
        this.validateAfterInactivity = validateAfterInactivity != null ? validateAfterInactivity : TimeValue.NEG_ONE_MILLISECOND;
    }

    @Override
    public Set<HttpHost> getRoutes() {
        return new HashSet<>(routePools.keySet());
    }

    @Override
    public void setValidateAfterInactivity(final TimeValue timeValue) {
        this.validateAfterInactivity = timeValue != null ? timeValue : TimeValue.NEG_ONE_MILLISECOND;
    }

    RoutePool getRoutePool(final HttpHost endpoint) {
        return routePools.computeIfAbsent(endpoint, key -> new RoutePool());
    }

    private boolean tryReserveGlobalSlot() {
        for (;;) {
            if (closed.get()) {
                return false;
            }
            final int current = totalConnectionCount.get();
            if (current >= maxTotal) {
                return false;
            }
            if (totalConnectionCount.compareAndSet(current, current + 1)) {
                if (closed.get()) {
                    totalConnectionCount.decrementAndGet();
                    return false;
                }
                return true;
            }
        }
    }

    private void releaseGlobalSlot() {
        if (!closed.get()) {
            totalConnectionCount.decrementAndGet();
        }
    }

    private boolean routeHasRoom(final RoutePool routePool) {
        int count = routePool.connectInFlight ? 1 : 0;
        for (final ConnectionEntry entry : routePool.entries) {
            if (entry.countsTowardRouteLimit()) {
                count++;
            }
        }
        return count < defaultMaxPerRoute;
    }

    /**
     * Removes {@code routePool} from the route map when it holds no state that
     * would require further servicing. Must be called while holding
     * {@code routePool.lock}.
     */
    private void pruneRoutePoolIfUnused(final HttpHost endpoint, final RoutePool routePool) {
        if (routePool.entries.isEmpty()
                && routePool.pendingRequests.isEmpty()
                && !routePool.connectInFlight
                && !routePool.globallyQueued) {
            routePools.remove(endpoint, routePool);
        }
    }

    /**
     * Acquires the per-endpoint route pool with its lock held. Re-fetches if a
     * concurrent prune has removed the candidate from the route map, so no
     * caller operates on a detached pool.
     */
    private RoutePool acquireRoutePool(final HttpHost endpoint) {
        for (;;) {
            final RoutePool candidate = getRoutePool(endpoint);
            candidate.lock.lock();
            if (routePools.get(endpoint) == candidate) {
                return candidate;
            }
            candidate.lock.unlock();
        }
    }

    private boolean markConnectInFlightIfPossible(final RoutePool routePool) {
        if (!routePool.connectInFlight && routeHasRoom(routePool) && tryReserveGlobalSlot()) {
            routePool.connectInFlight = true;
            return true;
        }
        return false;
    }

    Future<H2StreamLease> lease(
            final HttpHost endpoint,
            final Timeout connectTimeout,
            final FutureCallback<H2StreamLease> callback) {
        Args.notNull(endpoint, "Endpoint");
        final BasicFuture<H2StreamLease> future = new BasicFuture<>(callback);

        final ReentrantReadWriteLock.ReadLock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            if (closed.get()) {
                future.failed(new IllegalStateException("Connection pool shut down"));
                return future;
            }

            final RoutePool routePool = acquireRoutePool(endpoint);
            ConnectionEntry reserved = null;
            boolean startDial = false;
            int purged = 0;

            try {
                if (closed.get()) {
                    future.failed(new IllegalStateException("Connection pool shut down"));
                    return future;
                }

                reserved = tryFastReserve(routePool);
                if (reserved == null) {
                    purged = purgeDeadEntries(routePool);

                    reserved = tryFastReserve(routePool);
                    if (reserved == null) {
                        final PendingLease pending = new PendingLease(future, connectTimeout);

                        if (routePool.connectInFlight) {
                            routePool.pendingRequests.add(pending);
                            return future;
                        }

                        reserved = routePool.reserveAvailable();
                        if (reserved == null) {
                            if (markConnectInFlightIfPossible(routePool)) {
                                routePool.pendingRequests.add(pending);
                                startDial = true;
                            } else {
                                routePool.pendingRequests.add(pending);
                                if (routeHasRoom(routePool) && totalConnectionCount.get() >= maxTotal) {
                                    enqueueGloballyBlockedRoute(endpoint, routePool);
                                }
                            }
                        }
                    }
                }
            } finally {
                routePool.lock.unlock();
            }

            if (startDial) {
                startConnect(endpoint, connectTimeout, routePool);
            } else if (reserved != null) {
                completeLeaseWithValidation(endpoint, routePool, reserved, future, connectTimeout);
            }

            if (purged > 0) {
                processGlobalPending();
            }

            return future;
        } finally {
            readLock.unlock();
        }
    }

    private ConnectionEntry tryFastReserve(final RoutePool routePool) {
        if (routePool.connectInFlight || !routePool.pendingRequests.isEmpty() || routePool.entries.size() != 1) {
            return null;
        }
        final ConnectionEntry entry = routePool.entries.get(0);
        final int capacity = entry.availableCapacity();
        if (capacity > 0) {
            entry.reserved.incrementAndGet();
            entry.lastUsed = System.currentTimeMillis();
            return entry;
        }
        return null;
    }

    private void completeLeaseWithValidation(
            final HttpHost endpoint,
            final RoutePool routePool,
            final ConnectionEntry entry,
            final BasicFuture<H2StreamLease> future,
            final Timeout connectTimeout) {
        final IOSession session = entry.session;
        final TimeValue timeValue = validateAfterInactivity;
        if (TimeValue.isNonNegative(timeValue) && session.isOpen()) {
            final long lastIo = Math.max(session.getLastReadTime(), session.getLastWriteTime());
            final long deadline = lastIo + timeValue.toMilliseconds();
            if (deadline <= System.currentTimeMillis()) {
                session.enqueue(new StaleCheckCommand(valid -> {
                    if (valid) {
                        finalizeLease(endpoint, routePool, entry, future, connectTimeout);
                    } else {
                        retryAfterValidationFailure(endpoint, routePool, entry, future, connectTimeout);
                    }
                }), Command.Priority.NORMAL);
                return;
            }
        }
        finalizeLease(endpoint, routePool, entry, future, connectTimeout);
    }

    private void finalizeLease(
            final HttpHost endpoint,
            final RoutePool routePool,
            final ConnectionEntry entry,
            final BasicFuture<H2StreamLease> future,
            final Timeout connectTimeout) {
        final ReentrantReadWriteLock.ReadLock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            H2StreamLease lease = null;
            Exception failure = null;
            boolean retry = false;

            routePool.lock.lock();
            try {
                if (closed.get()) {
                    if (entry.reserved.get() > 0) {
                        entry.reserved.decrementAndGet();
                    }
                    failure = new IllegalStateException("Connection pool shut down");
                } else if (future.isDone()) {
                    if (entry.reserved.get() > 0) {
                        entry.reserved.decrementAndGet();
                    }
                    return;
                } else if (!routePool.entries.contains(entry)
                        || !entry.session.isOpen()
                        || entry.draining) {
                    retry = true;
                } else {
                    lease = createLease(endpoint, routePool, entry);
                }
            } finally {
                routePool.lock.unlock();
            }

            if (failure != null) {
                future.failed(failure);
                return;
            }

            if (retry) {
                retryAfterValidationFailure(endpoint, routePool, entry, future, connectTimeout);
                return;
            }

            if (!future.completed(lease)) {
                releaseReservation(endpoint, routePool, entry);
            }
        } finally {
            readLock.unlock();
        }
    }

    private void retryAfterValidationFailure(
            final HttpHost endpoint,
            final RoutePool routePool,
            final ConnectionEntry entry,
            final BasicFuture<H2StreamLease> future,
            final Timeout connectTimeout) {
        boolean startDial = false;
        final boolean evicted;
        ConnectionEntry retryEntry;

        routePool.lock.lock();
        try {
            if (entry.reserved.get() > 0) {
                entry.reserved.decrementAndGet();
            }
            entry.draining = true;
            evicted = evictIfDead(routePool, entry);

            if (closed.get() || future.isDone()) {
                return;
            }

            retryEntry = tryFastReserve(routePool);
            if (retryEntry == null) {
                retryEntry = routePool.reserveAvailable();
            }
            if (retryEntry == null) {
                final PendingLease pending = new PendingLease(future, connectTimeout);
                if (markConnectInFlightIfPossible(routePool)) {
                    routePool.pendingRequests.addFirst(pending);
                    startDial = true;
                } else {
                    routePool.pendingRequests.addFirst(pending);
                    if (routeHasRoom(routePool) && totalConnectionCount.get() >= maxTotal) {
                        enqueueGloballyBlockedRoute(endpoint, routePool);
                    }
                }
            }
        } finally {
            routePool.lock.unlock();
        }

        if (startDial) {
            startConnect(endpoint, connectTimeout, routePool);
        } else if (retryEntry != null) {
            completeLeaseWithValidation(endpoint, routePool, retryEntry, future, connectTimeout);
        }

        if (evicted) {
            processGlobalPending();
        }
    }

    private H2StreamLease createLease(
            final HttpHost endpoint,
            final RoutePool routePool,
            final ConnectionEntry entry) {
        return new H2StreamLease(entry.session, () -> releaseReservation(endpoint, routePool, entry));
    }

    private void releaseReservation(
            final HttpHost endpoint,
            final RoutePool routePool,
            final ConnectionEntry entry) {
        final List<Runnable> completions;
        routePool.lock.lock();
        try {
            if (entry.reserved.get() > 0) {
                entry.reserved.decrementAndGet();
            }
            entry.lastUsed = System.currentTimeMillis();
            if (!entry.observerRegistered.get()) {
                registerObserver(endpoint, routePool, entry);
            }
            completions = routePool.pendingRequests.isEmpty() ?
                    Collections.emptyList() :
                    drainPending(endpoint, routePool, true);
        } finally {
            routePool.lock.unlock();
        }
        fireCompletions(completions);
    }

    private static void purgeCancelledWaiters(final RoutePool routePool) {
        while (!routePool.pendingRequests.isEmpty() && routePool.pendingRequests.peek().future.isDone()) {
            routePool.pendingRequests.poll();
        }
    }

    private List<Runnable> drainPending(
            final HttpHost endpoint,
            final RoutePool routePool,
            final boolean startNewConnect) {
        final List<Runnable> completions = new ArrayList<>();
        purgeCancelledWaiters(routePool);

        while (!routePool.pendingRequests.isEmpty()) {
            ConnectionEntry entry = tryFastReserve(routePool);
            if (entry == null) {
                entry = routePool.reserveAvailable();
            }
            if (entry == null) {
                break;
            }

            final PendingLease pending = routePool.pendingRequests.poll();
            if (pending != null && !pending.future.isDone()) {
                final ConnectionEntry captured = entry;
                final Timeout timeout = pending.connectTimeout;
                completions.add(() -> completeLeaseWithValidation(
                        endpoint, routePool, captured, pending.future, timeout));
            } else {
                if (entry.reserved.get() > 0) {
                    entry.reserved.decrementAndGet();
                }
            }
        }

        if (startNewConnect) {
            purgeCancelledWaiters(routePool);
            if (!routePool.pendingRequests.isEmpty() && markConnectInFlightIfPossible(routePool)) {
                final Timeout timeout = routePool.pendingRequests.peek().connectTimeout;
                completions.add(() -> startConnect(endpoint, timeout, routePool));
            } else if (!routePool.pendingRequests.isEmpty()
                    && routeHasRoom(routePool) && totalConnectionCount.get() >= maxTotal) {
                enqueueGloballyBlockedRoute(endpoint, routePool);
            }
        }

        return completions;
    }

    private int purgeDeadEntries(final RoutePool routePool) {
        int purged = 0;
        final Iterator<ConnectionEntry> it = routePool.entries.iterator();
        while (it.hasNext()) {
            final ConnectionEntry entry = it.next();
            if (!entry.session.isOpen() && entry.reserved.get() <= 0) {
                it.remove();
                entry.support.setObserver(null);
                releaseGlobalSlot();
                purged++;
            }
        }
        return purged;
    }

    private void enqueueGloballyBlockedRoute(
            final HttpHost endpoint,
            final RoutePool routePool) {
        if (!routePool.globallyQueued
                && !routePool.pendingRequests.isEmpty()
                && !routePool.connectInFlight
                && routeHasRoom(routePool)) {
            routePool.globallyQueued = true;
            globallyBlockedRoutes.add(new QueuedRoute(endpoint, routePool));
        }
    }

    /**
     * Wake routes blocked by maxTotal without scanning every route.
     */
    private void processGlobalPending() {
        if (closed.get()) {
            return;
        }

        final List<Runnable> starts = new ArrayList<>();

        while (!closed.get() && totalConnectionCount.get() < maxTotal) {
            final QueuedRoute queuedRoute = globallyBlockedRoutes.poll();
            if (queuedRoute == null) {
                break;
            }

            final HttpHost endpoint = queuedRoute.endpoint;
            final RoutePool routePool = queuedRoute.routePool;
            Timeout connectTimeout = null;

            routePool.lock.lock();
            try {
                routePool.globallyQueued = false;
                purgeCancelledWaiters(routePool);

                if (closed.get()) {
                    continue;
                }
                if (routePools.get(endpoint) != routePool) {
                    continue;
                }
                if (routePool.pendingRequests.isEmpty()) {
                    continue;
                }

                if (markConnectInFlightIfPossible(routePool)) {
                    connectTimeout = routePool.pendingRequests.peek().connectTimeout;
                } else if (totalConnectionCount.get() >= maxTotal) {
                    enqueueGloballyBlockedRoute(endpoint, routePool);
                    break;
                }
            } finally {
                routePool.lock.unlock();
            }

            if (connectTimeout != null) {
                final Timeout capturedTimeout = connectTimeout;
                starts.add(() -> startConnect(endpoint, capturedTimeout, routePool));
            } else {
                break;
            }
        }

        fireCompletions(starts);
    }

    private void startConnect(
            final HttpHost endpoint,
            final Timeout connectTimeout,
            final RoutePool routePool) {
        final ReentrantReadWriteLock.ReadLock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            if (closed.get()) {
                return;
            }

            final H2PoolSessionSupport support = new H2PoolSessionSupport();
            final InetSocketAddress remoteAddress = addressResolver.resolve(endpoint);
            connectionInitiator.connect(
                    endpoint,
                    remoteAddress,
                    null,
                    connectTimeout,
                    support,
                    new FutureCallback<IOSession>() {

                        @Override
                        public void completed(final IOSession ioSession) {
                            if (tlsStrategy != null
                                    && URIScheme.HTTPS.same(endpoint.getSchemeName())
                                    && ioSession instanceof TransportSecurityLayer) {
                                try {
                                    tlsStrategy.upgrade(
                                            (TransportSecurityLayer) ioSession,
                                            endpoint,
                                            null,
                                            connectTimeout,
                                            new FutureCallback<TransportSecurityLayer>() {

                                                @Override
                                                public void completed(final TransportSecurityLayer transportSecurityLayer) {
                                                    onSessionReady(endpoint, ioSession, routePool, support);
                                                }

                                                @Override
                                                public void failed(final Exception ex) {
                                                    ioSession.close(CloseMode.IMMEDIATE);
                                                    onConnectFailed(endpoint, routePool, ex);
                                                }

                                                @Override
                                                public void cancelled() {
                                                    ioSession.close(CloseMode.IMMEDIATE);
                                                    onConnectFailed(endpoint, routePool, new InterruptedException());
                                                }
                                            });
                                    ioSession.setSocketTimeout(connectTimeout);
                                } catch (final RuntimeException ex) {
                                    ioSession.close(CloseMode.IMMEDIATE);
                                    onConnectFailed(endpoint, routePool, ex);
                                }
                            } else {
                                onSessionReady(endpoint, ioSession, routePool, support);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            onConnectFailed(endpoint, routePool, ex);
                        }

                        @Override
                        public void cancelled() {
                            onConnectFailed(endpoint, routePool, new InterruptedException());
                        }
                    });
        } catch (final RuntimeException ex) {
            onConnectFailed(endpoint, routePool, ex);
        } finally {
            readLock.unlock();
        }
    }

    void onSessionReady(
            final HttpHost endpoint,
            final IOSession ioSession,
            final RoutePool routePool,
            final H2PoolSessionSupport support) {
        boolean closeSession = false;
        List<Runnable> completions = null;

        routePool.lock.lock();
        try {
            routePool.connectInFlight = false;
            routePool.globallyQueued = false;

            if (closed.get()) {
                closeSession = true;
            } else {
                final ConnectionEntry entry = new ConnectionEntry(ioSession, support);
                routePool.entries.add(entry);
                registerObserver(endpoint, routePool, entry);
                completions = drainPending(endpoint, routePool, true);
            }
        } finally {
            routePool.lock.unlock();
        }

        if (closeSession) {
            ioSession.close(CloseMode.GRACEFUL);
            return;
        }
        fireCompletions(completions);
    }

    void onConnectFailed(
            final HttpHost endpoint,
            final RoutePool routePool,
            final Exception ex) {
        final List<Runnable> completions;
        final List<PendingLease> failed;
        boolean startDial = false;
        Timeout retryTimeout = null;

        routePool.lock.lock();
        try {
            routePool.connectInFlight = false;

            if (closed.get()) {
                return;
            }

            releaseGlobalSlot();
            completions = drainPending(endpoint, routePool, false);

            if (!completions.isEmpty()) {
                failed = null;
            } else if (!routePool.pendingRequests.isEmpty() && routePool.entries.isEmpty()) {
                failed = new ArrayList<>();
                PendingLease pending;
                while ((pending = routePool.pendingRequests.poll()) != null) {
                    failed.add(pending);
                }
            } else {
                failed = null;
                if (!routePool.pendingRequests.isEmpty()) {
                    if (markConnectInFlightIfPossible(routePool)) {
                        retryTimeout = routePool.pendingRequests.peek().connectTimeout;
                        startDial = true;
                    } else if (routeHasRoom(routePool) && totalConnectionCount.get() >= maxTotal) {
                        enqueueGloballyBlockedRoute(endpoint, routePool);
                    }
                }
            }
            pruneRoutePoolIfUnused(endpoint, routePool);
        } finally {
            routePool.lock.unlock();
        }

        fireCompletions(completions);

        if (failed != null) {
            for (final PendingLease pending : failed) {
                pending.future.failed(ex);
            }
        }

        if (startDial) {
            startConnect(endpoint, retryTimeout, routePool);
        }

        processGlobalPending();
    }

    private void registerObserver(
            final HttpHost endpoint,
            final RoutePool routePool,
            final ConnectionEntry entry) {
        if (!entry.observerRegistered.compareAndSet(false, true)) {
            return;
        }

        entry.support.setObserver(new H2PoolSessionSupport.Observer() {

            @Override
            public void onCapacityAvailable() {
                if (closed.get()) {
                    return;
                }
                final List<Runnable> completions;
                routePool.lock.lock();
                try {
                    if (entry.reserved.get() > 0) {
                        reconcileReserved(entry);
                    }
                    entry.lastUsed = System.currentTimeMillis();
                    completions = routePool.pendingRequests.isEmpty() ?
                            Collections.emptyList() :
                            drainPending(endpoint, routePool, true);
                } finally {
                    routePool.lock.unlock();
                }
                fireCompletions(completions);
            }

            @Override
            public void onDraining() {
                if (closed.get()) {
                    return;
                }

                final List<Runnable> completions;
                final boolean evicted;

                routePool.lock.lock();
                try {
                    entry.draining = true;
                    evicted = evictIfDead(routePool, entry);
                    completions = routePool.pendingRequests.isEmpty() ?
                            Collections.emptyList() :
                            drainPending(endpoint, routePool, true);
                    if (evicted) {
                        pruneRoutePoolIfUnused(endpoint, routePool);
                    }
                } finally {
                    routePool.lock.unlock();
                }

                fireCompletions(completions);

                if (evicted) {
                    processGlobalPending();
                }
            }

            @Override
            public void onSessionClosed() {
                if (closed.get()) {
                    return;
                }

                final List<Runnable> completions;
                final boolean evicted;

                routePool.lock.lock();
                try {
                    entry.draining = true;
                    entry.support.setObserver(null);
                    // Session is dead. Reservations and active streams on it
                    // are void, so evict unconditionally. Any caller still
                    // holding a reservation will see the entry invalidated in
                    // finalizeLease and retry.
                    if (routePool.entries.remove(entry)) {
                        releaseGlobalSlot();
                        evicted = true;
                    } else {
                        evicted = false;
                    }
                    completions = routePool.pendingRequests.isEmpty() ?
                            Collections.emptyList() :
                            drainPending(endpoint, routePool, true);
                    if (evicted) {
                        pruneRoutePoolIfUnused(endpoint, routePool);
                    }
                } finally {
                    routePool.lock.unlock();
                }

                fireCompletions(completions);

                if (evicted) {
                    processGlobalPending();
                }
            }
        });
    }

    /**
     * Reconciles the entry's {@code reserved} counter against the current
     * active-local-stream count reported by the multiplexer. Each new active
     * stream observed since the last sample consumes one reservation, which
     * atomically transitions a slot from reserved to active without a
     * separate handshake from the caller. Always invoked while holding
     * {@code routePool.lock}.
     */
    private static void reconcileReserved(final ConnectionEntry entry) {
        final int currentActive = entry.support.getActiveLocalStreams();
        final int previous = entry.lastSeenActive.getAndSet(currentActive);
        final int delta = currentActive - previous;
        if (delta <= 0) {
            return;
        }
        for (;;) {
            final int current = entry.reserved.get();
            if (current <= 0) {
                return;
            }
            final int consume = Math.min(current, delta);
            if (entry.reserved.compareAndSet(current, current - consume)) {
                return;
            }
        }
    }

    private boolean evictIfDead(
            final RoutePool routePool,
            final ConnectionEntry entry) {
        if (closed.get()) {
            return false;
        }
        if (!entry.draining) {
            return false;
        }
        if (entry.reserved.get() > 0) {
            return false;
        }

        if (entry.support.getActiveLocalStreams() > 0) {
            return false;
        }

        if (routePool.entries.remove(entry)) {
            entry.support.setObserver(null);
            releaseGlobalSlot();
            closeSession(entry.session, CloseMode.GRACEFUL);
            return true;
        }
        return false;
    }

    private void closeSession(
            final IOSession ioSession,
            final CloseMode closeMode) {
        if (closeMode == CloseMode.GRACEFUL) {
            ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
        } else {
            ioSession.close(closeMode);
        }
    }

    public void closeIdle(final TimeValue idleTime) {
        final long deadline = System.currentTimeMillis()
                - (TimeValue.isPositive(idleTime) ? idleTime.toMilliseconds() : 0);
        boolean freedCapacity = false;

        for (final Map.Entry<HttpHost, RoutePool> mapEntry : routePools.entrySet()) {
            final HttpHost endpoint = mapEntry.getKey();
            final RoutePool routePool = mapEntry.getValue();
            routePool.lock.lock();
            try {
                final Iterator<ConnectionEntry> it = routePool.entries.iterator();
                while (it.hasNext()) {
                    final ConnectionEntry entry = it.next();
                    if (entry.reserved.get() <= 0 && entry.lastUsed <= deadline) {
                        if (entry.support.getActiveLocalStreams() == 0) {
                            it.remove();
                            entry.support.setObserver(null);
                            releaseGlobalSlot();
                            freedCapacity = true;
                            closeSession(entry.session, CloseMode.GRACEFUL);
                        }
                    }
                }
                pruneRoutePoolIfUnused(endpoint, routePool);
            } finally {
                routePool.lock.unlock();
            }
        }

        if (freedCapacity) {
            processGlobalPending();
        }
    }

    void closeExpired() {
        boolean freedCapacity = false;

        for (final Map.Entry<HttpHost, RoutePool> mapEntry : routePools.entrySet()) {
            final HttpHost endpoint = mapEntry.getKey();
            final RoutePool routePool = mapEntry.getValue();
            routePool.lock.lock();
            try {
                final Iterator<ConnectionEntry> it = routePool.entries.iterator();
                while (it.hasNext()) {
                    final ConnectionEntry entry = it.next();
                    if (entry.reserved.get() <= 0 && (entry.draining || !entry.session.isOpen())) {
                        if (entry.support.getActiveLocalStreams() == 0) {
                            it.remove();
                            entry.support.setObserver(null);
                            releaseGlobalSlot();
                            freedCapacity = true;
                            closeSession(entry.session, CloseMode.GRACEFUL);
                        }
                    }
                }
                pruneRoutePoolIfUnused(endpoint, routePool);
            } finally {
                routePool.lock.unlock();
            }
        }

        if (freedCapacity) {
            processGlobalPending();
        }
    }

    @Override
    public void close(final CloseMode closeMode) {
        final ReentrantReadWriteLock.WriteLock writeLock = lifecycleLock.writeLock();
        writeLock.lock();
        try {
            if (closed.compareAndSet(false, true)) {
                final List<PendingLease> allCancelled = new ArrayList<>();
                for (final Map.Entry<HttpHost, RoutePool> mapEntry : routePools.entrySet()) {
                    final RoutePool routePool = mapEntry.getValue();
                    routePool.lock.lock();
                    try {
                        for (final ConnectionEntry entry : routePool.entries) {
                            entry.support.setObserver(null);
                            closeSession(entry.session, closeMode);
                        }
                        routePool.entries.clear();

                        PendingLease pending;
                        while ((pending = routePool.pendingRequests.poll()) != null) {
                            allCancelled.add(pending);
                        }
                    } finally {
                        routePool.lock.unlock();
                    }
                }
                for (final PendingLease pending : allCancelled) {
                    pending.future.cancel();
                }
                globallyBlockedRoutes.clear();
                routePools.clear();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("H2MultiplexingConnPool [routes: ");
        buf.append(routePools.size());
        buf.append(", total connections: ");
        buf.append(totalConnectionCount.get());
        buf.append(", globally blocked routes: ");
        buf.append(globallyBlockedRoutes.size());
        buf.append("]");
        return buf.toString();
    }

    private static void fireCompletions(final List<Runnable> completions) {
        if (completions == null || completions.isEmpty()) {
            return;
        }
        for (final Runnable runnable : completions) {
            runnable.run();
        }
    }

    static final class PendingLease {

        final BasicFuture<H2StreamLease> future;
        final Timeout connectTimeout;

        PendingLease(
                final BasicFuture<H2StreamLease> future,
                final Timeout connectTimeout) {
            this.future = future;
            this.connectTimeout = connectTimeout;
        }
    }

    static final class QueuedRoute {

        final HttpHost endpoint;
        final RoutePool routePool;

        QueuedRoute(
                final HttpHost endpoint,
                final RoutePool routePool) {
            this.endpoint = endpoint;
            this.routePool = routePool;
        }
    }

    static class ConnectionEntry {

        final IOSession session;
        final H2PoolSessionSupport support;
        final AtomicInteger reserved;
        final AtomicInteger lastSeenActive;
        final AtomicBoolean observerRegistered;

        volatile long lastUsed;
        volatile boolean draining;

        ConnectionEntry(final IOSession session, final H2PoolSessionSupport support) {
            this.session = session;
            this.support = support;
            this.reserved = new AtomicInteger(0);
            this.lastSeenActive = new AtomicInteger(support.getActiveLocalStreams());
            this.observerRegistered = new AtomicBoolean(false);
            this.lastUsed = System.currentTimeMillis();
            this.draining = false;
        }

        int availableCapacity() {
            if (draining || !session.isOpen()) {
                return 0;
            }
            if (support.isGoAwayReceived() || support.isShutdown()) {
                return 0;
            }
            final int peerMax = support.getPeerMaxConcurrentStreams();
            final int active = support.getActiveLocalStreams();
            final int res = reserved.get();
            return Math.max(0, peerMax - active - res);
        }

        /**
         * Whether this entry still occupies a per-route slot that must block
         * the pool from opening a replacement connection. A draining, closed,
         * {@code GOAWAY}-received or shut-down entry can serve no new leases
         * and therefore must not count.
         */
        boolean countsTowardRouteLimit() {
            if (draining || !session.isOpen()) {
                return false;
            }
            if (support.isGoAwayReceived() || support.isShutdown()) {
                return false;
            }
            return true;
        }
    }

    static class RoutePool {

        final List<ConnectionEntry> entries;
        final Deque<PendingLease> pendingRequests;
        final ReentrantLock lock;

        boolean connectInFlight;
        boolean globallyQueued;

        RoutePool() {
            this.entries = new ArrayList<>();
            this.pendingRequests = new ArrayDeque<>();
            this.lock = new ReentrantLock();
            this.connectInFlight = false;
            this.globallyQueued = false;
        }

        ConnectionEntry reserveAvailable() {
            ConnectionEntry best = null;
            int bestCapacity = 0;
            for (final ConnectionEntry entry : entries) {
                final int capacity = entry.availableCapacity();
                if (capacity > bestCapacity) {
                    best = entry;
                    bestCapacity = capacity;
                }
            }
            if (best != null) {
                best.reserved.incrementAndGet();
                best.lastUsed = System.currentTimeMillis();
                return best;
            }
            return null;
        }
    }

    @Override
    public Future<H2StreamLease> leaseSession(
            final HttpHost endpoint,
            final Timeout connectTimeout,
            final FutureCallback<H2StreamLease> callback) {
        return lease(endpoint, connectTimeout, callback);
    }

}
