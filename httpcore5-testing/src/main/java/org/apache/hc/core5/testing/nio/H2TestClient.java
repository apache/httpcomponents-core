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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.nio.support.DefaultAsyncPushConsumerFactory;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

public class H2TestClient extends AsyncRequester {

    private final SSLContext sslContext;
    private final SSLSessionInitializer sslSessionInitializer;
    private final SSLSessionVerifier sslSessionVerifier;
    private final RequestHandlerRegistry<Supplier<AsyncPushConsumer>> registry;

    public H2TestClient(
            final IOReactorConfig ioReactorConfig,
            final SSLContext sslContext,
            final SSLSessionInitializer sslSessionInitializer,
            final SSLSessionVerifier sslSessionVerifier) throws IOException {
        super(ioReactorConfig);
        this.sslContext = sslContext;
        this.sslSessionInitializer = sslSessionInitializer;
        this.sslSessionVerifier = sslSessionVerifier;
        this.registry = new RequestHandlerRegistry<>();
    }

    public H2TestClient() throws IOException {
        this(IOReactorConfig.DEFAULT, null, null, null);
    }

    public void register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        registry.register(null, uriPattern, supplier);
    }

    public void start(final IOEventHandlerFactory handlerFactory) throws IOException {
        super.execute(handlerFactory);
    }

    public void start(final HttpProcessor httpProcessor, final H2Config h2Config) throws IOException {
        start(new InternalClientH2EventHandlerFactory(
                httpProcessor,
                new DefaultAsyncPushConsumerFactory(registry),
                HttpVersionPolicy.FORCE_HTTP_2,
                h2Config,
                Http1Config.DEFAULT,
                CharCodingConfig.DEFAULT,
                sslContext,
                sslSessionInitializer,
                sslSessionVerifier));
    }

    public void start(final HttpProcessor httpProcessor, final Http1Config http1Config) throws IOException {
        start(new InternalClientH2EventHandlerFactory(
                httpProcessor,
                new DefaultAsyncPushConsumerFactory(registry),
                HttpVersionPolicy.FORCE_HTTP_1,
                H2Config.DEFAULT,
                http1Config,
                CharCodingConfig.DEFAULT,
                sslContext,
                sslSessionInitializer,
                sslSessionVerifier));
    }

    public void start(final H2Config h2Config) throws IOException {
        start(H2Processors.client(), h2Config);
    }

    public void start(final Http1Config http1Config) throws IOException {
        start(H2Processors.client(), http1Config);
    }

    public void start() throws Exception {
        start(H2Config.DEFAULT);
    }

    public Future<ClientSessionEndpoint> connect(
            final HttpHost host,
            final Timeout timeout,
            final FutureCallback<ClientSessionEndpoint> callback) {
        final BasicFuture<ClientSessionEndpoint> future = new BasicFuture<>(callback);
        requestSession(host, timeout, new FutureContribution<IOSession>(future) {

            @Override
            public void completed(final IOSession session) {
                future.completed(new ClientSessionEndpoint(session));
            }

        });
        return future;
    }

    public Future<ClientSessionEndpoint> connect(final HttpHost host,final Timeout timeout) {
        return connect(host, timeout, null);
    }

    public Future<ClientSessionEndpoint> connect(final String hostname, final int port, final Timeout timeout) {
        return connect(new HttpHost(hostname, port), timeout, null);
    }

}
