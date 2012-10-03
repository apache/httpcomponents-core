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

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponseFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestParserFactory;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.Config;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default factory for plain (non-encrypted), non-blocking {@link NHttpServerConnection}s.
 *
 * @since 4.2
 */
@Immutable
public class DefaultNHttpServerConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpServerConnection> {

    private final NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private final HttpRequestFactory requestFactory;
    private final ByteBufferAllocator allocator;
    private final HttpParams params;

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpClientConnectionFactory#DefaultNHttpClientConnectionFactory(
     *     ByteBufferAllocator, HttpResponseFactory)}
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
        this.requestFactory = requestFactory;
        this.allocator = allocator;
        this.params = params;
        this.requestParserFactory = null;
    }

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpClientConnectionFactory#DefaultNHttpClientConnectionFactory(
     *     ByteBufferAllocator, HttpResponseFactory)}
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
            final HttpRequestFactory requestFactory) {
        super();
        this.requestFactory = requestFactory;
        this.allocator = allocator;
        this.requestParserFactory = new DefaultHttpRequestParserFactory(null, requestFactory);
        this.params = null;
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpServerConnectionFactory() {
        this(null, null);
    }

    public DefaultNHttpServerConnection createConnection(final IOSession session) {
        if (this.params != null) {
            DefaultNHttpServerConnection conn = createConnection(
                    session, this.requestFactory, this.allocator, this.params);
            int timeout = Config.getInt(this.params, CoreConnectionPNames.SO_TIMEOUT, 0);
            conn.setSocketTimeout(timeout);
            return conn;
        } else {
            return new DefaultNHttpServerConnection(session, 8 * 1024,
                    this.allocator,
                    null, null, null, null, null,
                    this.requestParserFactory,
                    null);
        }
    }

}
