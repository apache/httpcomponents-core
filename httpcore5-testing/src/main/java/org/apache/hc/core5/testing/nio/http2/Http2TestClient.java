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

package org.apache.hc.core5.testing.nio.http2;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.ClientSessionEndpoint;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.Http2Processors;
import org.apache.hc.core5.http2.impl.nio.bootstrap.AsyncPushConsumerRegistry;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Http2TestClient extends AsyncRequester {

    private final SSLContext sslContext;
    private final AsyncPushConsumerRegistry pushConsumerRegistry;

    public Http2TestClient(final IOReactorConfig ioReactorConfig, final SSLContext sslContext) throws IOException {
        super(ioReactorConfig, new ExceptionListener() {

            private final Logger log = LogManager.getLogger(Http2TestClient.class);

            @Override
            public void onError(final Exception ex) {
                log.error(ex.getMessage(), ex);
            }

        }, new Callback<IOSession>() {

            @Override
            public void execute(final IOSession session) {
                session.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
            }

        });
        this.sslContext = sslContext;
        this.pushConsumerRegistry = new AsyncPushConsumerRegistry();
    }

    public Http2TestClient() throws IOException {
        this(IOReactorConfig.DEFAULT, null);
    }

    public void register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushConsumerRegistry.register(null, uriPattern, supplier);
    }

    public void start(final IOEventHandlerFactory handlerFactory) throws IOException {
        super.execute(handlerFactory);
    }

    public void start(final HttpProcessor httpProcessor, final H2Config h2Config) throws IOException {
        start(new InternalClientHttp2EventHandlerFactory(
                httpProcessor,
                pushConsumerRegistry,
                CharCodingConfig.DEFAULT,
                h2Config,
                sslContext));
    }

    public void start(final H2Config h2Config) throws IOException {
        start(Http2Processors.client(), h2Config);
    }

    public void start() throws Exception {
        start(H2Config.DEFAULT);
    }

    public Future<ClientSessionEndpoint> connect(
            final HttpHost host,
            final long timeout,
            final TimeUnit timeUnit,
            final FutureCallback<ClientSessionEndpoint> callback) throws InterruptedException {
        final BasicFuture<ClientSessionEndpoint> future = new BasicFuture<>(callback);
        requestSession(host, timeout, timeUnit, new SessionRequestCallback() {

            @Override
            public void completed(final SessionRequest request) {
                final IOSession session = request.getSession();
                future.completed(new ClientSessionEndpoint(session));
            }

            @Override
            public void failed(final SessionRequest request) {
                future.failed(request.getException());
            }

            @Override
            public void timeout(final SessionRequest request) {
                future.failed(new SocketTimeoutException("Connect timeout"));
            }

            @Override
            public void cancelled(final SessionRequest request) {
                future.cancel();
            }
        });
        return future;
    }

    public Future<ClientSessionEndpoint> connect(
            final HttpHost host,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException {
        return connect(host, timeout, timeUnit, null);
    }

    public Future<ClientSessionEndpoint> connect(
            final String hostname,
            final int port,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException {
        return connect(new HttpHost(hostname, port), timeout, timeUnit);
    }

}
