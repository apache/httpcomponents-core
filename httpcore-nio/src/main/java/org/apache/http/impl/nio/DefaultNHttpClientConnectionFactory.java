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

import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParserFactory;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.Config;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default factory for plain (non-encrypted), non-blocking {@link NHttpClientConnection}s.
 *
 * @since 4.2
 */
@Immutable
public class DefaultNHttpClientConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpClientConnection> {

    private final NHttpMessageParserFactory<HttpResponse> responseParserFactory;
    private final HttpResponseFactory responseFactory;
    private final ByteBufferAllocator allocator;
    private final HttpParams params;

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpClientConnectionFactory#DefaultNHttpClientConnectionFactory(
     *     ByteBufferAllocator, HttpResponseFactory)}
     */
    @Deprecated
    public DefaultNHttpClientConnectionFactory(
            final HttpResponseFactory responseFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super();
        Args.notNull(responseFactory, "HTTP response factory");
        Args.notNull(allocator, "Byte buffer allocator");
        Args.notNull(params, "HTTP parameters");
        this.responseFactory = responseFactory;
        this.allocator = allocator;
        this.params = params;
        this.responseParserFactory = null;
    }

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpClientConnectionFactory#DefaultNHttpClientConnectionFactory()}
     */
    @Deprecated
    public DefaultNHttpClientConnectionFactory(final HttpParams params) {
        this(DefaultHttpResponseFactory.INSTANCE, HeapByteBufferAllocator.INSTANCE, params);
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpClientConnectionFactory(
            final ByteBufferAllocator allocator,
            final HttpResponseFactory responseFactory) {
        super();
        this.responseFactory = responseFactory;
        this.allocator = allocator;
        this.responseParserFactory = new DefaultHttpResponseParserFactory(null, responseFactory);
        this.params = null;
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpClientConnectionFactory() {
        this(null, null);
    }

    /**
     * @deprecated (4.3) no longer used.
     */
    @Deprecated
    protected DefaultNHttpClientConnection createConnection(
            final IOSession session,
            final HttpResponseFactory responseFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        return new DefaultNHttpClientConnection(session, responseFactory, allocator, params);
    }

    public DefaultNHttpClientConnection createConnection(final IOSession session) {
        if (this.params != null) {
            DefaultNHttpClientConnection conn = createConnection(session, this.responseFactory,
                    this.allocator, this.params);
            int timeout = Config.getInt(this.params, CoreConnectionPNames.SO_TIMEOUT, 0);
            conn.setSocketTimeout(timeout);
            return conn;
        } else {
            return new DefaultNHttpClientConnection(
                    session, 8 * 1024,
                    this.allocator,
                    null, null, null, null, null, null,
                    this.responseParserFactory);
        }
    }

}
