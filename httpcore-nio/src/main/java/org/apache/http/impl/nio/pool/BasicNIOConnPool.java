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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponseFactory;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.reactor.SSLIOSession;
import org.apache.http.impl.nio.reactor.SSLMode;
import org.apache.http.impl.nio.reactor.SSLSetupHandler;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.pool.AbstractNIOConnPool;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;

/**
 * Basic non-blocking {@link IOSession} pool.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_TIMEOUT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#CONNECTION_TIMEOUT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 * </ul>
 *
 * @since 4.2
 */
@ThreadSafe
public class BasicNIOConnPool extends AbstractNIOConnPool<HttpHost, NHttpClientConnection, BasicNIOPoolEntry> {

    private static AtomicLong COUNTER = new AtomicLong();

    private final HttpResponseFactory responseFactory;
    private final ByteBufferAllocator allocator;
    private final SSLContext sslcontext;
    private final SSLSetupHandler sslHandler;
    private final HttpParams params;

    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpResponseFactory responseFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(ioreactor, 2, 20);
        if (responseFactory == null) {
            throw new IllegalArgumentException("HTTP response factory may not be null");
        }
        if (allocator == null) {
            throw new IllegalArgumentException("Byte buffer allocator may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.sslcontext = sslcontext;
        this.sslHandler = sslHandler;
        this.responseFactory = responseFactory;
        this.allocator = allocator;
        this.params = params;
    }

    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(ioreactor, sslcontext, sslHandler,
                new DefaultHttpResponseFactory(), new HeapByteBufferAllocator(), params);
    }

    public BasicNIOConnPool(
            final ConnectingIOReactor ioreactor,
            final HttpParams params) {
        this(ioreactor, null, null, params);
    }

    @Override
    protected SocketAddress resolveRemoteAddress(final HttpHost host) {
        return new InetSocketAddress(host.getHostName(), host.getPort());
    }

    @Override
    protected SocketAddress resolveLocalAddress(final HttpHost host) {
        return null;
    }

    private SSLContext getDefaultSSLContext() {
        SSLContext sslcontext;
        try {
            sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, null, null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failure initializing default SSL context", ex);
        }
        return sslcontext;
    }

    @Override
    protected NHttpClientConnection createConnection(
            final HttpHost route, final IOSession session) throws IOException {
        IOSession connSession;
        if (route.getSchemeName().equalsIgnoreCase("https")) {
            SSLContext connSSLContext = this.sslcontext != null ? this.sslcontext : getDefaultSSLContext();
            SSLIOSession ssliosession = new SSLIOSession(session, connSSLContext, this.sslHandler);
            ssliosession.bind(SSLMode.CLIENT, this.params);
            session.setAttribute(IOSession.SSL_SESSION_KEY, ssliosession);
            connSession = ssliosession;
        } else {
            connSession = session;
        }

        NHttpClientConnection conn = new DefaultNHttpClientConnection(
                connSession, this.responseFactory, this.allocator, this.params);

        session.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        int timeout = HttpConnectionParams.getSoTimeout(this.params);
        conn.setSocketTimeout(timeout);

        return conn;
    }

    @Override
    protected BasicNIOPoolEntry createEntry(final HttpHost host, final NHttpClientConnection conn) {
        return new BasicNIOPoolEntry(Long.toString(COUNTER.getAndIncrement()), host, conn);
    }

    @Override
    protected void closeEntry(final BasicNIOPoolEntry entry) {
        NHttpClientConnection conn = entry.getConnection();
        try {
            conn.shutdown();
        } catch (IOException ex) {
        }
    }

    @Override
    public Future<BasicNIOPoolEntry> lease(
            final HttpHost route,
            final Object state,
            final FutureCallback<BasicNIOPoolEntry> callback) {
        int connectTimeout = HttpConnectionParams.getConnectionTimeout(this.params);
        return super.lease(route, state, connectTimeout, TimeUnit.MILLISECONDS, callback);
    }

    @Override
    public Future<BasicNIOPoolEntry> lease(final HttpHost route, final Object state) {
        int connectTimeout = HttpConnectionParams.getConnectionTimeout(this.params);
        return super.lease(route, state, connectTimeout, TimeUnit.MILLISECONDS, null);
    }

}
