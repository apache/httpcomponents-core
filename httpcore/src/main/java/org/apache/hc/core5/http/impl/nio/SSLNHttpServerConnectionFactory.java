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
package org.apache.hc.core5.http.impl.nio;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.annotation.Immutable;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.impl.ConnSupport;
import org.apache.hc.core5.http.nio.NHttpConnectionFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLIOSession;
import org.apache.hc.core5.reactor.ssl.SSLMode;
import org.apache.hc.core5.reactor.ssl.SSLSetupHandler;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.ByteBufferAllocator;

/**
 * Default factory for SSL encrypted, non-blocking
 * {@link org.apache.hc.core5.http.nio.NHttpServerConnection}s.
 *
 * @since 4.2
 */
@Immutable
public class SSLNHttpServerConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpServerConnection> {

    private final SSLContext sslcontext;
    private final SSLSetupHandler sslHandler;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory;
    private final ByteBufferAllocator allocator;
    private final ConnectionConfig cconfig;

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final NHttpMessageParserFactory<HttpRequest> requestParserFactory,
            final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory,
            final ByteBufferAllocator allocator,
            final ConnectionConfig cconfig) {
        super();
        this.sslcontext = sslcontext != null ? sslcontext : SSLContexts.createSystemDefault();
        this.sslHandler = sslHandler;
        this.incomingContentStrategy = incomingContentStrategy;
        this.outgoingContentStrategy = outgoingContentStrategy;
        this.requestParserFactory = requestParserFactory;
        this.responseWriterFactory = responseWriterFactory;
        this.allocator = allocator;
        this.cconfig = cconfig != null ? cconfig : ConnectionConfig.DEFAULT;
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final NHttpMessageParserFactory<HttpRequest> requestParserFactory,
            final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory,
            final ByteBufferAllocator allocator,
            final ConnectionConfig cconfig) {
        this(sslcontext, sslHandler,
                null, null, requestParserFactory, responseWriterFactory, allocator, cconfig);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final NHttpMessageParserFactory<HttpRequest> requestParserFactory,
            final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory,
            final ConnectionConfig cconfig) {
        this(sslcontext, sslHandler,
                null, null, requestParserFactory, responseWriterFactory, null, cconfig);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(sslcontext, sslHandler, null, null, null, null, null, config);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(final ConnectionConfig config) {
        this(null, null, null, null, null, null, null, config);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory() {
        this(null, null, null, null, null, null, null, null);
    }

    /**
     * @since 4.3
     */
    protected SSLIOSession createSSLIOSession(
            final IOSession iosession,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler) {
        final SSLIOSession ssliosession = new SSLIOSession(iosession, SSLMode.SERVER,
                sslcontext, sslHandler);
        return ssliosession;
    }

    @Override
    public DefaultNHttpServerConnection createConnection(final IOSession iosession) {
        final SSLIOSession ssliosession = createSSLIOSession(iosession, this.sslcontext, this.sslHandler);
        iosession.setAttribute(SSLIOSession.SESSION_KEY, ssliosession);
        return new DefaultNHttpServerConnection(ssliosession,
                this.cconfig.getBufferSize(),
                this.cconfig.getFragmentSizeHint(),
                this.allocator,
                ConnSupport.createDecoder(this.cconfig),
                ConnSupport.createEncoder(this.cconfig),
                this.cconfig.getMessageConstraints(),
                this.incomingContentStrategy,
                this.outgoingContentStrategy,
                this.requestParserFactory,
                this.responseWriterFactory);
    }

}
