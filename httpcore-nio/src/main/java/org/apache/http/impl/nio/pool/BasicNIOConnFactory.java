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

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponseFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnectionFactory;
import org.apache.http.impl.nio.SSLNHttpClientConnectionFactory;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.pool.NIOConnFactory;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;

/**
 * A basic {@link NIOConnFactory} implementation that creates
 * {@link NHttpClientConnection} instances given a {@link HttpHost} instance.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 * </ul>
 *
 * @since 4.2
 */
@Immutable
public class BasicNIOConnFactory implements NIOConnFactory<HttpHost, NHttpClientConnection> {

    private final NHttpConnectionFactory<? extends NHttpClientConnection> plainFactory;
    private final NHttpConnectionFactory<? extends NHttpClientConnection> sslFactory;

    public BasicNIOConnFactory(
            final NHttpConnectionFactory<? extends NHttpClientConnection> plainFactory,
            final NHttpConnectionFactory<? extends NHttpClientConnection> sslFactory) {
        super();
        if (plainFactory == null) {
            throw new IllegalArgumentException("Plain HTTP client connection factory may not be null");
        }
        this.plainFactory = plainFactory;
        this.sslFactory = sslFactory;
    }

    public BasicNIOConnFactory(
            final NHttpConnectionFactory<? extends NHttpClientConnection> plainFactory) {
        this(plainFactory, null);
    }

    public BasicNIOConnFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpResponseFactory responseFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        this(new DefaultNHttpClientConnectionFactory(
                responseFactory, allocator, params),
                new SSLNHttpClientConnectionFactory(
                        sslcontext, sslHandler, responseFactory, allocator, params));
    }

    public BasicNIOConnFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(sslcontext, sslHandler,
                new DefaultHttpResponseFactory(), new HeapByteBufferAllocator(), params);
    }

    public BasicNIOConnFactory(final HttpParams params) {
        this(null, null, params);
    }

    public NHttpClientConnection create(final HttpHost route, final IOSession session) throws IOException {
        NHttpClientConnection conn;
        if (route.getSchemeName().equalsIgnoreCase("https")) {
            if (this.sslFactory == null) {
                throw new IOException("SSL not supported");
            }
            conn = this.sslFactory.createConnection(session);
        } else {
            conn = this.plainFactory.createConnection(session);
        }
        session.setAttribute(IOEventDispatch.CONNECTION_KEY, conn);
        return conn;
    }

}
