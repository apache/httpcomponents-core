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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.concurrent.CompletingFutureContribution;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
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
import org.apache.hc.core5.reactor.IOReactorMetricsListener;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.IOWorkerSelector;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 multiplexing client side message exchange initiator.
 *
 * @since 5.0
 */
public class H2MultiplexingRequester extends AsyncRequester {

    private final H2ConnPool connPool;

    private final int maxRequestsPerConnection;
    private final RequestSubmissionPolicy requestSubmissionPolicy;
    private final ConcurrentMap<String, ConnRequestState> connRequestStates;

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
            final TlsStrategy tlsStrategy,
            final IOReactorMetricsListener threadPoolListener,
            final IOWorkerSelector workerSelector) {
        this(ioReactorConfig, eventHandlerFactory, ioSessionDecorator, exceptionCallback, sessionListener,
                addressResolver, tlsStrategy, threadPoolListener, workerSelector, init(sessionListener, 0, RequestSubmissionPolicy.REJECT));
    }

    @Internal
    public H2MultiplexingRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final IOReactorMetricsListener threadPoolListener,
            final IOWorkerSelector workerSelector,
            final int maxRequestsPerConnection,
            final RequestSubmissionPolicy requestSubmissionPolicy) {
        this(ioReactorConfig, eventHandlerFactory, ioSessionDecorator, exceptionCallback, sessionListener,
                addressResolver, tlsStrategy, threadPoolListener, workerSelector, init(sessionListener, maxRequestsPerConnection, requestSubmissionPolicy));
    }

    private H2MultiplexingRequester(
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final IOReactorMetricsListener threadPoolListener,
            final IOWorkerSelector workerSelector,
            final Init init) {
        super(eventHandlerFactory, ioReactorConfig, ioSessionDecorator, exceptionCallback, init.sessionListener,
                ShutdownCommand.GRACEFUL_IMMEDIATE_CALLBACK, DefaultAddressResolver.INSTANCE,
                threadPoolListener, workerSelector);
        this.connPool = new H2ConnPool(this, addressResolver, tlsStrategy);
        this.maxRequestsPerConnection = init.maxRequestsPerConnection;
        this.requestSubmissionPolicy = init.requestSubmissionPolicy;
        this.connRequestStates = init.connRequestStates;
    }

    private ConnRequestState getConnRequestState(final IOSession ioSession) {
        final String id = ioSession.getId();
        ConnRequestState state = connRequestStates.get(id);
        if (state == null) {
            final ConnRequestState newState = new ConnRequestState();
            final ConnRequestState existing = connRequestStates.putIfAbsent(id, newState);
            state = existing != null ? existing : newState;
        }
        return state;
    }

    private void drainQueue(final IOSession ioSession, final ConnRequestState state) {
        if (!ioSession.isOpen()) {
            state.abortPending(new ConnectionClosedException());
            return;
        }
        for (;;) {
            if (!ioSession.isOpen()) {
                state.abortPending(new ConnectionClosedException());
                return;
            }
            if (!state.tryAcquire(maxRequestsPerConnection)) {
                return;
            }
            final QueuedRequest queuedRequest = state.poll();
            if (queuedRequest == null) {
                state.release();
                return;
            }
            if (!queuedRequest.submit(this)) {
                state.release();
            }
        }
    }

    private void submitRequest(
            final IOSession ioSession,
            final ConnRequestState connRequestState,
            final HttpRequest request,
            final EntityDetails entityDetails,
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final CancellableDependency cancellableDependency,
            final HttpContext context,
            final QueuedRequest queuedRequest) {
        final AtomicBoolean released = new AtomicBoolean(false);
        final AsyncClientExchangeHandler handlerProxy = new AsyncClientExchangeHandler() {

            private void releaseSlotIfNeeded() {
                if (released.compareAndSet(false, true)) {
                    connRequestState.release();
                    if (requestSubmissionPolicy == RequestSubmissionPolicy.QUEUE) {
                        drainQueue(ioSession, connRequestState);
                    }
                }
            }

            @Override
            public void releaseResources() {
                try {
                    exchangeHandler.releaseResources();
                } finally {
                    releaseSlotIfNeeded();
                }
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
                try {
                    exchangeHandler.cancel();
                } finally {
                    releaseSlotIfNeeded();
                }
            }

            @Override
            public void failed(final Exception cause) {
                try {
                    exchangeHandler.failed(cause);
                } finally {
                    releaseSlotIfNeeded();
                }
            }

        };
        if (queuedRequest != null) {
            queuedRequest.handlerProxy = handlerProxy;
        }
        final Timeout socketTimeout = ioSession.getSocketTimeout();
        ioSession.enqueue(new RequestExecutionCommand(
                        handlerProxy,
                        pushHandlerFactory,
                        context,
                        streamControl -> {
                            cancellableDependency.setDependency(streamControl);
                            if (socketTimeout != null) {
                                streamControl.setTimeout(socketTimeout);
                            }
                        }),
                Command.Priority.NORMAL);
        if (!ioSession.isOpen()) {
            handlerProxy.failed(new ConnectionClosedException());
            handlerProxy.releaseResources();
        }
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

    /**
     * @since 5.3
     */
    public Cancellable execute(
            final HttpHost target,
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Timeout timeout,
            final HttpContext context) {
        Args.notNull(exchangeHandler, "Exchange handler");
        Args.notNull(timeout, "Timeout");
        Args.notNull(context, "Context");
        final CancellableExecution cancellableExecution = new CancellableExecution();
        execute(target, exchangeHandler, pushHandlerFactory, cancellableExecution, timeout, context);
        return cancellableExecution;
    }

    public Cancellable execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Timeout timeout,
            final HttpContext context) {
        return execute(null, exchangeHandler, pushHandlerFactory, timeout, context);
    }

    /**
     * @since 5.3
     */
    public Cancellable execute(
            final HttpHost target,
            final AsyncClientExchangeHandler exchangeHandler,
            final Timeout timeout,
            final HttpContext context) {
        return execute(target, exchangeHandler, null, timeout, context);
    }

    public Cancellable execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final Timeout timeout,
            final HttpContext context) {
        return execute(null, exchangeHandler, null, timeout, context);
    }

    private void execute(
            final HttpHost target,
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
                final HttpHost host = target != null ? target : defaultTarget(request);
                if (request.getAuthority() == null) {
                    request.setAuthority(new URIAuthority(host));
                }
                connPool.getSession(host, timeout, new FutureCallback<IOSession>() {

                    @Override
                    public void completed(final IOSession ioSession) {
                        if (maxRequestsPerConnection > 0) {
                            final ConnRequestState connRequestState = getConnRequestState(ioSession);
                            if (connRequestState.tryAcquire(maxRequestsPerConnection)) {
                                submitRequest(ioSession, connRequestState, request, entityDetails, exchangeHandler, pushHandlerFactory,
                                        cancellableDependency, context, null);
                                return;
                            }
                            if (requestSubmissionPolicy == RequestSubmissionPolicy.QUEUE) {
                                final QueuedRequest queuedRequest = new QueuedRequest(connRequestState, ioSession, request, entityDetails,
                                        exchangeHandler, pushHandlerFactory, cancellableDependency, context);
                                cancellableDependency.setDependency(queuedRequest);
                                connRequestState.enqueue(queuedRequest);
                                return;
                            }
                            exchangeHandler.failed(new RejectedExecutionException(
                                    "Maximum number of pending requests per connection reached (max=" + maxRequestsPerConnection + ")"));
                            exchangeHandler.releaseResources();
                            return;
                        }
                        final AsyncClientExchangeHandler handlerProxy = new AsyncClientExchangeHandler() {

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

                        };
                        final Timeout socketTimeout = ioSession.getSocketTimeout();
                        ioSession.enqueue(new RequestExecutionCommand(
                                        handlerProxy,
                                        pushHandlerFactory,
                                        context,
                                        streamControl -> {
                                            cancellableDependency.setDependency(streamControl);
                                            if (socketTimeout != null) {
                                                streamControl.setTimeout(socketTimeout);
                                            }
                                        }),
                                Command.Priority.NORMAL);
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

    /**
     * @param <T> The result type returned by the Future's {@code get} method.
     * @since 5.3
     */
    public final <T> Future<T> execute(
            final HttpHost target,
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
                new CompletingFutureContribution<T, T>(future));
        execute(target, exchangeHandler, pushHandlerFactory, future, timeout, context != null ? context : HttpCoreContext.create());
        return future;
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final Timeout timeout,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return execute(null, requestProducer, responseConsumer, pushHandlerFactory, timeout, context, callback);
    }

    /**
     * @param <T> The result type returned by the Future's {@code get} method.
     * @since 5.3
     */
    public final <T> Future<T> execute(
            final HttpHost target,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final Timeout timeout,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return execute(target, requestProducer, responseConsumer, null, timeout, context, callback);
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final Timeout timeout,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return execute(null, requestProducer, responseConsumer, null, timeout, context, callback);
    }

    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final Timeout timeout,
            final FutureCallback<T> callback) {
        return execute(null, requestProducer, responseConsumer, null, timeout, null, callback);
    }

    @Internal
    public H2ConnPool getConnPool() {
        return connPool;
    }

    private static final class ConnRequestState {

        private final AtomicInteger active;
        private final ConcurrentLinkedQueue<QueuedRequest> queue;

        ConnRequestState() {
            this.active = new AtomicInteger(0);
            this.queue = new ConcurrentLinkedQueue<>();
        }

        boolean tryAcquire(final int max) {
            for (;;) {
                final int current = active.get();
                if (current >= max) {
                    return false;
                }
                if (active.compareAndSet(current, current + 1)) {
                    return true;
                }
            }
        }

        void release() {
            active.decrementAndGet();
        }

        void enqueue(final QueuedRequest request) {
            queue.add(request);
        }

        boolean remove(final QueuedRequest request) {
            return queue.remove(request);
        }

        QueuedRequest poll() {
            return queue.poll();
        }

        void abortPending(final Exception cause) {
            for (;;) {
                final QueuedRequest request = queue.poll();
                if (request == null) {
                    return;
                }
                request.failed(cause);
            }
        }

    }

    private static final class QueuedRequest implements Cancellable {

        private final ConnRequestState connRequestState;
        private final IOSession ioSession;
        private final HttpRequest request;
        private final EntityDetails entityDetails;
        private final AsyncClientExchangeHandler exchangeHandler;
        private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
        private final CancellableDependency cancellableDependency;
        private final HttpContext context;

        private final AtomicBoolean cancelled;
        private final AtomicBoolean submitted;
        private volatile AsyncClientExchangeHandler handlerProxy;

        QueuedRequest(
                final ConnRequestState connRequestState,
                final IOSession ioSession,
                final HttpRequest request,
                final EntityDetails entityDetails,
                final AsyncClientExchangeHandler exchangeHandler,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final CancellableDependency cancellableDependency,
                final HttpContext context) {
            this.connRequestState = connRequestState;
            this.ioSession = ioSession;
            this.request = request;
            this.entityDetails = entityDetails;
            this.exchangeHandler = exchangeHandler;
            this.pushHandlerFactory = pushHandlerFactory;
            this.cancellableDependency = cancellableDependency;
            this.context = context;
            this.cancelled = new AtomicBoolean(false);
            this.submitted = new AtomicBoolean(false);
        }

        @Override
        public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            final AsyncClientExchangeHandler proxy = handlerProxy;
            if (proxy != null) {
                proxy.cancel();
            } else {
                connRequestState.remove(this);
                exchangeHandler.cancel();
                exchangeHandler.releaseResources();
            }
            return true;
        }

        void failed(final Exception cause) {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            final AsyncClientExchangeHandler proxy = handlerProxy;
            if (proxy != null) {
                proxy.failed(cause);
            } else {
                exchangeHandler.failed(cause);
                exchangeHandler.releaseResources();
            }
        }

        boolean submit(final H2MultiplexingRequester requester) {
            if (cancelled.get()) {
                return false;
            }
            if (!submitted.compareAndSet(false, true)) {
                return true;
            }
            requester.submitRequest(ioSession, connRequestState, request, entityDetails, exchangeHandler, pushHandlerFactory,
                    cancellableDependency, context, this);
            return true;
        }

    }

    private static final class Init {

        private final int maxRequestsPerConnection;
        private final RequestSubmissionPolicy requestSubmissionPolicy;
        private final ConcurrentMap<String, ConnRequestState> connRequestStates;
        private final IOSessionListener sessionListener;

        Init(final IOSessionListener sessionListener, final int maxRequestsPerConnection, final RequestSubmissionPolicy requestSubmissionPolicy) {
            this.maxRequestsPerConnection = maxRequestsPerConnection;
            this.requestSubmissionPolicy = requestSubmissionPolicy != null ? requestSubmissionPolicy : RequestSubmissionPolicy.REJECT;
            this.connRequestStates = maxRequestsPerConnection > 0 ? new ConcurrentHashMap<String, ConnRequestState>() : null;
            this.sessionListener = new IOSessionListener() {

                @Override
                public void connected(final IOSession session) {
                    if (sessionListener != null) {
                        sessionListener.connected(session);
                    }
                }

                @Override
                public void startTls(final IOSession session) {
                    if (sessionListener != null) {
                        sessionListener.startTls(session);
                    }
                }

                @Override
                public void inputReady(final IOSession session) {
                    if (sessionListener != null) {
                        sessionListener.inputReady(session);
                    }
                }

                @Override
                public void outputReady(final IOSession session) {
                    if (sessionListener != null) {
                        sessionListener.outputReady(session);
                    }
                }

                @Override
                public void timeout(final IOSession session) {
                    if (sessionListener != null) {
                        sessionListener.timeout(session);
                    }
                }

                @Override
                public void exception(final IOSession session, final Exception ex) {
                    if (sessionListener != null) {
                        sessionListener.exception(session, ex);
                    }
                }

                @Override
                public void disconnected(final IOSession session) {
                    try {
                        if (sessionListener != null) {
                            sessionListener.disconnected(session);
                        }
                    } finally {
                        if (connRequestStates != null) {
                            final ConnRequestState state = connRequestStates.remove(session.getId());
                            if (state != null) {
                                state.abortPending(new ConnectionClosedException());
                            }
                        }
                    }
                }

            };
        }

    }

    private static Init init(final IOSessionListener sessionListener, final int maxRequestsPerConnection, final RequestSubmissionPolicy requestSubmissionPolicy) {
        return new Init(sessionListener, maxRequestsPerConnection, requestSubmissionPolicy);
    }

}