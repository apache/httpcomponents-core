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
 * {@link org.apache.hc.core5.http.nio.NHttpClientConnection}s.
 *
 * @since 4.2
 */
@Immutable
public class SSLNHttpClientConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpClientConnection> {

    public static final SSLNHttpClientConnectionFactory INSTANCE = new SSLNHttpClientConnectionFactory();

    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final NHttpMessageParserFactory<HttpResponse> responseParserFactory;
    private final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory;
    private final ByteBufferAllocator allocator;
    private final SSLContext sslcontext;
    private final SSLSetupHandler sslHandler;
    private final ConnectionConfig cconfig;

    /**
     * @since 4.3
     */
    public SSLNHttpClientConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final NHttpMessageParserFactory<HttpResponse> responseParserFactory,
            final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final ByteBufferAllocator allocator,
            final ConnectionConfig cconfig) {
        super();
        this.sslcontext = sslcontext != null ? sslcontext : SSLContexts.createSystemDefault();
        this.sslHandler = sslHandler;
        this.incomingContentStrategy = incomingContentStrategy;
        this.outgoingContentStrategy = outgoingContentStrategy;
        this.responseParserFactory = responseParserFactory;
        this.requestWriterFactory = requestWriterFactory;
        this.allocator = allocator;
        this.cconfig = cconfig != null ? cconfig : ConnectionConfig.DEFAULT;
    }

    /**
     * @since 4.3
     */
    public SSLNHttpClientConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final NHttpMessageParserFactory<HttpResponse> responseParserFactory,
            final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final ByteBufferAllocator allocator,
            final ConnectionConfig cconfig) {
        this(sslcontext, sslHandler,
                null, null, responseParserFactory, requestWriterFactory, allocator, cconfig);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpClientConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final NHttpMessageParserFactory<HttpResponse> responseParserFactory,
            final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final ConnectionConfig cconfig) {
        this(sslcontext, sslHandler,
                null, null, responseParserFactory, requestWriterFactory, null, cconfig);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpClientConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(sslcontext, sslHandler, null, null, null, null, null, config);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpClientConnectionFactory(final ConnectionConfig config) {
        this(null, null, null, null, null, null, null, config);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpClientConnectionFactory() {
        this(null, null, null, null, null, null);
    }

    /**
     * @since 4.3
     */
    protected SSLIOSession createSSLIOSession(
            final IOSession iosession,
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler) {
        final SSLIOSession ssliosession = new SSLIOSession(iosession, SSLMode.CLIENT,
                sslcontext, sslHandler);
        return ssliosession;
    }

    @Override
    public DefaultNHttpClientConnection createConnection(final IOSession iosession) {
        final SSLIOSession ssliosession = createSSLIOSession(iosession, this.sslcontext, this.sslHandler);
        iosession.setAttribute(SSLIOSession.SESSION_KEY, ssliosession);
        return new DefaultNHttpClientConnection(
                ssliosession,
                this.cconfig.getBufferSize(),
                this.cconfig.getFragmentSizeHint(),
                this.allocator,
                ConnSupport.createDecoder(this.cconfig),
                ConnSupport.createEncoder(this.cconfig),
                this.cconfig.getMessageConstraints(),
                this.incomingContentStrategy,
                this.outgoingContentStrategy,
                this.requestWriterFactory,
                this.responseParserFactory);
    }

}
