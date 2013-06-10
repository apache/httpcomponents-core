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

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestParserFactory;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLIOSession;
import org.apache.http.nio.reactor.ssl.SSLMode;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParamConfig;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default factory for SSL encrypted, non-blocking
 * {@link org.apache.http.nio.NHttpServerConnection}s.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable
public class SSLNHttpServerConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpServerConnection> {

    private final NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private final ByteBufferAllocator allocator;
    private final SSLContext sslcontext;
    private final SSLSetupHandler sslHandler;
    private final ConnectionConfig config;

    /**
     * @deprecated (4.3) use {@link
     *   SSLNHttpServerConnectionFactory#SSLNHttpServerConnectionFactory(SSLContext,
     *      SSLSetupHandler, NHttpMessageParserFactory, ByteBufferAllocator, ConnectionConfig)}
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
        this.allocator = allocator;
        this.requestParserFactory = new DefaultHttpRequestParserFactory(null, requestFactory);
        this.config = HttpParamConfig.getConnectionConfig(params);
    }

    /**
     * @deprecated (4.3) use {@link
     *   SSLNHttpServerConnectionFactory#SSLNHttpServerConnectionFactory(SSLContext,
     *     SSLSetupHandler, ConnectionConfig)}
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
     *   SSLNHttpServerConnectionFactory#SSLNHttpServerConnectionFactory(ConnectionConfig)}
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
            final NHttpMessageParserFactory<HttpRequest> requestParserFactory,
            final ByteBufferAllocator allocator,
            final ConnectionConfig config) {
        super();
        this.sslcontext = sslcontext;
        this.sslHandler = sslHandler;
        this.allocator = allocator != null ? allocator : HeapByteBufferAllocator.INSTANCE;
        this.requestParserFactory = requestParserFactory != null ? requestParserFactory :
            DefaultHttpRequestParserFactory.INSTANCE;
        this.config = config != null ? config : ConnectionConfig.DEFAULT;
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(
            final SSLContext sslcontext,
            final SSLSetupHandler sslHandler,
            final ConnectionConfig config) {
        this(sslcontext, sslHandler, null, null, config);
    }

    /**
     * @since 4.3
     */
    public SSLNHttpServerConnectionFactory(final ConnectionConfig config) {
        this(null, null, null, null, config);
    }

    private SSLContext getDefaultSSLContext() {
        final SSLContext sslcontext;
        try {
            sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, null, null);
        } catch (final Exception ex) {
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
        final SSLIOSession ssliosession = new SSLIOSession(iosession, SSLMode.SERVER,
                (sslcontext != null ? sslcontext : getDefaultSSLContext()),
                sslHandler);
        iosession.setAttribute(SSLIOSession.SESSION_KEY, ssliosession);
        return ssliosession;
    }

    public DefaultNHttpServerConnection createConnection(final IOSession iosession) {
        final SSLIOSession ssliosession = createSSLIOSession(iosession, this.sslcontext, this.sslHandler);
        CharsetDecoder chardecoder = null;
        CharsetEncoder charencoder = null;
        final Charset charset = this.config.getCharset();
        final CodingErrorAction malformedInputAction = this.config.getMalformedInputAction() != null ?
                this.config.getMalformedInputAction() : CodingErrorAction.REPORT;
        final CodingErrorAction unmappableInputAction = this.config.getUnmappableInputAction() != null ?
                this.config.getUnmappableInputAction() : CodingErrorAction.REPORT;
        if (charset != null) {
            chardecoder = charset.newDecoder();
            chardecoder.onMalformedInput(malformedInputAction);
            chardecoder.onUnmappableCharacter(unmappableInputAction);
            charencoder = charset.newEncoder();
            charencoder.onMalformedInput(malformedInputAction);
            charencoder.onUnmappableCharacter(unmappableInputAction);
        }
        return new DefaultNHttpServerConnection(ssliosession,
                this.config.getBufferSize(),
                this.config.getFragmentSizeHint(),
                this.allocator,
                chardecoder, charencoder, this.config.getMessageConstraints(),
                null, null,
                this.requestParserFactory,
                null);
    }

}
