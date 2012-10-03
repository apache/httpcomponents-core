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
package org.apache.http.impl.nio.pool;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.HttpHost;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.pool.AbstractNIOConnPool;
import org.apache.http.nio.pool.NIOConnFactory;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.Config;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.ConnPool;
import org.apache.http.util.Args;

/**
 * A very basic {@link ConnPool} implementation that represents a pool
 * of non-blocking {@link NHttpClientConnection} connections identified by
 * an {@link HttpHost} instance. Please note this pool implementation
 * does not support complex routes via a proxy cannot differentiate between
 * direct and proxied connections.
 *
 * @see HttpHost
 * @since 4.2
 */
@ThreadSafe
public class BasicNIOConnPool extends AbstractNIOConnPool<HttpHost, NHttpClientConnection, BasicNIOPoolEntry> {

    private static AtomicLong COUNTER = new AtomicLong();

    private final int connectTimeout;
    private final TimeUnit tunit;

    /**
     * @deprecated (4.3) use {@link BasicNIOConnPool#BasicNIOConnPool(ConnectingIOReactor,
     *   NIOConnFactory, int, TimeUnit)}
     */
    @Deprecated
    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor,
            final NIOConnFactory<HttpHost, NHttpClientConnection> connFactory,
            final HttpParams params) {
        super(ioreactor, connFactory, 2, 20);
        Args.notNull(params, "HTTP parameters");
        this.connectTimeout = Config.getInt(params, CoreConnectionPNames.CONNECTION_TIMEOUT, 0);
        this.tunit = TimeUnit.MILLISECONDS;
    }

    /**
     * @deprecated (4.3) use {@link BasicNIOConnPool#BasicNIOConnPool(ConnectingIOReactor,
     *   int, TimeUnit)}
     */
    @Deprecated
    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor, final HttpParams params) {
        this(ioreactor, new BasicNIOConnFactory(params), params);
    }

    /**
     * @since 4.3
     */
    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor,
            final NIOConnFactory<HttpHost, NHttpClientConnection> connFactory,
            int connectTimeout,
            final TimeUnit tunit) {
        super(ioreactor, connFactory, 2, 20);
        this.connectTimeout = connectTimeout;
        this.tunit = tunit != null ? tunit : TimeUnit.MILLISECONDS;
    }

    /**
     * @since 4.3
     */
    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor,
            final NIOConnFactory<HttpHost, NHttpClientConnection> connFactory) {
        this(ioreactor, connFactory, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * @since 4.3
     */
    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor, int connectTimeout, final TimeUnit tunit) {
        this(ioreactor, new BasicNIOConnFactory(), connectTimeout, tunit);
    }

    /**
     * @since 4.3
     */
    public BasicNIOConnPool(final ConnectingIOReactor ioreactor) {
        this(ioreactor, new BasicNIOConnFactory(), 0, TimeUnit.MILLISECONDS);
    }

    @Override
    protected SocketAddress resolveRemoteAddress(final HttpHost host) {
        return new InetSocketAddress(host.getHostName(), host.getPort());
    }

    @Override
    protected SocketAddress resolveLocalAddress(final HttpHost host) {
        return null;
    }

    @Override
    protected BasicNIOPoolEntry createEntry(final HttpHost host, final NHttpClientConnection conn) {
        return new BasicNIOPoolEntry(Long.toString(COUNTER.getAndIncrement()), host, conn);
    }

    @Override
    public Future<BasicNIOPoolEntry> lease(
            final HttpHost route,
            final Object state,
            final FutureCallback<BasicNIOPoolEntry> callback) {
        return super.lease(route, state, this.connectTimeout, this.tunit, callback);
    }

    @Override
    public Future<BasicNIOPoolEntry> lease(
            final HttpHost route,
            final Object state) {
        return super.lease(route, state, this.connectTimeout, this.tunit, null);
    }

}
