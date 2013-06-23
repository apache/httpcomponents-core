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
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.ConnSupport;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParserFactory;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParamConfig;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default factory for plain (non-encrypted), non-blocking
 * {@link org.apache.http.nio.NHttpClientConnection}s.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable
public class DefaultNHttpClientConnectionFactory
    implements NHttpConnectionFactory<DefaultNHttpClientConnection> {

    private final NHttpMessageParserFactory<HttpResponse> responseParserFactory;
    private final ByteBufferAllocator allocator;
    private final ConnectionConfig cconfig;

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpClientConnectionFactory#DefaultNHttpClientConnectionFactory(
     *      NHttpMessageParserFactory, ByteBufferAllocator, ConnectionConfig)}
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
        this.allocator = allocator;
        this.responseParserFactory = new DefaultHttpResponseParserFactory(null, responseFactory);
        this.cconfig = HttpParamConfig.getConnectionConfig(params);
    }

    /**
     * @deprecated (4.3) use {@link
     *   DefaultNHttpClientConnectionFactory#DefaultNHttpClientConnectionFactory(
     *     ConnectionConfig)}
     */
    @Deprecated
    public DefaultNHttpClientConnectionFactory(final HttpParams params) {
        this(DefaultHttpResponseFactory.INSTANCE, HeapByteBufferAllocator.INSTANCE, params);
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpClientConnectionFactory(
            final NHttpMessageParserFactory<HttpResponse> responseParserFactory,
            final ByteBufferAllocator allocator,
            final ConnectionConfig cconfig) {
        super();
        this.allocator = allocator != null ? allocator : HeapByteBufferAllocator.INSTANCE;
        this.responseParserFactory = responseParserFactory != null ? responseParserFactory :
            DefaultHttpResponseParserFactory.INSTANCE;
        this.cconfig = cconfig != null ? cconfig : ConnectionConfig.DEFAULT;
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpClientConnectionFactory(final ConnectionConfig cconfig) {
        this(null, null, cconfig);
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
        return new DefaultNHttpClientConnection(
                session,
                this.cconfig.getBufferSize(),
                this.cconfig.getFragmentSizeHint(),
                this.allocator,
                ConnSupport.createDecoder(this.cconfig),
                ConnSupport.createEncoder(this.cconfig),
                this.cconfig.getMessageConstraints(),
                null, null, null,
                this.responseParserFactory);
    }

}
