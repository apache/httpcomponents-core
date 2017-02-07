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
package org.apache.hc.core5.http.impl.bootstrap;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.pool.ConnPool;
import org.apache.hc.core5.pool.PoolEntry;

/**
 * @since 5.0
 */
final class PoolEntryHolder<T, C extends Closeable> {

    private final ConnPool<T, C> connPool;
    private final Callback<C> shutdownCallback;
    private final AtomicBoolean reusable;
    private final AtomicReference<PoolEntry<T, C>> poolEntryRef;

    public PoolEntryHolder(
            final ConnPool<T, C> connPool,
            final PoolEntry<T, C> poolEntry,
            final Callback<C> shutdownCallback) {
        this.connPool = connPool;
        this.poolEntryRef = new AtomicReference<>(poolEntry);
        this.shutdownCallback = shutdownCallback;
        this.reusable = new AtomicBoolean(false);
    }

    public C getConnection() {
        final PoolEntry<T, C> poolEntry = poolEntryRef.get();
        return poolEntry != null ? poolEntry.getConnection() : null;
    }

    public void markReusable() {
        reusable.set(true);
    }

    public void releaseConnection() {
        final PoolEntry<T, C> poolEntry = poolEntryRef.getAndSet(null);
        if (poolEntry != null) {
            connPool.release(poolEntry, reusable.get());
        }
    }

    public void abortConnection() {
        final PoolEntry<T, C> poolEntry = poolEntryRef.getAndSet(null);
        if (poolEntry != null) {
            poolEntry.discardConnection(shutdownCallback);
            connPool.release(poolEntry, false);
        }
    }

    public boolean isReleased() {
        return poolEntryRef.get() == null;
    }

}
