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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class PoolEntry<T, C> {

    private static AtomicLong COUNTER = new AtomicLong();

    private final long id;
    private final T route;
    private final C conn;
    private final long created;
    private final long validUnit;

    private Object state;
    private long updated;
    private long expiry;

    public PoolEntry(final T route, final C conn,
            final long timeToLive, final TimeUnit tunit) {
        super();
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("Connection may not be null");
        }
        if (tunit == null) {
            throw new IllegalArgumentException("Time unit may not be null");
        }
        this.route = route;
        this.conn = conn;
        this.id = COUNTER.incrementAndGet();
        this.created = System.currentTimeMillis();
        if (timeToLive > 0) {
            this.validUnit = this.created + tunit.toMillis(timeToLive);
        } else {
            this.validUnit = Long.MAX_VALUE;
        }
        this.expiry = this.validUnit;
    }

    public PoolEntry(final T route, final C conn) {
        this(route, conn, 0, TimeUnit.MILLISECONDS);
    }

    public T getRoute() {
        return this.route;
    }

    public C getConnection() {
        return this.conn;
    }

    public long getCreated() {
        return this.created;
    }

    public long getValidUnit() {
        return this.validUnit;
    }

    public Object getState() {
        return this.state;
    }

    public void setState(final Object state) {
        this.state = state;
    }

    public long getUpdated() {
        return this.updated;
    }

    public long getExpiry() {
        return this.expiry;
    }

    public void updateExpiry(final long time, final TimeUnit tunit) {
        if (tunit == null) {
            throw new IllegalArgumentException("Time unit may not be null");
        }
        this.updated = System.currentTimeMillis();
        long newExpiry;
        if (time > 0) {
            newExpiry = this.updated + tunit.toMillis(time);
        } else {
            newExpiry = Long.MAX_VALUE;
        }
        this.expiry = Math.min(newExpiry, this.validUnit);
    }

    public boolean isExpired(final long now) {
        return now >= this.expiry;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("[id:");
        buffer.append(this.id);
        buffer.append("][route:");
        buffer.append(this.route);
        buffer.append("][state:");
        buffer.append(this.state);
        buffer.append("]");
        return buffer.toString();
    }

}
