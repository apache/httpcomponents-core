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

package org.apache.hc.core5.http2.impl.nio.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequester;
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
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.nio.pool.H2ConnPool;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 multiplexing client side message exchange initiator.
 *
 * @since 5.0
 */
public class H2MultiplexingRequester extends AsyncRequester{

    private final H2ConnPool connPool;

    /**
     * Use {@link H2MultiplexingRequesterBootstrap} to create instances of this class.
     */
    @Internal
    public H2MultiplexingRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy) {
        super(eventHandlerFactory, ioReactorConfig, ioSessionDecorator, exceptionCallback, sessionListener,
                        ShutdownCommand.GRACEFUL_IMMEDIATE_CALLBACK, DefaultAddressResolver.INSTANCE);
        this.connPool = new H2ConnPool(this, addressResolver, tlsStrategy);
    }

    public void closeIdle(final TimeValue idleTime) {
        connPool.closeIdle(idleTime);
    }

    public Set<HttpHost> getRoutes() {
        return connPool.getRoutes();
    }

    public TimeValue getValidateAfterInactivity() {
        return connPool.getValidateAfterInactivity();
    }

    public void setValidateAfterInactivity(final TimeValue timeValue) {
        connPool.setValidateAfterInactivity(timeValue);
    }

    public Cancellable execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Timeout timeout,
            final HttpContext context) {
        Args.notNull(exchangeHandler, "Exchange handler");
        Args.notNull(timeout, "Timeout");
        Args.notNull(context, "Context");
        final CancellableExecution cancellableExecution = new CancellableExecution();
        execute(exchangeHandler, pushHandlerFactory, cancellableExecution, timeout, context);
        return cancellableExecution;
    }

    public Cancellable execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final Timeout timeout,
            final HttpContext context) {
        return execute(exchangeHandler, null, timeout, context);
    }

    private void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final CancellableDependency cancellableDependency,
            final Timeout timeout,
            final HttpContext context) {
        Args.notNull(exchangeHandler, "Exchange handler");
        Args.notNull(timeout, "Timeout");
        Args.notNull(context, "Context");
        try {
            exchangeHandler.produceRequest((request, entityDetails, httpContext) -> {
                final String scheme = request.getScheme();
                final URIAuthority authority = request.getAuthority();
                if (authority == null) {
                    throw new ProtocolException("Request authority not specified");
                }
                final HttpHost target = new HttpHost(scheme, authority);
                connPool.getSession(target, timeout, new FutureCallback<IOSession>() {

                    @Override
                    public void completed(final IOSession ioSession) {
                        ioSession.enqueue(new RequestExecutionCommand(new AsyncClientExchangeHandler() {

                            @Override
                            public void releaseResources() {
                                exchangeHandler.releaseResources();
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
                                exchangeHandler.streamEnd(trailers);
                            }

                            @Override
                            public void cancel() {
                                exchangeHandler.cancel();
                            }

                            @Override
                            public void failed(final Exception cause) {
                                exchangeHandler.failed(cause);
                            }

                        }, pushHandlerFactory, cancellableDependency, context), Command.Priority.NORMAL);
                        if (!ioSession.isOpen()) {
                            exchangeHandler.failed(new ConnectionClosedException());
                        }
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

            }, context);
        } catch (final IOException | HttpException ex) {
            exchangeHandler.failed(ex);
        }
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
        final ComplexFuture<T> future = new ComplexFuture<>(callback);
        final AsyncClientExchangeHandler exchangeHandler = new BasicClientExchangeHandler<>(
                requestProducer,
                responseConsumer,
                new FutureContribution<T>(future) {

                    @Override
                    public void completed(final T result) {
                        future.completed(result);
                    }

                });
        execute(exchangeHandler, pushHandlerFactory, future, timeout, context != null ? context : HttpCoreContext.create());
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

}
