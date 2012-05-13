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
package org.apache.http.pool;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.concurrent.FutureCallback;

/**
 * Abstract synchronous (blocking) pool of connections.
 * <p/>
 * Please note that this class does not maintain its own pool of execution {@link Thread}s.
 * Therefore, one <b>must</b> call {@link Future#get()} or {@link Future#get(long, TimeUnit)}
 * method on the {@link Future} object returned by the
 * {@link #lease(Object, Object, FutureCallback)} method in order for the lease operation
 * to complete.
 *
 * @param <T> the route type that represents the opposite endpoint of a pooled
 *   connection.
 * @param <C> the connection type.
 * @param <E> the type of the pool entry containing a pooled connection.
 * @since 4.2
 */
@ThreadSafe
public abstract class AbstractConnPool<T, C, E extends PoolEntry<T, C>>
                                               implements ConnPool<T, E>, ConnPoolControl<T> {

    private final Lock lock;
    private final ConnFactory<T, C> connFactory;
    private final Map<T, RouteSpecificPool<T, C, E>> routeToPool;
    private final Set<E> leased;
    private final LinkedList<E> available;
    private final LinkedList<PoolEntryFuture<E>> pending;
    private final Map<T, Integer> maxPerRoute;

    private volatile boolean isShutDown;
    private volatile int defaultMaxPerRoute;
    private volatile int maxTotal;

    public AbstractConnPool(
            final ConnFactory<T, C> connFactory,
            int defaultMaxPerRoute,
            int maxTotal) {
        super();
        if (connFactory == null) {
            throw new IllegalArgumentException("Connection factory may not null");
        }
        if (defaultMaxPerRoute <= 0) {
            throw new IllegalArgumentException("Max per route value may not be negative or zero");
        }
        if (maxTotal <= 0) {
            throw new IllegalArgumentException("Max total value may not be negative or zero");
        }
        this.lock = new ReentrantLock();
        this.connFactory = connFactory;
        this.routeToPool = new HashMap<T, RouteSpecificPool<T, C, E>>();
        this.leased = new HashSet<E>();
        this.available = new LinkedList<E>();
        this.pending = new LinkedList<PoolEntryFuture<E>>();
        this.maxPerRoute = new HashMap<T, Integer>();
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        this.maxTotal = maxTotal;
    }

    /**
     * Creates a new entry for the given connection with the given route.
     */
    protected abstract E createEntry(T route, C conn);

    public boolean isShutdown() {
        return this.isShutDown;
    }

    /**
     * Shuts down the pool.
     */
    public void shutdown() throws IOException {
        if (this.isShutDown) {
            return ;
        }
        this.isShutDown = true;
        this.lock.lock();
        try {
            for (E entry: this.available) {
                entry.close();
            }
            for (E entry: this.leased) {
                entry.close();
            }
            for (RouteSpecificPool<T, C, E> pool: this.routeToPool.values()) {
                pool.shutdown();
            }
            this.routeToPool.clear();
            this.leased.clear();
            this.available.clear();
        } finally {
            this.lock.unlock();
        }
    }

    private RouteSpecificPool<T, C, E> getPool(final T route) {
        RouteSpecificPool<T, C, E> pool = this.routeToPool.get(route);
        if (pool == null) {
            pool = new RouteSpecificPool<T, C, E>(route) {

                @Override
                protected E createEntry(C conn) {
                    return AbstractConnPool.this.createEntry(route, conn);
                }

            };
            this.routeToPool.put(route, pool);
        }
        return pool;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Please note that this class does not maintain its own pool of execution
     * {@link Thread}s. Therefore, one <b>must</b> call {@link Future#get()}
     * or {@link Future#get(long, TimeUnit)} method on the {@link Future}
     * returned by this method in order for the lease operation to complete.
     */
    public Future<E> lease(final T route, final Object state, final FutureCallback<E> callback) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        if (this.isShutDown) {
            throw new IllegalStateException("Connection pool shut down");
        }
        return new PoolEntryFuture<E>(this.lock, callback) {

            @Override
            public E getPoolEntry(
                    long timeout,
                    TimeUnit tunit)
                        throws InterruptedException, TimeoutException, IOException {
                return getPoolEntryBlocking(route, state, timeout, tunit, this);
            }

        };
    }

    /**
     * Attempts to lease a connection for the given route and with the given
     * state from the pool.
     * <p/>
     * Please note that this class does not maintain its own pool of execution
     * {@link Thread}s. Therefore, one <b>must</b> call {@link Future#get()}
     * or {@link Future#get(long, TimeUnit)} method on the {@link Future}
     * returned by this method in order for the lease operation to complete.
     *
     * @param route route of the connection.
     * @param state arbitrary object that represents a particular state
     *  (usually a security principal or a unique token identifying
     *  the user whose credentials have been used while establishing the connection).
     *  May be <code>null</code>.
     * @return future for a leased pool entry.
     */
    public Future<E> lease(final T route, final Object state) {
        return lease(route, state, null);
    }

    private E getPoolEntryBlocking(
            final T route, final Object state,
            final long timeout, final TimeUnit tunit,
            final PoolEntryFuture<E> future)
                throws IOException, InterruptedException, TimeoutException {

        Date deadline = null;
        if (timeout > 0) {
            deadline = new Date
                (System.currentTimeMillis() + tunit.toMillis(timeout));
        }

        this.lock.lock();
        try {
            RouteSpecificPool<T, C, E> pool = getPool(route);
            E entry = null;
            while (entry == null) {
                if (this.isShutDown) {
                    throw new IllegalStateException("Connection pool shut down");
                }
                for (;;) {
                    entry = pool.getFree(state);
                    if (entry == null) {
                        break;
                    }
                    if (entry.isClosed() || entry.isExpired(System.currentTimeMillis())) {
                        entry.close();
                        this.available.remove(entry);
                        pool.free(entry, false);
                    } else {
                        break;
                    }
                }
                if (entry != null) {
                    this.available.remove(entry);
                    this.leased.add(entry);
                    return entry;
                }

                // New connection is needed
                int maxPerRoute = getMax(route);
                // Shrink the pool prior to allocating a new connection
                int excess = Math.max(0, pool.getAllocatedCount() + 1 - maxPerRoute);
                if (excess > 0) {
                    for (int i = 0; i < excess; i++) {
                        E lastUsed = pool.getLastUsed();
                        if (lastUsed == null) {
                            break;
                        }
                        lastUsed.close();
                        this.available.remove(lastUsed);
                        pool.remove(lastUsed);
                    }
                }

                if (pool.getAllocatedCount() < maxPerRoute) {
                    int totalUsed = this.leased.size();
                    int freeCapacity = Math.max(this.maxTotal - totalUsed, 0);
                    if (freeCapacity > 0) {
                        int totalAvailable = this.available.size();
                        if (totalAvailable > freeCapacity - 1) {
                            if (!this.available.isEmpty()) {
                                E lastUsed = this.available.removeLast();
                                lastUsed.close();
                                RouteSpecificPool<T, C, E> otherpool = getPool(lastUsed.getRoute());
                                otherpool.remove(lastUsed);
                            }
                        }
                        C conn = this.connFactory.create(route);
                        entry = pool.add(conn);
                        this.leased.add(entry);
                        return entry;
                    }
                }

                boolean success = false;
                try {
                    pool.queue(future);
                    this.pending.add(future);
                    success = future.await(deadline);
                } finally {
                    // In case of 'success', we were woken up by the
                    // connection pool and should now have a connection
                    // waiting for us, or else we're shutting down.
                    // Just continue in the loop, both cases are checked.
                    pool.unqueue(future);
                    this.pending.remove(future);
                }
                // check for spurious wakeup vs. timeout
                if (!success && (deadline != null) &&
                    (deadline.getTime() <= System.currentTimeMillis())) {
                    break;
                }
            }
            throw new TimeoutException("Timeout waiting for connection");
        } finally {
            this.lock.unlock();
        }
    }

    private void notifyPending(final RouteSpecificPool<T, C, E> pool) {
        PoolEntryFuture<E> future = pool.nextPending();
        if (future != null) {
            this.pending.remove(future);
        } else {
            future = this.pending.poll();
        }
        if (future != null) {
            future.wakeup();
        }
    }

    public void release(E entry, boolean reusable) {
        this.lock.lock();
        try {
            if (this.leased.remove(entry)) {
                RouteSpecificPool<T, C, E> pool = getPool(entry.getRoute());
                pool.free(entry, reusable);
                if (reusable && !this.isShutDown) {
                    this.available.addFirst(entry);
                } else {
                    entry.close();
                }
                notifyPending(pool);
            }
        } finally {
            this.lock.unlock();
        }
    }

    private int getMax(final T route) {
        Integer v = this.maxPerRoute.get(route);
        if (v != null) {
            return v.intValue();
        } else {
            return this.defaultMaxPerRoute;
        }
    }

    public void setMaxTotal(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("Max value may not be negative or zero");
        }
        this.lock.lock();
        try {
            this.maxTotal = max;
        } finally {
            this.lock.unlock();
        }
    }

    public int getMaxTotal() {
        this.lock.lock();
        try {
            return this.maxTotal;
        } finally {
            this.lock.unlock();
        }
    }

    public void setDefaultMaxPerRoute(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("Max value may not be negative or zero");
        }
        this.lock.lock();
        try {
            this.defaultMaxPerRoute = max;
        } finally {
            this.lock.unlock();
        }
    }

    public int getDefaultMaxPerRoute() {
        this.lock.lock();
        try {
            return this.defaultMaxPerRoute;
        } finally {
            this.lock.unlock();
        }
    }

    public void setMaxPerRoute(final T route, int max) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        if (max <= 0) {
            throw new IllegalArgumentException("Max value may not be negative or zero");
        }
        this.lock.lock();
        try {
            this.maxPerRoute.put(route, max);
        } finally {
            this.lock.unlock();
        }
    }

    public int getMaxPerRoute(T route) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        this.lock.lock();
        try {
            return getMax(route);
        } finally {
            this.lock.unlock();
        }
    }

    public PoolStats getTotalStats() {
        this.lock.lock();
        try {
            return new PoolStats(
                    this.leased.size(),
                    this.pending.size(),
                    this.available.size(),
                    this.maxTotal);
        } finally {
            this.lock.unlock();
        }
    }

    public PoolStats getStats(final T route) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        this.lock.lock();
        try {
            RouteSpecificPool<T, C, E> pool = getPool(route);
            return new PoolStats(
                    pool.getLeasedCount(),
                    pool.getPendingCount(),
                    pool.getAvailableCount(),
                    getMax(route));
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Closes connections that have been idle longer than the given period
     * of time and evicts them from the pool.
     *
     * @param idletime maximum idle time.
     * @param tunit time unit.
     */
    public void closeIdle(long idletime, final TimeUnit tunit) {
        if (tunit == null) {
            throw new IllegalArgumentException("Time unit must not be null.");
        }
        long time = tunit.toMillis(idletime);
        if (time < 0) {
            time = 0;
        }
        long deadline = System.currentTimeMillis() - time;
        this.lock.lock();
        try {
            Iterator<E> it = this.available.iterator();
            while (it.hasNext()) {
                E entry = it.next();
                if (entry.getUpdated() <= deadline) {
                    entry.close();
                    RouteSpecificPool<T, C, E> pool = getPool(entry.getRoute());
                    pool.remove(entry);
                    it.remove();
                    notifyPending(pool);
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Closes expired connections and evicts them from the pool.
     */
    public void closeExpired() {
        long now = System.currentTimeMillis();
        this.lock.lock();
        try {
            Iterator<E> it = this.available.iterator();
            while (it.hasNext()) {
                E entry = it.next();
                if (entry.isExpired(now)) {
                    entry.close();
                    RouteSpecificPool<T, C, E> pool = getPool(entry.getRoute());
                    pool.remove(entry);
                    it.remove();
                    notifyPending(pool);
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("[leased: ");
        buffer.append(this.leased);
        buffer.append("][available: ");
        buffer.append(this.available);
        buffer.append("][pending: ");
        buffer.append(this.pending);
        buffer.append("]");
        return buffer.toString();
    }

}
