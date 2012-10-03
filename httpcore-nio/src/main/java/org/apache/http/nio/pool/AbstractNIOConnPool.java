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
package org.apache.http.nio.pool;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.pool.ConnPool;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolEntry;
import org.apache.http.pool.PoolStats;

/**
 * Abstract non-blocking connection pool.
 *
 * @param <T> route
 * @param <C> connection object
 * @param <E> pool entry
 *
 * @since 4.2
 */
@ThreadSafe
public abstract class AbstractNIOConnPool<T, C, E extends PoolEntry<T, C>>
                                                  implements ConnPool<T, E>, ConnPoolControl<T> {

    private final ConnectingIOReactor ioreactor;
    private final NIOConnFactory<T, C> connFactory;
    private final SessionRequestCallback sessionRequestCallback;
    private final Map<T, RouteSpecificPool<T, C, E>> routeToPool;
    private final LinkedList<LeaseRequest<T, C, E>> leasingRequests;
    private final Set<SessionRequest> pending;
    private final Set<E> leased;
    private final LinkedList<E> available;
    private final Map<T, Integer> maxPerRoute;
    private final Lock lock;

    private volatile boolean isShutDown;
    private volatile int defaultMaxPerRoute;
    private volatile int maxTotal;

    public AbstractNIOConnPool(
            final ConnectingIOReactor ioreactor,
            final NIOConnFactory<T, C> connFactory,
            int defaultMaxPerRoute,
            int maxTotal) {
        super();
        if (ioreactor == null) {
            throw new IllegalArgumentException("I/O reactor may not be null");
        }
        if (connFactory == null) {
            throw new IllegalArgumentException("Connection factory may not null");
        }
        if (defaultMaxPerRoute <= 0) {
            throw new IllegalArgumentException("Max per route value may not be negative or zero");
        }
        if (maxTotal <= 0) {
            throw new IllegalArgumentException("Max total value may not be negative or zero");
        }
        this.ioreactor = ioreactor;
        this.connFactory = connFactory;
        this.sessionRequestCallback = new InternalSessionRequestCallback();
        this.routeToPool = new HashMap<T, RouteSpecificPool<T, C, E>>();
        this.leasingRequests = new LinkedList<LeaseRequest<T, C, E>>();
        this.pending = new HashSet<SessionRequest>();
        this.leased = new HashSet<E>();
        this.available = new LinkedList<E>();
        this.maxPerRoute = new HashMap<T, Integer>();
        this.lock = new ReentrantLock();
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        this.maxTotal = maxTotal;
    }

    protected abstract SocketAddress resolveRemoteAddress(T route);

    protected abstract SocketAddress resolveLocalAddress(T route);

    protected abstract E createEntry(T route, C conn);

    public boolean isShutdown() {
        return this.isShutDown;
    }

    public void shutdown(long waitMs) throws IOException {
        if (this.isShutDown) {
            return ;
        }
        this.isShutDown = true;
        this.lock.lock();
        try {
            for (SessionRequest sessionRequest: this.pending) {
                sessionRequest.cancel();
            }
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
            this.pending.clear();
            this.available.clear();
            this.leasingRequests.clear();
            this.ioreactor.shutdown(waitMs);
        } finally {
            this.lock.unlock();
        }
    }

    private RouteSpecificPool<T, C, E> getPool(final T route) {
        RouteSpecificPool<T, C, E> pool = this.routeToPool.get(route);
        if (pool == null) {
            pool = new RouteSpecificPool<T, C, E>(route) {

                @Override
                protected E createEntry(final T route, final C conn) {
                    return AbstractNIOConnPool.this.createEntry(route, conn);
                }

            };
            this.routeToPool.put(route, pool);
        }
        return pool;
    }

    public Future<E> lease(
            final T route, final Object state,
            final long connectTimeout, final TimeUnit tunit,
            final FutureCallback<E> callback) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        if (tunit == null) {
            throw new IllegalArgumentException("Time unit may not be null.");
        }
        if (this.isShutDown) {
            throw new IllegalStateException("Session pool has been shut down");
        }
        this.lock.lock();
        try {
            long timeout = connectTimeout > 0 ? tunit.toMillis(connectTimeout) : 0;
            BasicFuture<E> future = new BasicFuture<E>(callback);
            LeaseRequest<T, C, E> request = new LeaseRequest<T, C, E>(route, state, timeout, future);
            this.leasingRequests.add(request);

            processPendingRequests();
            return future;
        } finally {
            this.lock.unlock();
        }
    }

    public Future<E> lease(final T route, final Object state, final FutureCallback<E> callback) {
        return lease(route, state, -1, TimeUnit.MICROSECONDS, callback);
    }

    public Future<E> lease(final T route, final Object state) {
        return lease(route, state, -1, TimeUnit.MICROSECONDS, null);
    }

    public void release(final E entry, boolean reusable) {
        if (entry == null) {
            return;
        }
        if (this.isShutDown) {
            return;
        }
        this.lock.lock();
        try {
            if (this.leased.remove(entry)) {
                RouteSpecificPool<T, C, E> pool = getPool(entry.getRoute());
                pool.free(entry, reusable);
                if (reusable) {
                    this.available.addFirst(entry);
                } else {
                    entry.close();
                }
                processPendingRequests();
            }
        } finally {
            this.lock.unlock();
        }
    }

    private void processPendingRequests() {
        ListIterator<LeaseRequest<T, C, E>> it = this.leasingRequests.listIterator();
        while (it.hasNext()) {
            LeaseRequest<T, C, E> request = it.next();

            T route = request.getRoute();
            Object state = request.getState();
            long deadline = request.getDeadline();
            BasicFuture<E> future = request.getFuture();

            long now = System.currentTimeMillis();
            if (now > deadline) {
                it.remove();
                future.failed(new TimeoutException());
                continue;
            }

            RouteSpecificPool<T, C, E> pool = getPool(route);
            E entry = null;
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
                it.remove();
                this.available.remove(entry);
                this.leased.add(entry);
                future.completed(entry);
                continue;
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
                int totalUsed = this.pending.size() + this.leased.size();
                int freeCapacity = Math.max(this.maxTotal - totalUsed, 0);
                if (freeCapacity == 0) {
                    continue;
                }
                int totalAvailable = this.available.size();
                if (totalAvailable > freeCapacity - 1) {
                    if (!this.available.isEmpty()) {
                        E lastUsed = this.available.removeLast();
                        lastUsed.close();
                        RouteSpecificPool<T, C, E> otherpool = getPool(lastUsed.getRoute());
                        otherpool.remove(lastUsed);
                    }
                }
                it.remove();
                SessionRequest sessionRequest = this.ioreactor.connect(
                        resolveRemoteAddress(route),
                        resolveLocalAddress(route),
                        route,
                        this.sessionRequestCallback);
                int timout = request.getConnectTimeout() < Integer.MAX_VALUE ?
                        (int) request.getConnectTimeout() : Integer.MAX_VALUE;
                sessionRequest.setConnectTimeout(timout);
                this.pending.add(sessionRequest);
                pool.addPending(sessionRequest, future);
            }
        }
    }

    public void validatePendingRequests() {
        this.lock.lock();
        try {
            long now = System.currentTimeMillis();
            ListIterator<LeaseRequest<T, C, E>> it = this.leasingRequests.listIterator();
            while (it.hasNext()) {
                LeaseRequest<T, C, E> request = it.next();
                long deadline = request.getDeadline();
                if (now > deadline) {
                    it.remove();
                    BasicFuture<E> future = request.getFuture();
                    future.failed(new TimeoutException());
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    protected void requestCompleted(final SessionRequest request) {
        if (this.isShutDown) {
            return;
        }
        @SuppressWarnings("unchecked")
        T route = (T) request.getAttachment();
        this.lock.lock();
        try {
            this.pending.remove(request);
            RouteSpecificPool<T, C, E> pool = getPool(route);
            IOSession session = request.getSession();
            try {
                C conn = this.connFactory.create(route, session);
                E entry = pool.createEntry(request, conn);
                this.leased.add(entry);
                pool.completed(request, entry);

            } catch (IOException ex) {
                pool.failed(request, ex);
            }
        } finally {
            this.lock.unlock();
        }
    }

    protected void requestCancelled(final SessionRequest request) {
        if (this.isShutDown) {
            return;
        }
        @SuppressWarnings("unchecked")
        T route = (T) request.getAttachment();
        this.lock.lock();
        try {
            this.pending.remove(request);
            RouteSpecificPool<T, C, E> pool = getPool(route);
            pool.cancelled(request);
            processPendingRequests();
        } finally {
            this.lock.unlock();
        }
    }

    protected void requestFailed(final SessionRequest request) {
        if (this.isShutDown) {
            return;
        }
        @SuppressWarnings("unchecked")
        T route = (T) request.getAttachment();
        this.lock.lock();
        try {
            this.pending.remove(request);
            RouteSpecificPool<T, C, E> pool = getPool(route);
            pool.failed(request, request.getException());
            processPendingRequests();
        } finally {
            this.lock.unlock();
        }
    }

    protected void requestTimeout(final SessionRequest request) {
        if (this.isShutDown) {
            return;
        }
        @SuppressWarnings("unchecked")
        T route = (T) request.getAttachment();
        this.lock.lock();
        try {
            this.pending.remove(request);
            RouteSpecificPool<T, C, E> pool = getPool(route);
            pool.timeout(request);
            processPendingRequests();
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
                }
            }
            processPendingRequests();
        } finally {
            this.lock.unlock();
        }
    }

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
                }
            }
            processPendingRequests();
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

    class InternalSessionRequestCallback implements SessionRequestCallback {

        public void completed(final SessionRequest request) {
            requestCompleted(request);
        }

        public void cancelled(final SessionRequest request) {
            requestCancelled(request);
        }

        public void failed(final SessionRequest request) {
            requestFailed(request);
        }

        public void timeout(final SessionRequest request) {
            requestTimeout(request);
        }

    }

}
