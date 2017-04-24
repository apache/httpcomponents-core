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

package org.apache.hc.core5.http.impl.bootstrap;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.pool.ControlledConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.reactor.TlsCapableIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * @since 5.0
 */
public class HttpAsyncRequester extends AsyncRequester {

    private final ControlledConnPool<HttpHost, IOSession> connPool;
    private final TlsStrategy tlsStrategy;

    public HttpAsyncRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final ControlledConnPool<HttpHost, IOSession> connPool,
            final TlsStrategy tlsStrategy,
            final ExceptionListener exceptionListener) throws IOReactorException {
        super(eventHandlerFactory, ioReactorConfig, exceptionListener, new Callback<IOSession>() {

            @Override
            public void execute(final IOSession session) {
                session.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
            }

        });
        this.connPool = Args.notNull(connPool, "Connection pool");
        this.tlsStrategy = tlsStrategy;
    }

    public void start() throws IOException {
        execute();
    }

    public Future<AsyncClientEndpoint> connect(
            final HttpHost host,
            final TimeValue timeout,
            final Object attachment,
            final FutureCallback<AsyncClientEndpoint> callback) {
        return doConnect(host, timeout, attachment, callback);
    }

    protected Future<AsyncClientEndpoint> doConnect(
            final HttpHost host,
            final TimeValue timeout,
            final Object attachment,
            final FutureCallback<AsyncClientEndpoint> callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeout, "Timeout");
        final ComplexFuture<AsyncClientEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<PoolEntry<HttpHost, IOSession>> leaseFuture = connPool.lease(
                host, null, new FutureCallback<PoolEntry<HttpHost, IOSession>>() {

            @Override
            public void completed(final PoolEntry<HttpHost, IOSession> poolEntry) {
                final AsyncClientEndpoint endpoint = new InternalAsyncClientEndpoint(poolEntry);
                final IOSession ioSession = poolEntry.getConnection();
                if (ioSession != null && ioSession.isClosed()) {
                    poolEntry.discardConnection(ShutdownType.IMMEDIATE);
                }
                if (poolEntry.hasConnection()) {
                    resultFuture.completed(endpoint);
                } else {
                    final SessionRequest sessionRequest = requestSession(host, timeout, attachment, new SessionRequestCallback() {

                        @Override
                        public void completed(final SessionRequest request) {
                            final TlsCapableIOSession session = request.getSession();
                            if (tlsStrategy != null && "https".equalsIgnoreCase(host.getSchemeName())) {
                                tlsStrategy.upgrade(
                                        session,
                                        host,
                                        session.getLocalAddress(),
                                        session.getRemoteAddress(),
                                        attachment);
                            }
                            session.setSocketTimeout(timeout.toMillisIntBound());
                            poolEntry.assignConnection(session);
                            resultFuture.completed(endpoint);
                        }

                        @Override
                        public void failed(final SessionRequest request) {
                            try {
                                resultFuture.failed(request.getException());
                            } finally {
                                endpoint.releaseAndDiscard();
                            }
                        }

                        @Override
                        public void timeout(final SessionRequest request) {
                            try {
                                resultFuture.failed(new SocketTimeoutException("Connect timeout"));
                            } finally {
                                endpoint.releaseAndDiscard();
                            }
                        }

                        @Override
                        public void cancelled(final SessionRequest request) {
                            try {
                                resultFuture.cancel();
                            } finally {
                                endpoint.releaseAndDiscard();
                            }
                        }

                    });
                    resultFuture.setDependency(sessionRequest);
                }
            }

            @Override
            public void failed(final Exception ex) {
                resultFuture.failed(ex);
            }

            @Override
            public void cancelled() {
                resultFuture.cancel();
            }

        });
        resultFuture.setDependency(leaseFuture);
        return resultFuture;
    }

    public Future<AsyncClientEndpoint> connect(final HttpHost host, final TimeValue timeout) throws InterruptedException {
        return connect(host, timeout, null, null);
    }

    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final TimeValue timeout,
            final HttpContext context) {
        Args.notNull(exchangeHandler, "Exchange handler");
        Args.notNull(timeout, "Timeout");
        Args.notNull(context, "Context");
        try {
            exchangeHandler.produceRequest(new RequestChannel() {

                @Override
                public void sendRequest(
                        final HttpRequest request,
                        final EntityDetails entityDetails) throws HttpException, IOException {
                    final String scheme = request.getScheme();
                    final URIAuthority authority = request.getAuthority();
                    if (authority == null) {
                        throw new ProtocolException("Request authority not specified");
                    }
                    final HttpHost target = new HttpHost(authority, scheme);
                    connect(target, timeout, null, new FutureCallback<AsyncClientEndpoint>() {

                        @Override
                        public void completed(final AsyncClientEndpoint endpoint) {
                            endpoint.execute(new AsyncClientExchangeHandler() {

                                @Override
                                public void releaseResources() {
                                    endpoint.releaseAndDiscard();
                                    exchangeHandler.releaseResources();
                                }

                                @Override
                                public void failed(final Exception cause) {
                                    endpoint.releaseAndDiscard();
                                    exchangeHandler.failed(cause);
                                }

                                @Override
                                public void cancel() {
                                    endpoint.releaseAndDiscard();
                                    exchangeHandler.cancel();
                                }

                                @Override
                                public void produceRequest(final RequestChannel channel) throws HttpException, IOException {
                                    channel.sendRequest(request, entityDetails);
                                }

                                @Override
                                public int available() {
                                    return exchangeHandler.available();
                                }

                                @Override
                                public void produce(final DataStreamChannel channel) throws IOException {
                                    exchangeHandler.produce(channel);
                                }

                                @Override
                                public void consumeInformation(final HttpResponse response) throws HttpException, IOException {
                                    exchangeHandler.consumeInformation(response);
                                }

                                @Override
                                public void consumeResponse(
                                        final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
                                    if (entityDetails == null) {
                                        endpoint.releaseAndReuse();
                                    }
                                    exchangeHandler.consumeResponse(response, entityDetails);
                                }

                                @Override
                                public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                                    exchangeHandler.updateCapacity(capacityChannel);
                                }

                                @Override
                                public int consume(final ByteBuffer src) throws IOException {
                                    return exchangeHandler.consume(src);
                                }

                                @Override
                                public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                                    endpoint.releaseAndReuse();
                                    exchangeHandler.streamEnd(trailers);
                                }

                            }, context);

                        }

                        @Override
                        public void failed(final Exception ex) {
                            exchangeHandler.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            exchangeHandler.cancel();
                        }

                    });

                }

            });

        } catch (IOException | HttpException ex) {
            exchangeHandler.failed(ex);
        }
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final TimeValue timeout,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "Request producer");
        Args.notNull(responseConsumer, "Response consumer");
        Args.notNull(timeout, "Timeout");
        final BasicFuture<T> future = new BasicFuture<>(callback);
        final AsyncClientExchangeHandler exchangeHandler = new BasicClientExchangeHandler<>(requestProducer, responseConsumer, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                future.completed(result);
            }

            @Override
            public void failed(final Exception ex) {
                future.failed(ex);
            }

            @Override
            public void cancelled() {
                future.cancel();
            }

        });
        execute(exchangeHandler, timeout, context != null ? context : HttpCoreContext.create());
        return future;
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final TimeValue timeout,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, timeout, null, callback);
    }

    private class InternalAsyncClientEndpoint extends AsyncClientEndpoint {

        final AtomicReference<PoolEntry<HttpHost, IOSession>> poolEntryRef;

        InternalAsyncClientEndpoint(final PoolEntry<HttpHost, IOSession> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
        }

        @Override
        public void execute(final AsyncClientExchangeHandler exchangeHandler, final HttpContext context) {
            final PoolEntry<HttpHost, IOSession> poolEntry = poolEntryRef.get();
            if (poolEntry == null) {
                throw new IllegalStateException("Endpoint has already been released");
            }
            final IOSession ioSession = poolEntry.getConnection();
            if (ioSession == null) {
                throw new IllegalStateException("I/O session is invalid");
            }
            ioSession.addLast(new ExecutionCommand(exchangeHandler, context));
        }

        @Override
        public void releaseAndReuse() {
            final PoolEntry<HttpHost, IOSession> poolEntry = poolEntryRef.getAndSet(null);
            if (poolEntry != null) {
                final IOSession ioSession = poolEntry.getConnection();
                connPool.release(poolEntry, ioSession != null && !ioSession.isClosed());
            }
        }

        @Override
        public void releaseAndDiscard() {
            final PoolEntry<HttpHost, IOSession> poolEntry = poolEntryRef.getAndSet(null);
            if (poolEntry != null) {
                poolEntry.discardConnection(ShutdownType.IMMEDIATE);
                connPool.release(poolEntry, false);    ;
            }
        }

    }

}
