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
package org.apache.http.impl.nio;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestParserFactory;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLIOSession;
import org.apache.http.nio.reactor.ssl.SSLMode;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.Config;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default factory for SSL encrypted, non-blocking {@link NHttpServerConnection}s.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable
public class SSLNHttpServerConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpServerConnection> {

    private final NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private final HttpRequestFactory requestFactory;
    private final ByteBufferAllocator allocator;
    private final SSLContext sslcontext;
    private final SSLSetupHandler sslHandler;
    private final HttpParams params;

    /**
     * @deprecated (4.3) use {@link
     *   SSLNHttpServerConnectionFactory#SSLNHttpServerConnectionFactory(SSLContext,
     *     SSLSetupHandler, HttpRequestFactory, ByteBufferAllocator)}
     */
    @Deprecated
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpRequestFactory requestFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super();
        Args.notNull(requestFactory, "HTTP request factory");
        Args.notNull(allocator, "Byte buffer allocator");
        Args.notNull(params, "HTTP parameters");
        this.sslcontext = sslcontext;
        this.sslHandler = sslHandler;
        this.requestFactory = requestFactory;
        this.allocator = allocator;
        this.params = params;
        this.requestParserFactory = null;
    }

    /**
     * @deprecated (4.3) use {@link
     *   SSLNHttpServerConnectionFactory#SSLNHttpServerConnectionFactory(SSLContext,
     *     SSLSetupHandler)}
     */
    @Deprecated
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpParams params) {
        this(sslcontext, sslHandler, DefaultHttpRequestFactory.INSTANCE,
                HeapByteBufferAllocator.INSTANCE, params);
    }

    /**
     * @deprecated (4.3) use {@link
     *   SSLNHttpServerConnectionFactory#SSLNHttpServerConnectionFactory()}
     */
    @Deprecated
    public SSLNHttpServerConnectionFactory(final HttpParams params) {
        this(null, null, params);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final HttpRequestFactory requestFactory,
            final ByteBufferAllocator allocator) {
        super();
        this.sslcontext = sslcontext;
        this.sslHandler = sslHandler;
        this.requestFactory = requestFactory;
        this.allocator = allocator;
        this.requestParserFactory = new DefaultHttpRequestParserFactory(null, requestFactory);
        this.params = null;
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler) {
        this(sslcontext, sslHandler, null, null);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory() {
        this(null, null, null, null);
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

    /**
     * @deprecated (4.3) no longer used.
     */
    @Deprecated
    protected DefaultNHttpServerConnection createConnection(
            final IOSession session,
            final HttpRequestFactory requestFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        return new DefaultNHttpServerConnection(session, requestFactory, allocator, params);
    }

    /**
     * @since 4.3
     */
    protected SSLIOSession createSSLIOSession(
            final IOSession iosession,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler) {
        SSLIOSession ssliosession = new SSLIOSession(iosession, SSLMode.SERVER,
                (sslcontext != null ? sslcontext : getDefaultSSLContext()),
                sslHandler);
        iosession.setAttribute(SSLIOSession.SESSION_KEY, ssliosession);
        return ssliosession;
    }

    public DefaultNHttpServerConnection createConnection(final IOSession iosession) {
        SSLIOSession ssliosession = createSSLIOSession(iosession, this.sslcontext, this.sslHandler);
        if (this.params != null) {
            DefaultNHttpServerConnection conn = createConnection(
                    ssliosession, this.requestFactory, this.allocator, this.params);
            int timeout = Config.getInt(this.params, CoreConnectionPNames.SO_TIMEOUT, 0);
            conn.setSocketTimeout(timeout);
            return conn;
        } else {
            return new DefaultNHttpServerConnection(ssliosession, 8 * 1024,
                    this.allocator,
                    null, null, null, null, null,
                    this.requestParserFactory,
                    null);
        }
    }

}
