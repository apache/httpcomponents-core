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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.pool.PoolEntry;
import org.apache.http.pool.PoolStats;

public abstract class SessionPool<T, E extends PoolEntry<T, IOSession>> {

    private final ConnectingIOReactor ioreactor;
    private final SessionRequestCallback sessionRequestCallback;
    private final Map<T, RouteSpecificPool<T, E>> routeToPool;
    private final LinkedList<LeaseRequest<T, E>> leasingRequests;
    private final Set<SessionRequest> pendingSessions;
    private final Set<E> leasedSessions;
    private final LinkedList<E> availableSessions;
    private final Map<T, Integer> maxPerRoute;
    private final Lock lock;

    private volatile boolean isShutDown;
    private volatile int defaultMaxPerRoute;
    private volatile int maxTotal;

    public SessionPool(
            final ConnectingIOReactor ioreactor,
            int defaultMaxPerRoute,
            int maxTotal) {
        super();
        if (ioreactor == null) {
            throw new IllegalArgumentException("I/O reactor may not be null");
        }
        if (defaultMaxPerRoute <= 0) {
            throw new IllegalArgumentException("Max per route value may not be negative or zero");
        }
        if (maxTotal <= 0) {
            throw new IllegalArgumentException("Max total value may not be negative or zero");
        }
        this.ioreactor = ioreactor;
        this.sessionRequestCallback = new InternalSessionRequestCallback();
        this.routeToPool = new HashMap<T, RouteSpecificPool<T, E>>();
        this.leasingRequests = new LinkedList<LeaseRequest<T, E>>();
        this.pendingSessions = new HashSet<SessionRequest>();
        this.leasedSessions = new HashSet<E>();
        this.availableSessions = new LinkedList<E>();
        this.maxPerRoute = new HashMap<T, Integer>();
        this.lock = new ReentrantLock();
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        this.maxTotal = maxTotal;
    }

    protected abstract SocketAddress resolveRemoteAddress(T route);

    protected abstract SocketAddress resolveLocalAddress(T route);

    protected abstract E createEntry(T route, IOSession session);

