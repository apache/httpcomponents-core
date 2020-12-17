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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsUpgradeCapable;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.EndpointParameters;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/1.1 client side message exchange initiator.
 *
 * @since 5.0
 */
public class HttpAsyncRequester extends AsyncRequester implements ConnPoolControl<HttpHost> {

    private final ManagedConnPool<HttpHost, IOSession> connPool;
    private final TlsStrategy tlsStrategy;
    private final Timeout handshakeTimeout;

    /**
     * Use {@link AsyncRequesterBootstrap} to create instances of this class.
     *
     * @since 5.2
     */
    @Internal
    public HttpAsyncRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final ManagedConnPool<HttpHost, IOSession> connPool,
            final TlsStrategy tlsStrategy,
            final Timeout handshakeTimeout) {
        super(eventHandlerFactory, ioReactorConfig, ioSessionDecorator, exceptionCallback, sessionListener,
                ShutdownCommand.GRACEFUL_IMMEDIATE_CALLBACK, DefaultAddressResolver.INSTANCE);
        this.connPool = Args.notNull(connPool, "Connection pool");
        this.tlsStrategy = tlsStrategy;
        this.handshakeTimeout = handshakeTimeout;
    }

    /**
     * Use {@link AsyncRequesterBootstrap} to create instances of this class.
     */
    @Internal
    public HttpAsyncRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final ManagedConnPool<HttpHost, IOSession> connPool) {
        this(ioReactorConfig, eventHandlerFactory, ioSessionDecorator, exceptionCallback, sessionListener, connPool,
                null, null);
    }

    @Override
    public PoolStats getTotalStats() {
        return connPool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final HttpHost route) {
        return connPool.getStats(route);
    }

    @Override
    public void setMaxTotal(final int max) {
        connPool.setMaxTotal(max);
    }

    @Override
    public int getMaxTotal() {
        return connPool.getMaxTotal();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        connPool.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return connPool.getDefaultMaxPerRoute();
    }

    @Override
    public void setMaxPerRoute(final HttpHost route, final int max) {
        connPool.setMaxPerRoute(route, max);
    }

    @Override
    public int getMaxPerRoute(final HttpHost route) {
        return connPool.getMaxPerRoute(route);
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        connPool.closeIdle(idleTime);
    }

    @Override
    public void closeExpired() {
        connPool.closeExpired();
    }

    @Override
    public Set<HttpHost> getRoutes() {
        return connPool.getRoutes();
    }

    public Future<AsyncClientEndpoint> connect(
            final HttpHost host,
            final Timeout timeout,
            final Object attachment,
            final FutureCallback<AsyncClientEndpoint> callback) {
        return doConnect(host, timeout, attachment, callback);
    }

    protected Future<AsyncClientEndpoint> doConnect(
            final HttpHost host,
            final Timeout timeout,
            final Object attachment,
            final FutureCallback<AsyncClientEndpoint> callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeout, "Timeout");
        final ComplexFuture<AsyncClientEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<PoolEntry<HttpHost, IOSession>> leaseFuture = connPool.lease(
                host, null, timeout, new FutureCallback<PoolEntry<HttpHost, IOSession>>() {

                    @Override
                    public void completed(final PoolEntry<HttpHost, IOSession> poolEntry) {
                        final AsyncClientEndpoint endpoint = new InternalAsyncClientEndpoint(poolEntry);
                        final IOSession ioSession = poolEntry.getConnection();
                        if (ioSession != null && !ioSession.isOpen()) {
                            poolEntry.discardConnection(CloseMode.IMMEDIATE);
                        }
                        if (poolEntry.hasConnection()) {
                            resultFuture.completed(endpoint);
                        } else {
                            final Future<IOSession> future = requestSession(
                                    host,
                                    timeout,
                                    new EndpointParameters(host, attachment),
                                    new FutureCallback<IOSession>() {

                                        @Override
                                        public void completed(final IOSession session) {
                                            session.setSocketTimeout(timeout);
                                            poolEntry.assignConnection(session);
                                            resultFuture.completed(endpoint);
                                        }

                                        @Override
                                        public void failed(final Exception cause) {
                                            try {
                                                resultFuture.failed(cause);
                                            } finally {
                                                endpoint.releaseAndDiscard();
                                            }
                                        }

                                        @Override
                                        public void cancelled() {
                                            try {
                                                resultFuture.cancel();
                                            } finally {
                                                endpoint.releaseAndDiscard();
                                            }
                                        }

                                    });
                            resultFuture.setDependency(future);
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

    public Future<AsyncClientEndpoint> connect(final HttpHost host, final Timeout timeout) {
        return connect(host, timeout, null, null);
    }

    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Timeout timeout,
            final HttpContext executeContext) {
        Args.notNull(exchangeHandler, "Exchange handler");
        Args.notNull(timeout, "Timeout");
        Args.notNull(executeContext, "Context");
        try {
            exchangeHandler.produceRequest((request, entityDetails, requestContext) -> {
                final String scheme = request.getScheme();
                final URIAuthority authority = request.getAuthority();
                if (authority == null) {
                    throw new ProtocolException("Request authority not specified");
                }
                final HttpHost target = new HttpHost(scheme, authority);
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
                            public void produceRequest(final RequestChannel channel, final HttpContext httpContext) throws HttpException, IOException {
                                channel.sendRequest(request, entityDetails, httpContext);
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
                            public void consumeInformation(final HttpResponse response, final HttpContext httpContext) throws HttpException, IOException {
                                exchangeHandler.consumeInformation(response, httpContext);
                            }

                            @Override
                            public void consumeResponse(
                                    final HttpResponse response, final EntityDetails entityDetails, final HttpContext httpContext) throws HttpException, IOException {
                                if (entityDetails == null) {
                                    endpoint.releaseAndReuse();
                                }
                                exchangeHandler.consumeResponse(response, entityDetails, httpContext);
                            }

                            @Override
                            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                                exchangeHandler.updateCapacity(capacityChannel);
                            }

                            @Override
                            public void consume(final ByteBuffer src) throws IOException {
                                exchangeHandler.consume(src);
                            }

                            @Override
                            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                                endpoint.releaseAndReuse();
                                exchangeHandler.streamEnd(trailers);
                            }

                        }, pushHandlerFactory, executeContext);

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

            }, executeContext);

        } catch (final IOException | HttpException ex) {
            exchangeHandler.failed(ex);
        }
    }

    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final Timeout timeout,
            final HttpContext executeContext) {
        execute(exchangeHandler, null, timeout, executeContext);
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Timeout timeout,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "Request producer");
        Args.notNull(responseConsumer, "Response consumer");
        Args.notNull(timeout, "Timeout");
        final BasicFuture<T> future = new BasicFuture<>(callback);
        final AsyncClientExchangeHandler exchangeHandler = new BasicClientExchangeHandler<>(
                requestProducer,
                responseConsumer,
                new FutureContribution<T>(future) {

                    @Override
                    public void completed(final T result) {
                        future.completed(result);
                    }

                });
        execute(exchangeHandler, pushHandlerFactory, timeout, context != null ? context : HttpCoreContext.create());
        return future;
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final Timeout timeout,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, timeout, context, callback);
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final Timeout timeout,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, timeout, null, callback);
    }

    protected void doTlsUpgrade(
            final ProtocolIOSession ioSession,
            final NamedEndpoint endpoint,
            final FutureCallback<ProtocolIOSession> callback) {
        if (tlsStrategy != null) {
            tlsStrategy.upgrade(ioSession,
                    endpoint,
                    null,
                    handshakeTimeout,
                    new CallbackContribution<TransportSecurityLayer>(callback) {

                        @Override
                        public void completed(final TransportSecurityLayer transportSecurityLayer) {
                            if (callback != null) {
                                callback.completed(ioSession);
                            }
                        }

                    });
        } else {
            throw new IllegalStateException("TLS upgrade not supported");
        }
    }

    private class InternalAsyncClientEndpoint extends AsyncClientEndpoint implements TlsUpgradeCapable {

        final AtomicReference<PoolEntry<HttpHost, IOSession>> poolEntryRef;

        InternalAsyncClientEndpoint(final PoolEntry<HttpHost, IOSession> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
        }

        private IOSession getIOSession() {
            final PoolEntry<HttpHost, IOSession> poolEntry = poolEntryRef.get();
            if (poolEntry == null) {
                throw new IllegalStateException("Endpoint has already been released");
            }
            final IOSession ioSession = poolEntry.getConnection();
            if (ioSession == null) {
                throw new IllegalStateException("I/O session is invalid");
            }
            return ioSession;
        }

        @Override
        public void execute(
                final AsyncClientExchangeHandler exchangeHandler,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final HttpContext context) {
            final IOSession ioSession = getIOSession();
            ioSession.enqueue(new RequestExecutionCommand(exchangeHandler, pushHandlerFactory, null, context), Command.Priority.NORMAL);
            if (!ioSession.isOpen()) {
                try {
                    exchangeHandler.failed(new ConnectionClosedException());
                } finally {
                    exchangeHandler.releaseResources();
                }
            }
        }

        @Override
        public boolean isConnected() {
            final PoolEntry<HttpHost, IOSession> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                final IOSession ioSession = poolEntry.getConnection();
                if (ioSession == null || !ioSession.isOpen()) {
                    return false;
                }
                final IOEventHandler handler = ioSession.getHandler();
                return (handler instanceof HttpConnection) && ((HttpConnection) handler).isOpen();
            }
            return false;
        }

        @Override
        public void releaseAndReuse() {
            final PoolEntry<HttpHost, IOSession> poolEntry = poolEntryRef.getAndSet(null);
            if (poolEntry != null) {
                final IOSession ioSession = poolEntry.getConnection();
                connPool.release(poolEntry, ioSession != null && ioSession.isOpen());
            }
        }

        @Override
        public void releaseAndDiscard() {
            final PoolEntry<HttpHost, IOSession> poolEntry = poolEntryRef.getAndSet(null);
            if (poolEntry != null) {
                poolEntry.discardConnection(CloseMode.GRACEFUL);
                connPool.release(poolEntry, false);
            }
        }

        @Override
        public void tlsUpgrade(final NamedEndpoint endpoint, final FutureCallback<ProtocolIOSession> callback) {
            final IOSession ioSession = getIOSession();
            if (ioSession instanceof ProtocolIOSession) {
                doTlsUpgrade((ProtocolIOSession) ioSession, endpoint, callback);
            } else {
                throw new IllegalStateException("TLS upgrade not supported");
            }
        }
    }

}
