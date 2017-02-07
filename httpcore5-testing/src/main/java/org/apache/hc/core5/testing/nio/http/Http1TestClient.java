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

package org.apache.hc.core5.testing.nio.http;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.ClientSessionEndpoint;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Http1TestClient extends AsyncRequester {

    private final SSLContext sslContext;

    public Http1TestClient(final IOReactorConfig ioReactorConfig, final SSLContext sslContext) throws IOException {
        super(ioReactorConfig, new ExceptionListener() {

            private final Logger log = LogManager.getLogger(Http1TestClient.class);

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
    }

    public Http1TestClient() throws IOException {
        this(IOReactorConfig.DEFAULT, null);
    }

    public void start(
            final HttpProcessor httpProcessor,
            final H1Config h1Config) throws IOException {
        execute(new InternalClientHttp1EventHandlerFactory(
                httpProcessor,
                h1Config,
                CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                sslContext));
    }

    public void start(final H1Config h1Config) throws IOException {
        start(HttpProcessors.client(), h1Config);
    }

    public void start() throws IOException {
        start(H1Config.DEFAULT);
    }

    @Override
    public SessionRequest requestSession(
            final HttpHost host,
            final long timeout,
            final TimeUnit timeUnit,
            final SessionRequestCallback callback) {
        return super.requestSession(host, timeout, timeUnit, callback);
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
