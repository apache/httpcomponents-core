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
package org.apache.hc.core5.http.impl.nio.pool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.pool.nio.AbstractNIOConnPool;
import org.apache.hc.core5.pool.nio.NIOConnFactory;
import org.apache.hc.core5.pool.nio.SocketAddressResolver;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;

/**
 * Basic {@link org.apache.hc.core5.pool.ConnPool} implementation that
 * represents a pool of non-blocking {@link IOSession}s
 * identified by an {@link HttpHost} instance. Please note this pool
 * implementation does not support complex routes via a proxy cannot
 * differentiate between direct and proxied connections.
 *
 * @see HttpHost
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class BasicNIOConnPool extends AbstractNIOConnPool<HttpHost, IOSession, BasicNIOPoolEntry> {

    private static final AtomicLong COUNTER = new AtomicLong();

    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 25;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 5;

    private final int connectTimeout;

    static class BasicAddressResolver implements SocketAddressResolver<HttpHost> {

        @Override
        public SocketAddress resolveLocalAddress(final HttpHost host) {
            return null;
        }

        @Override
        public SocketAddress resolveRemoteAddress(final HttpHost host) {
            final String hostname = host.getHostName();
            int port = host.getPort();
            if (port == -1) {
                if (host.getSchemeName().equalsIgnoreCase("http")) {
                    port = 80;
                } else if (host.getSchemeName().equalsIgnoreCase("https")) {
                    port = 443;
                }
            }
            return new InetSocketAddress(hostname, port);
        }

    }

    static class BasicNIOConnFactory implements NIOConnFactory<HttpHost, IOSession> {

        public BasicNIOConnFactory() {
            super();
        }

        @Override
        public IOSession create(final HttpHost route, final IOSession session) throws IOException {
            return session;
        }

    }

    /**
     * @since 4.3
     */
    public BasicNIOConnPool(
            final ConnectionInitiator connectionInitiator,
            final int connectTimeout) {
        super(connectionInitiator, new BasicNIOConnFactory(), new BasicAddressResolver(), DEFAULT_MAX_CONNECTIONS_PER_ROUTE, DEFAULT_MAX_TOTAL_CONNECTIONS);
        this.connectTimeout = connectTimeout;
    }

    /**
     * @since 4.3
     */
    public BasicNIOConnPool(final ConnectionInitiator connectionInitiator) {
        this(connectionInitiator, 0);
    }

    @Override
    protected BasicNIOPoolEntry createEntry(final HttpHost host, final IOSession conn) {
        final BasicNIOPoolEntry entry = new BasicNIOPoolEntry(
                Long.toString(COUNTER.getAndIncrement()), host, conn);
        entry.setSocketTimeout(conn.getSocketTimeout());
        return entry;
    }

    @Override
    public Future<BasicNIOPoolEntry> lease(
            final HttpHost route,
            final Object state,
            final FutureCallback<BasicNIOPoolEntry> callback) {
        return super.lease(route, state,
                this.connectTimeout, TimeUnit.MILLISECONDS, callback);
    }

    @Override
    public Future<BasicNIOPoolEntry> lease(
            final HttpHost route,
            final Object state) {
        return super.lease(route, state,
                this.connectTimeout, TimeUnit.MILLISECONDS, null);
    }

    @Override
    protected void onLease(final BasicNIOPoolEntry entry) {
        final IOSession conn = entry.getConnection();
        conn.setSocketTimeout(entry.getSocketTimeout());
    }

    @Override
    public void release(final BasicNIOPoolEntry entry, final boolean reusable) {
        super.release(entry, reusable && !entry.isClosed());
    }

    @Override
    protected void onRelease(final BasicNIOPoolEntry entry) {
        final IOSession conn = entry.getConnection();
        entry.setSocketTimeout(conn.getSocketTimeout());
        conn.setSocketTimeout(0);
    }

}
