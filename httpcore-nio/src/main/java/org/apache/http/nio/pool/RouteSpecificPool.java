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

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.pool.PoolEntry;

@NotThreadSafe
abstract class RouteSpecificPool<T, C, E extends PoolEntry<T, C>> {

    private final T route;
    private final Set<E> leased;
    private final LinkedList<E> available;
    private final Map<SessionRequest, BasicFuture<E>> pending;

    RouteSpecificPool(final T route) {
        super();
        this.route = route;
        this.leased = new HashSet<E>();
        this.available = new LinkedList<E>();
        this.pending = new HashMap<SessionRequest, BasicFuture<E>>();
    }

    protected abstract E createEntry(T route, C conn);

    public int getLeasedCount() {
        return this.leased.size();
    }

    public int getPendingCount() {
        return this.pending.size();
    }

    public int getAvailableCount() {
        return this.available.size();
    }

    public int getAllocatedCount() {
        return this.available.size() + this.leased.size() + this.pending.size();
    }

    public E getFree(final Object state) {
        if (!this.available.isEmpty()) {
            if (state != null) {
                Iterator<E> it = this.available.iterator();
                while (it.hasNext()) {
                    E entry = it.next();
                    if (state.equals(entry.getState())) {
                        it.remove();
                        this.leased.add(entry);
                        return entry;
                    }
                }
            }
            Iterator<E> it = this.available.iterator();
            while (it.hasNext()) {
                E entry = it.next();
                if (entry.getState() == null) {
                    it.remove();
                    this.leased.add(entry);
                    return entry;
                }
            }
        }
        return null;
    }

    public E getLastUsed() {
        if (!this.available.isEmpty()) {
            return this.available.getLast();
        } else {
            return null;
        }
    }

    public boolean remove(final E entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Pool entry may not be null");
        }
        if (!this.available.remove(entry)) {
            if (!this.leased.remove(entry)) {
                return false;
            }
        }
        return true;
    }

    public void free(final E entry, boolean reusable) {
        if (entry == null) {
            throw new IllegalArgumentException("Pool entry may not be null");
        }
        boolean found = this.leased.remove(entry);
        if (!found) {
            throw new IllegalStateException("Entry " + entry +
                    " has not been leased from this pool");
        }
        if (reusable) {
            this.available.addFirst(entry);
        }
    }

    public void addPending(
            final SessionRequest sessionRequest,
            final BasicFuture<E> future) {
        this.pending.put(sessionRequest, future);
    }

    private BasicFuture<E> removeRequest(final SessionRequest request) {
        BasicFuture<E> future = this.pending.remove(request);
        if (future == null) {
            throw new IllegalStateException("Invalid session request");
        }
        return future;
    }

    public E createEntry(final SessionRequest request, final C conn) {
        E entry = createEntry(this.route, conn);
        this.leased.add(entry);
        return entry;
    }

    public void completed(SessionRequest request, E entry) {
        BasicFuture<E> future = removeRequest(request);
        future.completed(entry);
    }

    public void cancelled(final SessionRequest request) {
        BasicFuture<E> future = removeRequest(request);
        future.cancel(true);
    }

    public void failed(final SessionRequest request, final Exception ex) {
        BasicFuture<E> future = removeRequest(request);
        future.failed(ex);
    }

    public void timeout(final SessionRequest request) {
        BasicFuture<E> future = removeRequest(request);
        future.failed(new SocketTimeoutException());
    }

    public void shutdown() {
        for (SessionRequest sessionRequest: this.pending.keySet()) {
            sessionRequest.cancel();
        }
        this.pending.clear();
        for (E entry: this.available) {
            entry.close();
        }
        this.available.clear();
        for (E entry: this.leased) {
            entry.close();
        }
        this.leased.clear();
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("[route: ");
        buffer.append(this.route);
        buffer.append("][leased: ");
        buffer.append(this.leased.size());
        buffer.append("][available: ");
        buffer.append(this.available.size());
        buffer.append("][pending: ");
        buffer.append(this.pending.size());
        buffer.append("]");
        return buffer.toString();
    }

}
