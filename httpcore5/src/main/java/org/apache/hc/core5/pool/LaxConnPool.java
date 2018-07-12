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
package org.apache.hc.core5.pool;

import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Connection pool with higher concurrency but with lax connection limit guarantees.
 *
 * @param <T> route
 * @param <C> connection object
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
@Experimental
public class LaxConnPool<T, C extends ModalCloseable> implements ManagedConnPool<T, C> {

    private final TimeValue timeToLive;
    private final ConnPoolListener<T> connPoolListener;
    private final PoolReusePolicy policy;
    private final ConcurrentMap<T, PerRoutePool<T, C>> routeToPool;
    private final AtomicBoolean isShutDown;

    private volatile int defaultMaxPerRoute;

    /**
     * @since 5.0
     */
    public LaxConnPool(
            final int defaultMaxPerRoute,
            final TimeValue timeToLive,
            final PoolReusePolicy policy,
            final ConnPoolListener<T> connPoolListener) {
        super();
        Args.positive(defaultMaxPerRoute, "Max per route value");
        this.timeToLive = TimeValue.defaultsToNegativeOneMillisecond(timeToLive);
        this.connPoolListener = connPoolListener;
        this.policy = policy != null ? policy : PoolReusePolicy.LIFO;
        this.routeToPool = new ConcurrentHashMap<>();
        this.isShutDown = new AtomicBoolean(false);
        this.defaultMaxPerRoute = defaultMaxPerRoute;
    }

    public LaxConnPool(final int defaultMaxPerRoute) {
        this(defaultMaxPerRoute, TimeValue.NEG_ONE_MILLISECONDS, PoolReusePolicy.LIFO, null);
    }

