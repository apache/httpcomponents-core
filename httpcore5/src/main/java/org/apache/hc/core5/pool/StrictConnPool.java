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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Deadline;
import org.apache.hc.core5.util.DeadlineTimeoutException;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Connection pool with strict connection limit guarantees.
 *
 * @param <T> route
 * @param <C> connection object
 *
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class StrictConnPool<T, C extends ModalCloseable> implements ManagedConnPool<T, C> {

    private final TimeValue timeToLive;
    private final PoolReusePolicy policy;
    private final DisposalCallback<C> disposalCallback;
    private final ConnPoolListener<T> connPoolListener;
    private final Map<T, PerRoutePool<T, C>> routeToPool;
    private final LinkedList<LeaseRequest<T, C>> pendingRequests;
    private final Set<PoolEntry<T, C>> leased;
    private final LinkedList<PoolEntry<T, C>> available;
    private final ConcurrentLinkedQueue<LeaseRequest<T, C>> completedRequests;
    private final Map<T, Integer> maxPerRoute;
    private final Lock lock;
    private final AtomicBoolean isShutDown;

    private volatile int defaultMaxPerRoute;
    private volatile int maxTotal;

    /**
     * @since 5.0
     */
    public StrictConnPool(
            final int defaultMaxPerRoute,
            final int maxTotal,
            final TimeValue timeToLive,
            final PoolReusePolicy policy,
            final DisposalCallback<C> disposalCallback,
            final ConnPoolListener<T> connPoolListener) {
        super();
        Args.positive(defaultMaxPerRoute, "Max per route value");
        Args.positive(maxTotal, "Max total value");
        this.timeToLive = TimeValue.defaultsToNegativeOneMillisecond(timeToLive);
        this.policy = policy != null ? policy : PoolReusePolicy.LIFO;
        this.disposalCallback = disposalCallback;
        this.connPoolListener = connPoolListener;
        this.routeToPool = new HashMap<>();
        this.pendingRequests = new LinkedList<>();
        this.leased = new HashSet<>();
        this.available = new LinkedList<>();
        this.completedRequests = new ConcurrentLinkedQueue<>();
        this.maxPerRoute = new HashMap<>();
        this.lock = new ReentrantLock();
        this.isShutDown = new AtomicBoolean(false);
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        this.maxTotal = maxTotal;
    }

    /**
     * @since 5.0
     */
    public StrictConnPool(
            final int defaultMaxPerRoute,
            final int maxTotal,
            final TimeValue timeToLive,
            final PoolReusePolicy policy,
            final ConnPoolListener<T> connPoolListener) {
        this(defaultMaxPerRoute, maxTotal, timeToLive, policy, null, connPoolListener);
    }

    public StrictConnPool(final int defaultMaxPerRoute, final int maxTotal) {
        this(defaultMaxPerRoute, maxTotal, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, null);
    }

    public boolean isShutdown() {
        return this.isShutDown.get();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.isShutDown.compareAndSet(false, true)) {
            fireCallbacks();
            this.lock.lock();
            try {
                for (final PerRoutePool<T, C> pool: this.routeToPool.values()) {
                    pool.shutdown(closeMode);
                }
                this.routeToPool.clear();
                this.leased.clear();
                this.available.clear();
                this.pendingRequests.clear();
            } finally {
                this.lock.unlock();
            }
        }
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    private PerRoutePool<T, C> getPool(final T route) {
        PerRoutePool<T, C> pool = this.routeToPool.get(route);
        if (pool == null) {
            pool = new PerRoutePool<>(route, this.disposalCallback);
            this.routeToPool.put(route, pool);
        }
        return pool;
    }

    @Override
    public Future<PoolEntry<T, C>> lease(
            final T route, final Object state,
            final Timeout requestTimeout,
            final FutureCallback<PoolEntry<T, C>> callback) {
        Args.notNull(route, "Route");
        Args.notNull(requestTimeout, "Request timeout");
        Asserts.check(!this.isShutDown.get(), "Connection pool shut down");
        final Deadline deadline = Deadline.calculate(requestTimeout);
        final BasicFuture<PoolEntry<T, C>> future = new BasicFuture<PoolEntry<T, C>>(callback) {

            @Override
            public synchronized PoolEntry<T, C> get(
                    final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                try {
                    return super.get(timeout, unit);
                } catch (final TimeoutException ex) {
                    cancel();
                    throw ex;
                }
            }

        };
        final boolean acquiredLock;

        try {
            acquiredLock = this.lock.tryLock(requestTimeout.getDuration(), requestTimeout.getTimeUnit());
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            future.cancel();
            return future;
        }

        if (acquiredLock) {
            try {
                final LeaseRequest<T, C> request = new LeaseRequest<>(route, state, requestTimeout, future);
                final boolean completed = processPendingRequest(request);
                if (!request.isDone() && !completed) {
                    this.pendingRequests.add(request);
                }
                if (request.isDone()) {
                    this.completedRequests.add(request);
                }
            } finally {
                this.lock.unlock();
            }
            fireCallbacks();
        } else {
            future.failed(DeadlineTimeoutException.from(deadline));
        }

        return future;
    }

    public Future<PoolEntry<T, C>> lease(final T route, final Object state) {
        return lease(route, state, Timeout.DISABLED, null);
    }

    @Override
    public void release(final PoolEntry<T, C> entry, final boolean reusable) {
        if (entry == null) {
            return;
        }
        if (this.isShutDown.get()) {
            return;
        }
        if (!reusable) {
            entry.discardConnection(CloseMode.GRACEFUL);
        }
        this.lock.lock();
        try {
            if (this.leased.remove(entry)) {
                if (this.connPoolListener != null) {
                    this.connPoolListener.onRelease(entry.getRoute(), this);
                }
                final PerRoutePool<T, C> pool = getPool(entry.getRoute());
                final boolean keepAlive = entry.hasConnection() && reusable;
                pool.free(entry, keepAlive);
                if (keepAlive) {
                    switch (policy) {
                        case LIFO:
                            this.available.addFirst(entry);
                            break;
                        case FIFO:
                            this.available.addLast(entry);
                            break;
                        default:
                            throw new IllegalStateException("Unexpected ConnPoolPolicy value: " + policy);
                    }
                } else {
                    entry.discardConnection(CloseMode.GRACEFUL);
                }
                processNextPendingRequest();
            } else {
                throw new IllegalStateException("Pool entry is not present in the set of leased entries");
            }
        } finally {
            this.lock.unlock();
        }
        fireCallbacks();
    }

    private void processPendingRequests() {
        final ListIterator<LeaseRequest<T, C>> it = this.pendingRequests.listIterator();
        while (it.hasNext()) {
            final LeaseRequest<T, C> request = it.next();
            final BasicFuture<PoolEntry<T, C>> future = request.getFuture();
            if (future.isCancelled()) {
                it.remove();
                continue;
            }
            final boolean completed = processPendingRequest(request);
            if (request.isDone() || completed) {
                it.remove();
            }
            if (request.isDone()) {
                this.completedRequests.add(request);
            }
        }
    }

    private void processNextPendingRequest() {
        final ListIterator<LeaseRequest<T, C>> it = this.pendingRequests.listIterator();
        while (it.hasNext()) {
            final LeaseRequest<T, C> request = it.next();
            final BasicFuture<PoolEntry<T, C>> future = request.getFuture();
            if (future.isCancelled()) {
                it.remove();
                continue;
            }
            final boolean completed = processPendingRequest(request);
            if (request.isDone() || completed) {
                it.remove();
            }
            if (request.isDone()) {
                this.completedRequests.add(request);
            }
            if (completed) {
                return;
            }
        }
    }

    private boolean processPendingRequest(final LeaseRequest<T, C> request) {
        final T route = request.getRoute();
        final Object state = request.getState();
        final Deadline deadline = request.getDeadline();

        if (deadline.isExpired()) {
            request.failed(DeadlineTimeoutException.from(deadline));
            return false;
        }

        final PerRoutePool<T, C> pool = getPool(route);
        PoolEntry<T, C> entry;
        for (;;) {
            entry = pool.getFree(state);
            if (entry == null) {
                break;
            }
            if (entry.getExpiryDeadline().isExpired()) {
                entry.discardConnection(CloseMode.GRACEFUL);
                this.available.remove(entry);
                pool.free(entry, false);
            } else {
                break;
            }
        }
        if (entry != null) {
            this.available.remove(entry);
            this.leased.add(entry);
            request.completed(entry);
            if (this.connPoolListener != null) {
                this.connPoolListener.onLease(entry.getRoute(), this);
            }
            return true;
        }

        // New connection is needed
        final int maxPerRoute = getMax(route);
        // Shrink the pool prior to allocating a new connection
        final int excess = Math.max(0, pool.getAllocatedCount() + 1 - maxPerRoute);
        if (excess > 0) {
            for (int i = 0; i < excess; i++) {
                final PoolEntry<T, C> lastUsed = pool.getLastUsed();
                if (lastUsed == null) {
                    break;
                }
                lastUsed.discardConnection(CloseMode.GRACEFUL);
                this.available.remove(lastUsed);
                pool.remove(lastUsed);
            }
        }

        if (pool.getAllocatedCount() < maxPerRoute) {
            final int freeCapacity = Math.max(this.maxTotal - this.leased.size(), 0);
            if (freeCapacity == 0) {
                return false;
            }
            final int totalAvailable = this.available.size();
            if (totalAvailable > freeCapacity - 1) {
                if (!this.available.isEmpty()) {
                    final PoolEntry<T, C> lastUsed = this.available.removeLast();
                    lastUsed.discardConnection(CloseMode.GRACEFUL);
                    final PerRoutePool<T, C> otherpool = getPool(lastUsed.getRoute());
                    otherpool.remove(lastUsed);
                }
            }

            entry = pool.createEntry(this.timeToLive);
            this.leased.add(entry);
            request.completed(entry);
            if (this.connPoolListener != null) {
                this.connPoolListener.onLease(entry.getRoute(), this);
            }
            return true;
        }
        return false;
    }

    private void fireCallbacks() {
        LeaseRequest<T, C> request;
        while ((request = this.completedRequests.poll()) != null) {
            final BasicFuture<PoolEntry<T, C>> future = request.getFuture();
            final Exception ex = request.getException();
            final PoolEntry<T, C> result = request.getResult();
            boolean successfullyCompleted = false;
            if (ex != null) {
                future.failed(ex);
            } else if (result != null) {
                if (future.completed(result)) {
                    successfullyCompleted = true;
                }
            } else {
                future.cancel();
            }
            if (!successfullyCompleted) {
                release(result, true);
            }
        }
    }

    public void validatePendingRequests() {
        this.lock.lock();
        try {
            final long now = System.currentTimeMillis();
            final ListIterator<LeaseRequest<T, C>> it = this.pendingRequests.listIterator();
            while (it.hasNext()) {
                final LeaseRequest<T, C> request = it.next();
                final BasicFuture<PoolEntry<T, C>> future = request.getFuture();
                if (future.isCancelled() && !request.isDone()) {
                    it.remove();
                } else {
                    final Deadline deadline = request.getDeadline();
                    if (deadline.isBefore(now)) {
                        request.failed(DeadlineTimeoutException.from(deadline));
                    }
                    if (request.isDone()) {
                        it.remove();
                        this.completedRequests.add(request);
                    }
                }
            }
        } finally {
            this.lock.unlock();
        }
        fireCallbacks();
    }

    private int getMax(final T route) {
        final Integer v = this.maxPerRoute.get(route);
        if (v != null) {
            return v;
        }
        return this.defaultMaxPerRoute;
    }

    @Override
    public void setMaxTotal(final int max) {
        Args.positive(max, "Max value");
        this.lock.lock();
        try {
            this.maxTotal = max;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int getMaxTotal() {
        this.lock.lock();
        try {
            return this.maxTotal;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        Args.positive(max, "Max value");
        this.lock.lock();
        try {
            this.defaultMaxPerRoute = max;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int getDefaultMaxPerRoute() {
        this.lock.lock();
        try {
            return this.defaultMaxPerRoute;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void setMaxPerRoute(final T route, final int max) {
        Args.notNull(route, "Route");
        this.lock.lock();
        try {
            if (max > -1) {
                this.maxPerRoute.put(route, max);
            } else {
                this.maxPerRoute.remove(route);
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int getMaxPerRoute(final T route) {
        Args.notNull(route, "Route");
        this.lock.lock();
        try {
            return getMax(route);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public PoolStats getTotalStats() {
        this.lock.lock();
        try {
            return new PoolStats(
                    this.leased.size(),
                    this.pendingRequests.size(),
                    this.available.size(),
                    this.maxTotal);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public PoolStats getStats(final T route) {
        Args.notNull(route, "Route");
        this.lock.lock();
        try {
            final PerRoutePool<T, C> pool = getPool(route);
            int pendingCount = 0;
            for (final LeaseRequest<T, C> request: pendingRequests) {
                if (LangUtils.equals(route, request.getRoute())) {
                    pendingCount++;
                }
            }
            return new PoolStats(
                    pool.getLeasedCount(),
                    pendingCount,
                    pool.getAvailableCount(),
                    getMax(route));
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Returns snapshot of all knows routes
     *
     * @since 4.4
     */
    @Override
    public Set<T> getRoutes() {
        this.lock.lock();
        try {
            return new HashSet<>(routeToPool.keySet());
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Enumerates all available connections.
     *
     * @since 4.3
     */
    public void enumAvailable(final Callback<PoolEntry<T, C>> callback) {
        this.lock.lock();
        try {
            final Iterator<PoolEntry<T, C>> it = this.available.iterator();
            while (it.hasNext()) {
                final PoolEntry<T, C> entry = it.next();
                callback.execute(entry);
                if (!entry.hasConnection()) {
                    final PerRoutePool<T, C> pool = getPool(entry.getRoute());
                    pool.remove(entry);
                    it.remove();
                }
            }
            processPendingRequests();
            purgePoolMap();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Enumerates all leased connections.
     *
     * @since 4.3
     */
    public void enumLeased(final Callback<PoolEntry<T, C>> callback) {
        this.lock.lock();
        try {
            final Iterator<PoolEntry<T, C>> it = this.leased.iterator();
            while (it.hasNext()) {
                final PoolEntry<T, C> entry = it.next();
                callback.execute(entry);
            }
            processPendingRequests();
        } finally {
            this.lock.unlock();
        }
    }

    private void purgePoolMap() {
        final Iterator<Map.Entry<T, PerRoutePool<T, C>>> it = this.routeToPool.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<T, PerRoutePool<T, C>> entry = it.next();
            final PerRoutePool<T, C> pool = entry.getValue();
            if (pool.getAllocatedCount() == 0) {
                it.remove();
            }
        }
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        final long deadline = System.currentTimeMillis() - (TimeValue.isPositive(idleTime) ? idleTime.toMilliseconds() : 0);
        enumAvailable(entry -> {
            if (entry.getUpdated() <= deadline) {
                entry.discardConnection(CloseMode.GRACEFUL);
            }
        });
    }

    @Override
    public void closeExpired() {
        final long now = System.currentTimeMillis();
        enumAvailable(entry -> {
            if (entry.getExpiryDeadline().isBefore(now)) {
                entry.discardConnection(CloseMode.GRACEFUL);
            }
        });
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[leased: ");
        buffer.append(this.leased.size());
        buffer.append("][available: ");
        buffer.append(this.available.size());
        buffer.append("][pending: ");
        buffer.append(this.pendingRequests.size());
        buffer.append("]");
        return buffer.toString();
    }


    static class LeaseRequest<T, C extends ModalCloseable> {

        private final T route;
        private final Object state;
        private final Deadline deadline;
        private final BasicFuture<PoolEntry<T, C>> future;
        // 'completed' is used internally to guard setting
        // 'result' and 'ex', but mustn't be used by 'isDone()'.
        private final AtomicBoolean completed;
        private volatile PoolEntry<T, C> result;
        private volatile Exception ex;

        /**
         * Constructor
         *
         * @param route route
         * @param state state
         * @param requestTimeout timeout to wait in a request queue until kicked off
         * @param future future callback
         */
        public LeaseRequest(
                final T route,
                final Object state,
                final Timeout requestTimeout,
                final BasicFuture<PoolEntry<T, C>> future) {
            super();
            this.route = route;
            this.state = state;
            this.deadline = Deadline.calculate(requestTimeout);
            this.future = future;
            this.completed = new AtomicBoolean(false);
        }

        public T getRoute() {
            return this.route;
        }

        public Object getState() {
            return this.state;
        }

        public Deadline getDeadline() {
            return this.deadline;
        }

        public boolean isDone() {
            // This method must not use 'completed.get()' which would result in a race
            // where a caller may observe completed=true while neither result nor ex
            // have been set yet.
            return ex != null || result != null;
        }

        public void failed(final Exception ex) {
            if (this.completed.compareAndSet(false, true)) {
                this.ex = ex;
            }
        }

        public void completed(final PoolEntry<T, C> result) {
            if (this.completed.compareAndSet(false, true)) {
                this.result = result;
            }
        }

        public BasicFuture<PoolEntry<T, C>> getFuture() {
            return this.future;
        }

        public PoolEntry<T, C> getResult() {
            return this.result;
        }

        public Exception getException() {
            return this.ex;
        }

        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            buffer.append("[");
            buffer.append(this.route);
            buffer.append("][");
            buffer.append(this.state);
            buffer.append("]");
            return buffer.toString();
        }

    }

    static class PerRoutePool<T, C extends ModalCloseable> {

        private final T route;
        private final Set<PoolEntry<T, C>> leased;
        private final LinkedList<PoolEntry<T, C>> available;
        private final DisposalCallback<C> disposalCallback;

        PerRoutePool(final T route, final DisposalCallback<C> disposalCallback) {
            super();
            this.route = route;
            this.disposalCallback = disposalCallback;
            this.leased = new HashSet<>();
            this.available = new LinkedList<>();
        }

        public final T getRoute() {
            return route;
        }

        public int getLeasedCount() {
            return this.leased.size();
        }

        public int getAvailableCount() {
            return this.available.size();
        }

        public int getAllocatedCount() {
            return this.available.size() + this.leased.size();
        }

        public PoolEntry<T, C> getFree(final Object state) {
            if (!this.available.isEmpty()) {
                if (state != null) {
                    final Iterator<PoolEntry<T, C>> it = this.available.iterator();
                    while (it.hasNext()) {
                        final PoolEntry<T, C> entry = it.next();
                        if (state.equals(entry.getState())) {
                            it.remove();
                            this.leased.add(entry);
                            return entry;
                        }
                    }
                }
                final Iterator<PoolEntry<T, C>> it = this.available.iterator();
                while (it.hasNext()) {
                    final PoolEntry<T, C> entry = it.next();
                    if (entry.getState() == null) {
                        it.remove();
                        this.leased.add(entry);
                        return entry;
                    }
                }
            }
            return null;
        }

        public PoolEntry<T, C> getLastUsed() {
            return this.available.peekLast();
        }

        public boolean remove(final PoolEntry<T, C> entry) {
            return this.available.remove(entry) || this.leased.remove(entry);
        }

        public void free(final PoolEntry<T, C> entry, final boolean reusable) {
            final boolean found = this.leased.remove(entry);
            Asserts.check(found, "Entry %s has not been leased from this pool", entry);
            if (reusable) {
                this.available.addFirst(entry);
            }
        }

        public PoolEntry<T, C> createEntry(final TimeValue timeToLive) {
            final PoolEntry<T, C> entry = new PoolEntry<>(this.route, timeToLive, disposalCallback);
            this.leased.add(entry);
            return entry;
        }

        public void shutdown(final CloseMode closeMode) {
            PoolEntry<T, C> availableEntry;
            while ((availableEntry = available.poll()) != null) {
                availableEntry.discardConnection(closeMode);
            }
            for (final PoolEntry<T, C> entry: this.leased) {
                entry.discardConnection(closeMode);
            }
            this.leased.clear();
        }

        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            buffer.append("[route: ");
            buffer.append(this.route);
            buffer.append("][leased: ");
            buffer.append(this.leased.size());
            buffer.append("][available: ");
            buffer.append(this.available.size());
            buffer.append("]");
            return buffer.toString();
        }

    }
}
