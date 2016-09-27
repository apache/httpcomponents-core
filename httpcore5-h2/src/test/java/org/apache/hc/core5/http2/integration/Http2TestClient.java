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

package org.apache.hc.core5.http2.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.nio.AsyncPushConsumer;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.http2.nio.Supplier;
import org.apache.hc.core5.http2.nio.command.ClientCommandEndpoint;
import org.apache.hc.core5.http2.nio.command.ShutdownCommand;
import org.apache.hc.core5.http2.nio.command.ShutdownType;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorExceptionHandler;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionCallback;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;

public class Http2TestClient {

    private final ExecutorService executorService;
    private final UriPatternMatcher<Supplier<AsyncPushConsumer>> pushHandlerMatcher;

    private volatile DefaultConnectingIOReactor ioReactor;
    private volatile Exception exception;

    public Http2TestClient() throws IOException {
        super();
        this.executorService = Executors.newSingleThreadExecutor();
        this.pushHandlerMatcher = new UriPatternMatcher<>();
    }

    public Future<ClientCommandEndpoint> connect(
            final InetSocketAddress address,
            final int connectTimeout,
            final Object attachment,
            final FutureCallback<ClientCommandEndpoint> callback) throws InterruptedException {
        final BasicFuture<ClientCommandEndpoint> future = new BasicFuture<>(callback);
        final SessionRequest sessionRequest = this.ioReactor.connect(address, null, attachment, new SessionRequestCallback() {

            @Override
            public void completed(final SessionRequest request) {
                final IOSession session = request.getSession();
                future.completed(new ClientCommandEndpoint(session));
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
        sessionRequest.setConnectTimeout(connectTimeout);
        return future;
    }

    public Future<ClientCommandEndpoint> connect(
            final InetSocketAddress address,
            final int connectTimeout) throws InterruptedException {
        return connect(address, connectTimeout, null, null);
    }

    public Future<ClientCommandEndpoint> connect(
            final String hostname,
            final int port,
            final int connectTimeout) throws InterruptedException {
        return connect(new InetSocketAddress(hostname, port), connectTimeout, null, null);
    }

    private AsyncPushConsumer createHandler(final HttpRequest request) throws HttpException, IOException {

        final HttpHost authority;
        try {
            authority = HttpHost.create(request.getAuthority());
        } catch (IllegalArgumentException ex) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, ex.getMessage());
        }
        if (!"localhost".equalsIgnoreCase(authority.getHostName())) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Not authoritative");
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

    public void registerHandler(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushHandlerMatcher.register(uriPattern, supplier);
    }

    public void start() throws Exception {
        start(H2Config.DEFAULT);
    }

    public void start(final H2Config h2Config) throws Exception {
        final HttpProcessor httpProcessor = new DefaultHttpProcessor(
                new H2RequestContent(),
                new H2RequestTargetHost(),
                new H2RequestConnControl(),
                new RequestUserAgent("TEST-CLIENT/1.1"),
                new RequestExpectContinue());
        this.ioReactor = new DefaultConnectingIOReactor(new InternalClientHttp2EventHandlerFactory(
                httpProcessor,
                new HandlerFactory<AsyncPushConsumer>() {

                    @Override
                    public AsyncPushConsumer create(
                            final HttpRequest request, final HttpContext context) throws HttpException, IOException {
                        return createHandler(request);
                    }

                },
                StandardCharsets.US_ASCII,
                h2Config));
        ioReactor.setExceptionHandler(new IOReactorExceptionHandler() {

            @Override
            public boolean handle(final IOException ex) {
                ex.printStackTrace();
                return false;
            }

            @Override
            public boolean handle(final RuntimeException ex) {
                ex.printStackTrace();
                return false;
            }

        });
        this.executorService.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    ioReactor.execute();
                } catch (Exception ex) {
                    exception = ex;
                }
            }
        });
    }

    public IOReactorStatus getStatus() {
        return this.ioReactor.getStatus();
    }

    public List<ExceptionEvent> getAuditLog() {
        return this.ioReactor.getAuditLog();
    }

    public Exception getException() {
        return this.exception;
    }

    public void awaitShutdown(final long deadline, final TimeUnit timeUnit) throws InterruptedException {
        ioReactor.awaitShutdown(deadline, timeUnit);
    }

    public void initiateShutdown() throws IOException {
        ioReactor.initiateShutdown();
        ioReactor.enumSessions(new IOSessionCallback() {

            @Override
            public void execute(final IOSession session) throws IOException {
                session.getCommandQueue().addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
                session.setEvent(SelectionKey.OP_WRITE);
            }

        });
    }

    public void shutdown(final long graceTime, final TimeUnit timeUnit) throws IOException {
        initiateShutdown();
        ioReactor.shutdown(graceTime, timeUnit);
    }

}
