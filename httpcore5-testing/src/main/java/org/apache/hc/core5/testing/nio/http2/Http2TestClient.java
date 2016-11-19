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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.Supplier;
import org.apache.hc.core5.http.impl.nio.bootstrap.AsyncRequester;
import org.apache.hc.core5.http.impl.nio.bootstrap.ClientEndpoint;
import org.apache.hc.core5.http.impl.nio.bootstrap.ClientEndpointImpl;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http2.bootstrap.Http2Processors;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionCallback;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
public class Http2TestClient extends AsyncRequester {

    private final SSLContext sslContext;
    private final UriPatternMatcher<Supplier<AsyncPushConsumer>> pushHandlerMatcher;

    public Http2TestClient(final IOReactorConfig ioReactorConfig, final SSLContext sslContext) throws IOException {
        super(ioReactorConfig, new ExceptionListener() {

            private final Logger log = LogManager.getLogger(Http2TestClient.class);

            @Override
            public void onError(final Exception ex) {
                log.error(ex.getMessage(), ex);
            }

        }, new IOSessionCallback() {

            @Override
            public void execute(final IOSession session) throws IOException {
                session.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
            }

        });
        this.sslContext = sslContext;
        this.pushHandlerMatcher = new UriPatternMatcher<>();
    }

    public Http2TestClient() throws IOException {
        this(IOReactorConfig.DEFAULT, null);
    }

    private AsyncPushConsumer createHandler(final HttpRequest request) throws HttpException {

        final URIAuthority authority = request.getAuthority();
        if (authority != null && !"localhost".equalsIgnoreCase(authority.getHostName())) {
            throw new MisdirectedRequestException("Not authoritative");
        }
        String path = request.getPath();
        final int i = path.indexOf("?");
        if (i != -1) {
            path = path.substring(0, i - 1);
        }
        final Supplier<AsyncPushConsumer> supplier = pushHandlerMatcher.lookup(path);
        if (supplier != null) {
            return supplier.get();
        } else {
            return null;
        }
    }

    public void register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushHandlerMatcher.register(uriPattern, supplier);
    }

    public void start(final IOEventHandlerFactory handlerFactory) throws IOException {
        super.execute(handlerFactory);
    }

    public void start(final HttpProcessor httpProcessor, final H2Config h2Config) throws IOException {
        start(new InternalClientHttp2EventHandlerFactory(
                httpProcessor,
                new HandlerFactory<AsyncPushConsumer>() {

                    @Override
                    public AsyncPushConsumer create(final HttpRequest request) throws HttpException {
                        return createHandler(request);
                    }

                },
                StandardCharsets.US_ASCII,
                h2Config,
                sslContext));
    }

    public void start(final H2Config h2Config) throws IOException {
        start(Http2Processors.client(), h2Config);
    }

    public void start() throws Exception {
        start(H2Config.DEFAULT);
    }

    public Future<ClientEndpoint> connect(
            final NamedEndpoint remoteEndpoint,
            final long timeout,
            final TimeUnit timeUnit,
            final FutureCallback<ClientEndpoint> callback) throws InterruptedException {
        final BasicFuture<ClientEndpoint> future = new BasicFuture<>(callback);
        requestSession(remoteEndpoint, timeout, timeUnit, new SessionRequestCallback() {

            @Override
            public void completed(final SessionRequest request) {
                final IOSession session = request.getSession();
                future.completed(new ClientEndpointImpl(session));
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

    public Future<ClientEndpoint> connect(
            final NamedEndpoint remoteEndpoint,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException {
        return connect(remoteEndpoint, timeout, timeUnit, null);
    }

    public Future<ClientEndpoint> connect(
            final String hostname,
            final int port,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException {
        return connect(new HttpHost(hostname, port), timeout, timeUnit);
    }

}
