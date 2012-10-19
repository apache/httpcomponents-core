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

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestParserFactory;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParamConfig;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default factory for plain (non-encrypted), non-blocking {@link NHttpServerConnection}s.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable
public class DefaultNHttpServerConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpServerConnection> {

    private final NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private final ByteBufferAllocator allocator;
    private final ConnectionConfig config;

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpServerConnectionFactory#DefaultNHttpServerConnectionFactory(
     *     ByteBufferAllocator, HttpRequestFactory, ConnectionConfig)}
     */
    @Deprecated
    public DefaultNHttpServerConnectionFactory(
            final HttpRequestFactory requestFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super();
        Args.notNull(requestFactory, "HTTP request factory");
        Args.notNull(allocator, "Byte buffer allocator");
        Args.notNull(params, "HTTP parameters");
        this.requestParserFactory = new DefaultHttpRequestParserFactory(null, requestFactory);
        this.allocator = allocator;
        this.config = HttpParamConfig.getConnectionConfig(params);
    }

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpServerConnectionFactory#DefaultNHttpServerConnectionFactory(ConnectionConfig)}
     */
    @Deprecated
    public DefaultNHttpServerConnectionFactory(final HttpParams params) {
        this(DefaultHttpRequestFactory.INSTANCE, HeapByteBufferAllocator.INSTANCE, params);
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
    public DefaultNHttpServerConnectionFactory(
            final ByteBufferAllocator allocator,
            final HttpRequestFactory requestFactory,
            final ConnectionConfig config) {
        super();
        this.allocator = allocator != null ? allocator : HeapByteBufferAllocator.INSTANCE;
        this.requestParserFactory = new DefaultHttpRequestParserFactory(null, requestFactory);
        this.config = config != null ? config : ConnectionConfig.DEFAULT;
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpServerConnectionFactory(final ConnectionConfig config) {
        this(null, null, config);
    }

    public DefaultNHttpServerConnection createConnection(final IOSession session) {
        CharsetDecoder chardecoder = null;
        CharsetEncoder charencoder = null;
        Charset charset = this.config.getCharset();
        CodingErrorAction malformedInputAction = this.config.getMalformedInputAction() != null ?
                this.config.getMalformedInputAction() : CodingErrorAction.REPORT;
        CodingErrorAction unmappableInputAction = this.config.getUnmappableInputAction() != null ?
                this.config.getUnmappableInputAction() : CodingErrorAction.REPORT;
        if (charset != null) {
            chardecoder = charset.newDecoder();
            chardecoder.onMalformedInput(malformedInputAction);
            chardecoder.onUnmappableCharacter(unmappableInputAction);
            charencoder = charset.newEncoder();
            charencoder.onMalformedInput(malformedInputAction);
            charencoder.onUnmappableCharacter(unmappableInputAction);
        }
        return new DefaultNHttpServerConnection(session, 8 * 1024,
                this.allocator,
                chardecoder, charencoder, this.config.getMessageConstraints(),
                null, null,
                this.requestParserFactory,
                null);
    }

}
