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

import java.net.ConnectException;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.pool.PoolEntry;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

@NotThreadSafe
abstract class RouteSpecificPool<T, C, E extends PoolEntry<T, C>> {

    private final T route;
    private final Set<E> leased;
    private final Deque<E> available;
    private final ConcurrentHashMap<SessionRequest, BasicFuture<E>> pending;

    RouteSpecificPool(final T route) {
        super();
        this.route = route;
        this.leased = Collections.newSetFromMap(new ConcurrentHashMap<E,Boolean>()) ;
        this.available = new ConcurrentLinkedDeque<E>();
        this.pending = new ConcurrentHashMap<SessionRequest, BasicFuture<E>>();
    }

    public T getRoute() {
        return this.route;
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
            E selectedEntry = null ;
            if (state != null) {
                final Iterator<E> it = this.available.iterator();
                while (it.hasNext()) {
                    final E entry = it.next();
                    if (state.equals(entry.getState())) {
                        selectedEntry = entry ;
                        break ;
                    }
                }
            }
            if (selectedEntry == null) {
                final Iterator<E> it = this.available.iterator();
                while (it.hasNext()) {
                    final E entry = it.next();
                    if (entry.getState() == null) {
                        selectedEntry = entry ;
                        break ;
                    }
                }
            }

            if (selectedEntry != null) {
                if (available.remove(selectedEntry)) {
                    this.leased.add(selectedEntry);
                    return selectedEntry;
                }
                else {
                    return getFree(state) ;
                }
            }
        }
        return null;
    }

    public E getLastUsed() {
        return this.available.peekLast();
    }

    public boolean remove(final E entry) {
        Args.notNull(entry, "Pool entry");
        if (!this.leased.remove(entry)) {
            if (!this.available.remove(entry)) {
                return false;
            }
        }
        return true;
    }

    public void free(final E entry, final boolean reusable) {
        Args.notNull(entry, "Pool entry");
        final boolean found = this.leased.remove(entry);
        Asserts.check(found, "Entry %s has not been leased from this pool", entry);
        if (reusable) {
            this.available.offerFirst(entry);
        }
    }

    public void addPending(
            final SessionRequest sessionRequest,
            final BasicFuture<E> future) {
        this.pending.putIfAbsent(sessionRequest, future);
    }

    private BasicFuture<E> removeRequest(final SessionRequest request) {
        final BasicFuture<E> future = this.pending.remove(request);
        Asserts.notNull(future, "Session request future");
        return future;
    }

    public E createEntry(final SessionRequest request, final C conn) {
        final E entry = createEntry(this.route, conn);
        this.leased.add(entry);
        return entry;
    }

    public void completed(final SessionRequest request, final E entry) {
        final BasicFuture<E> future = removeRequest(request);
        future.completed(entry);
    }

    public void cancelled(final SessionRequest request) {
        final BasicFuture<E> future = removeRequest(request);
        future.cancel(true);
    }

    public void failed(final SessionRequest request, final Exception ex) {
        final BasicFuture<E> future = removeRequest(request);
        future.failed(ex);
    }

    public void timeout(final SessionRequest request) {
        final BasicFuture<E> future = removeRequest(request);
        future.failed(new ConnectException());
    }

    public void shutdown() {
        for (final SessionRequest sessionRequest: this.pending.keySet()) {
            sessionRequest.cancel();
        }
        this.pending.clear();
        for (final E entry: this.available) {
            entry.close();
        }
        this.available.clear();
        for (final E entry: this.leased) {
            entry.close();
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
        buffer.append("][pending: ");
        buffer.append(this.pending.size());
        buffer.append("]");
        return buffer.toString();
    }

}