    protected abstract void closeEntry(E entry);

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
            for (SessionRequest sessionRequest: this.pendingSessions) {
                sessionRequest.cancel();
            }
            for (E entry: this.availableSessions) {
                closeEntry(entry);
            }
            for (E entry: this.leasedSessions) {
                closeEntry(entry);
            }
            for (RouteSpecificPool<T, E> pool: this.routeToPool.values()) {
                pool.shutdown();
            }
            this.routeToPool.clear();
            this.leasedSessions.clear();
            this.pendingSessions.clear();
            this.availableSessions.clear();
            this.leasingRequests.clear();
            this.ioreactor.shutdown(waitMs);
        } finally {
            this.lock.unlock();
        }
    }

    private RouteSpecificPool<T, E> getPool(final T route) {
        RouteSpecificPool<T, E> pool = this.routeToPool.get(route);
        if (pool == null) {
            pool = new RouteSpecificPool<T, E>(route) {

                @Override
                protected E createEntry(final T route, final IOSession session) {
                    return SessionPool.this.createEntry(route, session);
                }

            };
            this.routeToPool.put(route, pool);
        }
        return pool;
    }

    public void lease(
            final T route, final Object state,
            final long connectTimeout, final TimeUnit tunit,
            final PoolEntryCallback<E> callback) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        if (tunit == null) {
            throw new IllegalArgumentException("Time unit may not be null.");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback may not be null.");
        }
        if (this.isShutDown) {
            throw new IllegalStateException("Session pool has been shut down");
        }
        this.lock.lock();
        try {
            int timeout = (int) tunit.toMillis(connectTimeout);
            if (timeout < 0) {
                timeout = 0;
            }
            LeaseRequest<T, E> request = new LeaseRequest<T, E>(route, state, timeout, callback);
            this.leasingRequests.add(request);

            processPendingRequests();
        } finally {
            this.lock.unlock();
        }
    }

    public void release(final E entry, boolean reusable) {
        if (this.isShutDown) {
            return;
        }
        this.lock.lock();
        try {
            if (this.leasedSessions.remove(entry)) {
                RouteSpecificPool<T, E> pool = getPool(entry.getRoute());
                pool.freeEntry(entry, reusable);
                if (reusable) {
                    this.availableSessions.add(entry);
                } else {
                    closeEntry(entry);
                }
                processPendingRequests();
            }
        } finally {
            this.lock.unlock();
        }
    }

    private void processPendingRequests() {
        ListIterator<LeaseRequest<T, E>> it = this.leasingRequests.listIterator();
        while (it.hasNext()) {
            LeaseRequest<T, E> request = it.next();

            T route = request.getRoute();
            Object state = request.getState();
            int timeout = request.getConnectTimeout();
            PoolEntryCallback<E> callback = request.getCallback();

            RouteSpecificPool<T, E> pool = getPool(request.getRoute());
            E entry = null;
            for (;;) {
                entry = pool.getFreeEntry(state);
                if (entry == null) {
                    break;
                }
                if (entry.isExpired(System.currentTimeMillis())) {
                    closeEntry(entry);
                    this.availableSessions.remove(entry);
                    pool.freeEntry(entry, false);
                } else {
                    break;
                }
            }
            if (entry != null) {
                it.remove();
                this.availableSessions.remove(entry);
                this.leasedSessions.add(entry);
                callback.completed(entry);
                continue;
            }
            if (pool.getAllocatedCount() < getMaxPerRoute(route)) {
                int totalUsed = this.pendingSessions.size() + this.leasedSessions.size();
                int freeCapacity = Math.max(this.maxTotal - totalUsed, 0);
                if (freeCapacity == 0) {
                    continue;
                }
                int totalAvailable = this.availableSessions.size();
                if (totalAvailable > freeCapacity - 1) {
                    dropLastUsed();
                }
                it.remove();
                SessionRequest sessionRequest = this.ioreactor.connect(
                        resolveRemoteAddress(route),
                        resolveLocalAddress(route),
                        route,
                        this.sessionRequestCallback);
                sessionRequest.setConnectTimeout(timeout);
                this.pendingSessions.add(sessionRequest);
                pool.addPending(sessionRequest, callback);
            }
        }
    }

    private void dropLastUsed() {
        if (!this.availableSessions.isEmpty()) {
            E entry = this.availableSessions.removeFirst();
            closeEntry(entry);
            RouteSpecificPool<T, E> pool = getPool(entry.getRoute());
            pool.remove(entry);
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
            this.pendingSessions.remove(request);
            RouteSpecificPool<T, E> pool = getPool(route);
            E entry = pool.completed(request);
            this.leasedSessions.add(entry);
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
            this.pendingSessions.remove(request);
            RouteSpecificPool<T, E> pool = getPool(route);
            pool.cancelled(request);
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
            this.pendingSessions.remove(request);
            RouteSpecificPool<T, E> pool = getPool(route);
            pool.failed(request);
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
            this.pendingSessions.remove(request);
            RouteSpecificPool<T, E> pool = getPool(route);
            pool.timeout(request);
        } finally {
            this.lock.unlock();
        }
    }

    private int getMaxPerRoute(final T route) {
        Integer v = this.maxPerRoute.get(route);
        if (v != null) {
            return v.intValue();
        } else {
            return this.defaultMaxPerRoute;
        }
    }

    public void setTotalMax(int max) {
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

    public void setDefaultMaxPerHost(int max) {
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

    public void setMaxPerHost(final T route, int max) {
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

    public PoolStats getTotalStats() {
        this.lock.lock();
        try {
            return new PoolStats(
                    this.leasedSessions.size(),
                    this.pendingSessions.size(),
                    this.availableSessions.size(),
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
            RouteSpecificPool<T, E> pool = getPool(route);
            return new PoolStats(
                    pool.getLeasedCount(),
                    pool.getPendingCount(),
                    pool.getAvailableCount(),
                    getMaxPerRoute(route));
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
            Iterator<E> it = this.availableSessions.iterator();
            while (it.hasNext()) {
                E entry = it.next();
                if (entry.getUpdated() <= deadline) {
                    closeEntry(entry);
                    RouteSpecificPool<T, E> pool = getPool(entry.getRoute());
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
            Iterator<E> it = this.availableSessions.iterator();
            while (it.hasNext()) {
                E entry = it.next();
                if (entry.isExpired(now)) {
                    closeEntry(entry);
                    RouteSpecificPool<T, E> pool = getPool(entry.getRoute());
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
        buffer.append(this.leasedSessions);
        buffer.append("][available: ");
        buffer.append(this.availableSessions);
        buffer.append("][pending: ");
        buffer.append(this.pendingSessions);
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