    public boolean isShutdown() {
        return isShutDown.get();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (isShutDown.compareAndSet(false, true)) {
            for (final Iterator<PerRoutePool<T, C>> it = routeToPool.values().iterator(); it.hasNext(); ) {
                final PerRoutePool<T, C> routePool = it.next();
                routePool.shutdown(closeMode);
            }
            routeToPool.clear();
        }
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    private PerRoutePool<T, C> getPool(final T route) {
        PerRoutePool<T, C> routePool = routeToPool.get(route);
        if (routePool == null) {
            final PerRoutePool<T, C> newRoutePool = new PerRoutePool<>(
                    route,
                    defaultMaxPerRoute,
                    timeToLive,
                    policy,
                    this,
                    connPoolListener);
            routePool = routeToPool.putIfAbsent(route, newRoutePool);
            if (routePool == null) {
                routePool = newRoutePool;
            }
        }
        return routePool;
    }

    @Override
    public Future<PoolEntry<T, C>> lease(
            final T route, final Object state,
            final Timeout requestTimeout,
            final FutureCallback<PoolEntry<T, C>> callback) {
        Args.notNull(route, "Route");
        Asserts.check(!isShutDown.get(), "Connection pool shut down");
        final PerRoutePool<T, C> routePool = getPool(route);
        return routePool.lease(state, requestTimeout, callback);
    }

    public Future<PoolEntry<T, C>> lease(final T route, final Object state) {
        return lease(route, state, Timeout.DISABLED, null);
    }

    @Override
    public void release(final PoolEntry<T, C> entry, final boolean reusable) {
        if (entry == null) {
            return;
        }
        if (isShutDown.get()) {
            return;
        }
        final PerRoutePool<T, C> routePool = getPool(entry.getRoute());
        if (connPoolListener != null) {
            connPoolListener.onLease(entry.getRoute(), this);
        }
        routePool.release(entry, reusable);
    }

    public void validatePendingRequests() {
        for (final PerRoutePool<T, C> routePool : routeToPool.values()) {
            routePool.validatePendingRequests();
        }
    }

    @Override
    public void setMaxTotal(final int max) {
    }

    @Override
    public int getMaxTotal() {
        return 0;
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        Args.positive(max, "Max value");
        defaultMaxPerRoute = max;
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return defaultMaxPerRoute;
    }

    @Override
    public void setMaxPerRoute(final T route, final int max) {
        Args.notNull(route, "Route");
        final PerRoutePool<T, C> routePool = getPool(route);
        routePool.setMax(max > -1 ? max : defaultMaxPerRoute);
    }

    @Override
    public int getMaxPerRoute(final T route) {
        Args.notNull(route, "Route");
        final PerRoutePool<T, C> routePool = getPool(route);
        return routePool.getMax();
    }

    @Override
    public PoolStats getTotalStats() {
        int leasedTotal = 0;
        int pendingTotal = 0;
        int availableTotal = 0;
        int maxTotal = 0;
        for (final PerRoutePool<T, C> routePool : routeToPool.values()) {
            leasedTotal += routePool.getLeasedCount();
            pendingTotal += routePool.getPendingCount();
            availableTotal += routePool.getAvailableCount();
            maxTotal += routePool.getMax();
        }
        return new PoolStats(leasedTotal, pendingTotal, availableTotal, maxTotal);
    }

    @Override
    public PoolStats getStats(final T route) {
        Args.notNull(route, "Route");
        final PerRoutePool<T, C> routePool = getPool(route);
        return new PoolStats(
                routePool.getLeasedCount(),
                routePool.getPendingCount(),
                routePool.getAvailableCount(),
                routePool.getMax());
    }

    @Override
    public Set<T> getRoutes() {
        return new HashSet<>(routeToPool.keySet());
    }

    public void enumAvailable(final Callback<PoolEntry<T, C>> callback) {
        for (final PerRoutePool<T, C> routePool : routeToPool.values()) {
            routePool.enumAvailable(callback);
        }
    }

    public void enumLeased(final Callback<PoolEntry<T, C>> callback) {
        for (final PerRoutePool<T, C> routePool : routeToPool.values()) {
            routePool.enumLeased(callback);
        }
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        final long deadline = System.currentTimeMillis() - (TimeValue.isPositive(idleTime) ? idleTime.toMillis() : 0);
        enumAvailable(new Callback<PoolEntry<T, C>>() {

            @Override
            public void execute(final PoolEntry<T, C> entry) {
                if (entry.getUpdated() <= deadline) {
                    entry.discardConnection(CloseMode.GRACEFUL);
                }
            }

        });
    }

    @Override
    public void closeExpired() {
        final long now = System.currentTimeMillis();
        enumAvailable(new Callback<PoolEntry<T, C>>() {

            @Override
            public void execute(final PoolEntry<T, C> entry) {
                if (entry.getExpiry() < now) {
                    entry.discardConnection(CloseMode.GRACEFUL);
                }
            }

        });
    }

    @Override
    public String toString() {
        final PoolStats totalStats = getTotalStats();
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[leased: ");
        buffer.append(totalStats.getLeased());
        buffer.append("][available: ");
        buffer.append(totalStats.getAvailable());
        buffer.append("][pending: ");
        buffer.append(totalStats.getPending());
        buffer.append("]");
        return buffer.toString();
    }

    static class LeaseRequest<T, C extends ModalCloseable> implements Cancellable {

        private final Object state;
        private final long deadline;
        private final BasicFuture<PoolEntry<T, C>> future;

        LeaseRequest(
                final Object state,
                final TimeValue requestTimeout,
                final BasicFuture<PoolEntry<T, C>> future) {
            super();
            this.state = state;
            this.deadline = TimeValue.calculateDeadline(System.currentTimeMillis(), requestTimeout);
            this.future = future;
        }

        BasicFuture<PoolEntry<T, C>> getFuture() {
            return this.future;
        }

        public Object getState() {
            return this.state;
        }

        public long getDeadline() {
            return this.deadline;
        }

        public boolean isDone() {
            return this.future.isDone();
        }

        public void completed(final PoolEntry<T, C> result) {
            future.completed(result);
        }

        public void failed(final Exception ex) {
            future.failed(ex);
        }

        @Override
        public boolean cancel() {
            return future.cancel();
        }

    }

    static class PerRoutePool<T, C extends ModalCloseable> {

        private final T route;
        private final TimeValue timeToLive;
        private final PoolReusePolicy policy;
        private final ConnPoolStats<T> connPoolStats;
        private final ConnPoolListener<T> connPoolListener;
        private final ConcurrentMap<PoolEntry<T, C>, Boolean> leased;
        private final Deque<PoolEntry<T, C>> available;
        private final Deque<LeaseRequest<T, C>> pending;
        private final AtomicBoolean terminated;

        private volatile int max;

        PerRoutePool(
                final T route,
                final int max,
                final TimeValue timeToLive,
                final PoolReusePolicy policy,
                final ConnPoolStats<T> connPoolStats,
                final ConnPoolListener<T> connPoolListener) {
            super();
            this.route = route;
            this.timeToLive = timeToLive;
            this.policy = policy;
            this.connPoolStats = connPoolStats;
            this.connPoolListener = connPoolListener;
            this.leased = new ConcurrentHashMap<>();
            this.available = new ConcurrentLinkedDeque<>();
            this.pending = new ConcurrentLinkedDeque<>();
            this.terminated = new AtomicBoolean(false);
            this.max = max;
        }

        public void shutdown(final CloseMode closeMode) {
            if (terminated.compareAndSet(false, true)) {
                PoolEntry<T, C> availableEntry;
                while ((availableEntry = available.poll()) != null) {
                    availableEntry.discardConnection(closeMode);
                }
                for (final PoolEntry<T, C> entry : leased.keySet()) {
                    entry.discardConnection(closeMode);
                }
                leased.clear();
                LeaseRequest<T, C> leaseRequest;
                while ((leaseRequest = pending.poll()) != null) {
                    leaseRequest.cancel();
                }
            }
        }

        private void addLeased(final PoolEntry<T, C> entry) {
            if (leased.putIfAbsent(entry, Boolean.TRUE) != null) {
                throw new IllegalStateException("Pool entry already present in the set of leased entries");
            } else if (connPoolListener != null) {
                connPoolListener.onLease(route, connPoolStats);
            }
        }

        private void removeLeased(final PoolEntry<T, C> entry) {
            if (connPoolListener != null) {
                connPoolListener.onRelease(route, connPoolStats);
            }
            if (!leased.remove(entry, Boolean.TRUE)) {
                throw new IllegalStateException("Pool entry is not present in the set of leased entries");
            }
        }

        private PoolEntry<T, C> getAvailableEntry(final Object state) {
            final PoolEntry<T, C> entry = available.poll();
            if (entry != null) {
                if (entry.getExpiry() < System.currentTimeMillis()) {
                    entry.discardConnection(CloseMode.GRACEFUL);
                }
                if (!LangUtils.equals(entry.getState(), state)) {
                    entry.discardConnection(CloseMode.GRACEFUL);
                }
            }
            return entry;
        }

        public Future<PoolEntry<T, C>> lease(
                final Object state,
                final TimeValue requestTimeout,
                final FutureCallback<PoolEntry<T, C>> callback) {
            Asserts.check(!terminated.get(), "Connection pool shut down");
            final BasicFuture<PoolEntry<T, C>> future = new BasicFuture<>(callback);
            final PoolEntry<T, C> availableEntry = getAvailableEntry(state);
            if (availableEntry != null) {
                addLeased(availableEntry);
                future.completed(availableEntry);
            } else {
                if (pending.isEmpty() && leased.size() < max) {
                    final PoolEntry<T, C> entry = new PoolEntry<>(route, timeToLive);
                    addLeased(entry);
                    future.completed(entry);
                } else {
                    pending.add(new LeaseRequest<>(state, requestTimeout, future));
                }
            }
            return future;
        }

        public void release(final PoolEntry<T, C> releasedEntry, final boolean reusable) {
            removeLeased(releasedEntry);
            if (!reusable || releasedEntry.getExpiry() < System.currentTimeMillis()) {
                releasedEntry.discardConnection(CloseMode.GRACEFUL);
            }
            if (releasedEntry.hasConnection()) {
                switch (policy) {
                    case LIFO:
                        available.addFirst(releasedEntry);
                        break;
                    case FIFO:
                        available.addLast(releasedEntry);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected ConnPoolPolicy value: " + policy);
                }
            }
            LeaseRequest<T, C> leaseRequest;
            while ((leaseRequest = pending.poll()) != null) {
                if (leaseRequest.isDone()) {
                    continue;
                }
                final Object state = leaseRequest.getState();
                final long deadline = leaseRequest.getDeadline();

                final long now = System.currentTimeMillis();
                if (now > deadline) {
                    leaseRequest.failed(new TimeoutException());
                } else {
                    final PoolEntry<T, C> availableEntry = getAvailableEntry(state);
                    if (availableEntry != null) {
                        addLeased(releasedEntry);
                        leaseRequest.completed(availableEntry);
                    } else if (leased.size() < max) {
                        final PoolEntry<T, C> newEntry = new PoolEntry<>(route, timeToLive);
                        addLeased(newEntry);
                        leaseRequest.completed(newEntry);
                    }
                    break;
                }
            }
        }

        public void validatePendingRequests() {
            final long now = System.currentTimeMillis();
            final Iterator<LeaseRequest<T, C>> it = pending.iterator();
            while (it.hasNext()) {
                final LeaseRequest<T, C> request = it.next();
                final BasicFuture<PoolEntry<T, C>> future = request.getFuture();
                if (future.isCancelled() && !request.isDone()) {
                    it.remove();
                } else {
                    final long deadline = request.getDeadline();
                    if (now > deadline) {
                        request.failed(new TimeoutException());
                    }
                    if (request.isDone()) {
                        it.remove();
                    }
                }
            }
        }

        public final T getRoute() {
            return route;
        }

        public int getMax() {
            return max;
        }

        public void setMax(final int max) {
            this.max = max;
        }

        public int getPendingCount() {
            return pending.size();
        }

        public int getLeasedCount() {
            return leased.size();
        }

        public int getAvailableCount() {
            return available.size();
        }

        public void enumAvailable(final Callback<PoolEntry<T, C>> callback) {
            for (final Iterator<PoolEntry<T, C>> it = available.iterator(); it.hasNext(); ) {
                final PoolEntry<T, C> entry = it.next();
                callback.execute(entry);
                if (!entry.hasConnection()) {
                    it.remove();
                }
            }
        }

        public void enumLeased(final Callback<PoolEntry<T, C>> callback) {
            for (final Iterator<PoolEntry<T, C>> it = leased.keySet().iterator(); it.hasNext(); ) {
                final PoolEntry<T, C> entry = it.next();
                callback.execute(entry);
                if (!entry.hasConnection()) {
                    it.remove();
                }
            }
        }

        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            buffer.append("[route: ");
            buffer.append(route);
            buffer.append("][leased: ");
            buffer.append(leased.size());
            buffer.append("][available: ");
            buffer.append(available.size());
            buffer.append("][pending: ");
            buffer.append(pending.size());
            buffer.append("]");
            return buffer.toString();
        }

    }

}