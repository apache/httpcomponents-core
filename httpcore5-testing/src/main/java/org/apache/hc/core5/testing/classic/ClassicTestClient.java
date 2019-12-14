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

package org.apache.hc.core5.testing.classic;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.TimeValue;

public class ClassicTestClient {

    private final SSLContext sslContext;
    private final SocketConfig socketConfig;

    private final AtomicReference<HttpRequester> requesterRef;

    public ClassicTestClient(final SSLContext sslContext, final SocketConfig socketConfig) {
        super();
        this.sslContext = sslContext;
        this.socketConfig = socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
        this.requesterRef = new AtomicReference<>(null);
    }

    public ClassicTestClient(final SocketConfig socketConfig) {
        this(null, socketConfig);
    }

    public ClassicTestClient() {
        this(null, null);
    }

    public void start() {
        start(null);
    }

    public void start(final HttpProcessor httpProcessor) {
        if (requesterRef.get() == null) {
            final HttpRequestExecutor requestExecutor = new HttpRequestExecutor(
                    HttpRequestExecutor.DEFAULT_WAIT_FOR_CONTINUE,
                    DefaultConnectionReuseStrategy.INSTANCE,
                    LoggingHttp1StreamListener.INSTANCE);
            final StrictConnPool<HttpHost, HttpClientConnection> connPool = new StrictConnPool<>(
                    20,
                    50,
                    TimeValue.NEG_ONE_MILLISECOND,
                    PoolReusePolicy.LIFO,
                    LoggingConnPoolListener.INSTANCE);
            final HttpRequester requester = new HttpRequester(
                    requestExecutor,
                    httpProcessor != null ? httpProcessor : HttpProcessors.client(),
                    connPool,
                    socketConfig,
                    new LoggingBHttpClientConnectionFactory(Http1Config.DEFAULT, CharCodingConfig.DEFAULT),
                    sslContext != null ? sslContext.getSocketFactory() : null,
                    null,
                    null,
                    DefaultAddressResolver.INSTANCE);
            requesterRef.compareAndSet(null, requester);
        } else {
            throw new IllegalStateException("Requester has already been started");
        }
    }

    public void shutdown(final CloseMode closeMode) {
        final HttpRequester requester = requesterRef.getAndSet(null);
        if (requester != null) {
            requester.close(closeMode);
        }
    }

    public ClassicHttpResponse execute(
            final HttpHost targetHost,
            final ClassicHttpRequest request,
            final HttpContext context) throws HttpException, IOException {
        final HttpRequester requester = this.requesterRef.get();
        if (requester == null) {
            throw new IllegalStateException("Requester has not been started");
        }
        if (request.getAuthority() == null) {
            request.setAuthority(new URIAuthority(targetHost));
        }
        request.setScheme(targetHost.getSchemeName());
        return requester.execute(targetHost, request, socketConfig.getSoTimeout(), context);
    }

}
