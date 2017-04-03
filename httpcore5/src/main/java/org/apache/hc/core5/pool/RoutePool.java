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

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.apache.hc.core5.io.GracefullyCloseable;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TimeValue;

final class RoutePool<T, C extends GracefullyCloseable> {

    private final T route;
    private final Set<PoolEntry<T, C>> leased;
    private final LinkedList<PoolEntry<T, C>> available;

    RoutePool(final T route) {
        super();
        this.route = route;
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
        if (!this.available.isEmpty()) {
            return this.available.getLast();
        }
        return null;
    }

    public boolean remove(final PoolEntry<T, C> entry) {
        Args.notNull(entry, "Pool entry");
        if (!this.available.remove(entry) && !this.leased.remove(entry)) {
            return false;
        }
        return true;
    }

    public void free(final PoolEntry<T, C> entry, final boolean reusable) {
        Args.notNull(entry, "Pool entry");
        final boolean found = this.leased.remove(entry);
        Asserts.check(found, "Entry %s has not been leased from this pool", entry);
        if (reusable) {
            this.available.addFirst(entry);
        }
    }

    public PoolEntry<T, C> createEntry(final TimeValue timeToLive) {
        final PoolEntry<T, C> entry = new PoolEntry<>(this.route, timeToLive);
        this.leased.add(entry);
        return entry;
    }

    public void shutdown(final ShutdownType shutdownType) {
        for (final PoolEntry<T, C> entry: this.available) {
            entry.discardConnection(shutdownType);
        }
        this.available.clear();
        for (final PoolEntry<T, C> entry: this.leased) {
            entry.discardConnection(shutdownType);
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
