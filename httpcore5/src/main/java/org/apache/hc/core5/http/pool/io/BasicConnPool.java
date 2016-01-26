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
package org.apache.hc.core5.http.pool.io;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.annotation.ThreadSafe;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.pool.io.AbstractConnPool;
import org.apache.hc.core5.pool.io.ConnFactory;

/**
 * A very basic {@link org.apache.hc.core5.pool.ConnPool} implementation that
 * represents a pool of blocking {@link HttpClientConnection} connections
 * identified by an {@link HttpHost} instance. Please note this pool
 * implementation does not support complex routes via a proxy cannot
 * differentiate between direct and proxied connections.
 *
 * @see HttpHost
 * @since 4.2
 */
@ThreadSafe
public class BasicConnPool extends AbstractConnPool<HttpHost, HttpClientConnection, BasicPoolEntry> {

    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 25;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 5;

    private static final AtomicLong COUNTER = new AtomicLong();

    /**
     * @since 5.0
     */
    public BasicConnPool(
            final ConnFactory<HttpHost, HttpClientConnection> connFactory,
            final int defaultMaxPerRoute,
            final int maxTotal) {
        super(connFactory, defaultMaxPerRoute, maxTotal);
    }

    public BasicConnPool(final ConnFactory<HttpHost, HttpClientConnection> connFactory) {
        this(connFactory, DEFAULT_MAX_CONNECTIONS_PER_ROUTE, DEFAULT_MAX_TOTAL_CONNECTIONS);
    }

    /**
     * @since 4.3
     */
    public BasicConnPool(final SocketConfig sconfig, final ConnectionConfig cconfig) {
        this(new BasicConnFactory(sconfig, cconfig));
    }

    /**
     * @since 4.3
     */
    public BasicConnPool() {
        this(new BasicConnFactory(SocketConfig.DEFAULT, ConnectionConfig.DEFAULT));
    }

    @Override
    protected BasicPoolEntry createEntry(
            final HttpHost host,
            final HttpClientConnection conn) {
        return new BasicPoolEntry(Long.toString(COUNTER.getAndIncrement()), host, conn);
    }

    @Override
    protected boolean validate(final BasicPoolEntry entry) {
        return !entry.getConnection().isStale();
    }

}
