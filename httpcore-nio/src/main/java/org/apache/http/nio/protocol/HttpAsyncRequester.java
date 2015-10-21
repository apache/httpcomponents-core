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
package org.apache.http.nio.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.ExceptionLogger;
import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.ConnPool;
import org.apache.http.pool.PoolEntry;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.util.Args;

/**
 * {@code HttpAsyncRequester} is a utility class that can be used
 * in conjunction with {@link HttpAsyncRequestExecutor} to initiate execution
 * of asynchronous HTTP requests.
 *
 * @see HttpAsyncRequestExecutor
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable
public class HttpAsyncRequester {

    private final HttpProcessor httpprocessor;
    private final ConnectionReuseStrategy connReuseStrategy;
    private final ExceptionLogger exceptionLogger;

    /**
     * @deprecated (4.3) use {@link HttpAsyncRequester#HttpAsyncRequester(HttpProcessor,
     *   ConnectionReuseStrategy)}
     */
    @Deprecated
    public HttpAsyncRequester(
            final HttpProcessor httpprocessor,
            final ConnectionReuseStrategy reuseStrategy,
            final HttpParams params) {
        this(httpprocessor, reuseStrategy);
    }

    /**
     * Creates new instance of {@code HttpAsyncRequester}.
     * @param httpprocessor HTTP protocol processor.
     * @param connReuseStrategy Connection re-use strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param exceptionLogger Exception logger. If {@code null}
     *   {@link ExceptionLogger#NO_OP} will be used. Please note that the exception
     *   logger will be only used to log I/O exception thrown while closing
     *   {@link java.io.Closeable} objects (such as {@link org.apache.http.HttpConnection}).
     *
     * @since 4.4
     */
    public HttpAsyncRequester(
            final HttpProcessor httpprocessor,
            final ConnectionReuseStrategy connReuseStrategy,
            final ExceptionLogger exceptionLogger) {
        super();
        this.httpprocessor = Args.notNull(httpprocessor, "HTTP processor");
        this.connReuseStrategy = connReuseStrategy != null ? connReuseStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.exceptionLogger = exceptionLogger != null ? exceptionLogger : ExceptionLogger.NO_OP;
    }

    /**
     * Creates new instance of HttpAsyncRequester.
     *
     * @since 4.3
     */
    public HttpAsyncRequester(
            final HttpProcessor httpprocessor,
            final ConnectionReuseStrategy connReuseStrategy) {
        this(httpprocessor, connReuseStrategy, (ExceptionLogger) null);
    }

    /**
     * Creates new instance of HttpAsyncRequester.
     *
     * @since 4.3
     */
    public HttpAsyncRequester(final HttpProcessor httpprocessor) {
        this(httpprocessor, null);
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param requestProducer request producer.
     * @param responseConsumer response consumer.
     * @param conn underlying HTTP connection.
     * @param context HTTP context
     * @param callback future callback.
     * @return future representing pending completion of the operation.
     */
    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "HTTP request producer");
        Args.notNull(responseConsumer, "HTTP response consumer");
        Args.notNull(conn, "HTTP connection");
        Args.notNull(context, "HTTP context");
        final BasicAsyncClientExchangeHandler<T> handler = new BasicAsyncClientExchangeHandler<T>(
                requestProducer, responseConsumer, callback, context, conn,
                this.httpprocessor, this.connReuseStrategy);
        initExecution(handler, conn);
        return handler.getFuture();
    }

    private void initExecution(
            final HttpAsyncClientExchangeHandler handler, final NHttpClientConnection conn) {

        final HttpContext context = conn.getContext();
        synchronized (context) {
            context.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, handler);
            if (!conn.isOpen()) {
                handler.failed(new ConnectionClosedException("Connection closed"));
            } else {
                conn.requestOutput();
            }
        }
        if (handler.isDone()) {
            try {
                handler.close();
            } catch (final IOException ex) {
                log(ex);
            }
        }
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param requestProducer request producer.
     * @param responseConsumer response consumer.
     * @param conn underlying HTTP connection.
     * @param context HTTP context
     * @return future representing pending completion of the operation.
     */
    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn,
            final HttpContext context) {
        return execute(requestProducer, responseConsumer, conn, context, null);
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param requestProducer request producer.
     * @param responseConsumer response consumer.
     * @param conn underlying HTTP connection.
     * @return future representing pending completion of the operation.
     */
    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn) {
        return execute(requestProducer, responseConsumer, conn, new BasicHttpContext());
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param requestProducer request producer.
     * @param responseConsumer response consumer.
     * @param connPool pool of persistent reusable connections.
     * @param context HTTP context
     * @param callback future callback.
     * @return future representing pending completion of the operation.
     */
    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final ConnPool<HttpHost, E> connPool,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "HTTP request producer");
        Args.notNull(responseConsumer, "HTTP response consumer");
        Args.notNull(connPool, "HTTP connection pool");
        Args.notNull(context, "HTTP context");
        final BasicFuture<T> future = new BasicFuture<T>(callback);
        final HttpHost target = requestProducer.getTarget();
        connPool.lease(target, null, new ConnRequestCallback<T, E>(
                future, requestProducer, responseConsumer, connPool, context));
        return future;
    }

    /**
     * Initiates asynchronous pipelined HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param target target host.
     * @param requestProducers list of request producers.
     * @param responseConsumers list of response consumers.
     * @param connPool pool of persistent reusable connections.
     * @param context HTTP context
     * @param callback future callback.
     * @return future representing pending completion of the operation.
     *
     * @since 4.4
     */
    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<List<T>> executePipelined(
            final HttpHost target,
            final List<? extends HttpAsyncRequestProducer> requestProducers,
            final List<? extends HttpAsyncResponseConsumer<T>> responseConsumers,
            final ConnPool<HttpHost, E> connPool,
            final HttpContext context,
            final FutureCallback<List<T>> callback) {
        Args.notNull(target, "HTTP target");
        Args.notEmpty(requestProducers, "Request producer list");
        Args.notEmpty(responseConsumers, "Response consumer list");
        Args.notNull(connPool, "HTTP connection pool");
        Args.notNull(context, "HTTP context");
        final BasicFuture<List<T>> future = new BasicFuture<List<T>>(callback);
        connPool.lease(target, null, new ConnPipelinedRequestCallback<T, E>(
                future, requestProducers, responseConsumers, connPool, context));
        return future;
    }

    /**
     * Initiates asynchronous HTTP request execution. This method automatically releases
     * the given pool entry once request execution is completed (successfully or unsuccessfully).
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param requestProducer request producer.
     * @param responseConsumer response consumer.
     * @param poolEntry leased pool entry. It will be automatically released
     *   back to the pool when execution is completed.
     * @param connPool pool of persistent reusable connections.
     * @param context HTTP context
     * @param callback future callback.
     * @return future representing pending completion of the operation.
     *
     * @since 4.3
     */
    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final E poolEntry,
            final ConnPool<HttpHost, E> connPool,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Args.notNull(requestProducer, "HTTP request producer");
        Args.notNull(responseConsumer, "HTTP response consumer");
        Args.notNull(connPool, "HTTP connection pool");
        Args.notNull(poolEntry, "Pool entry");
        Args.notNull(context, "HTTP context");
        final BasicFuture<T> future = new BasicFuture<T>(callback);
        final NHttpClientConnection conn = poolEntry.getConnection();
        final BasicAsyncClientExchangeHandler<T> handler = new BasicAsyncClientExchangeHandler<T>(
                requestProducer, responseConsumer,
                new RequestExecutionCallback<T, E>(future, poolEntry, connPool),
                context, conn,
                this.httpprocessor, this.connReuseStrategy);
        initExecution(handler, conn);
        return future;
    }

    /**
     * Initiates asynchronous pipelined HTTP request execution. This method automatically releases
     * the given pool entry once request execution is completed (successfully or unsuccessfully).
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param requestProducers list of request producers.
     * @param responseConsumers list of response consumers.
     * @param poolEntry leased pool entry. It will be automatically released
     *   back to the pool when execution is completed.
     * @param connPool pool of persistent reusable connections.
     * @param context HTTP context
     * @param callback future callback.
     * @return future representing pending completion of the operation.
     *
     * @since 4.4
     */
    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<List<T>> executePipelined(
            final List<HttpAsyncRequestProducer> requestProducers,
            final List<HttpAsyncResponseConsumer<T>> responseConsumers,
            final E poolEntry,
            final ConnPool<HttpHost, E> connPool,
            final HttpContext context,
            final FutureCallback<List<T>> callback) {
        Args.notEmpty(requestProducers, "Request producer list");
        Args.notEmpty(responseConsumers, "Response consumer list");
        Args.notNull(connPool, "HTTP connection pool");
        Args.notNull(poolEntry, "Pool entry");
        Args.notNull(context, "HTTP context");
        final BasicFuture<List<T>> future = new BasicFuture<List<T>>(callback);
        final NHttpClientConnection conn = poolEntry.getConnection();
        final PipeliningClientExchangeHandler<T> handler = new PipeliningClientExchangeHandler<T>(
                requestProducers, responseConsumers,
                new RequestExecutionCallback<List<T>, E>(future, poolEntry, connPool),
                context, conn,
                this.httpprocessor, this.connReuseStrategy);
        initExecution(handler, conn);
        return future;
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param requestProducer request producer.
     * @param responseConsumer response consumer.
     * @param connPool pool of persistent reusable connections.
     * @param context HTTP context
     * @return future representing pending completion of the operation.
     */
    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final ConnPool<HttpHost, E> connPool,
            final HttpContext context) {
        return execute(requestProducer, responseConsumer, connPool, context, null);
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param requestProducer request producer.
     * @param responseConsumer response consumer.
     * @param connPool pool of persistent reusable connections.
     * @return future representing pending completion of the operation.
     */
    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final ConnPool<HttpHost, E> connPool) {
        return execute(requestProducer, responseConsumer, connPool, new BasicHttpContext());
    }

    class ConnRequestCallback<T, E extends PoolEntry<HttpHost, NHttpClientConnection>> implements FutureCallback<E> {

        private final BasicFuture<T> requestFuture;
        private final HttpAsyncRequestProducer requestProducer;
        private final HttpAsyncResponseConsumer<T> responseConsumer;
        private final ConnPool<HttpHost, E> connPool;
        private final HttpContext context;

        ConnRequestCallback(
                final BasicFuture<T> requestFuture,
                final HttpAsyncRequestProducer requestProducer,
                final HttpAsyncResponseConsumer<T> responseConsumer,
                final ConnPool<HttpHost, E> connPool,
                final HttpContext context) {
            super();
            this.requestFuture = requestFuture;
            this.requestProducer = requestProducer;
            this.responseConsumer = responseConsumer;
            this.connPool = connPool;
            this.context = context;
        }

        @Override
        public void completed(final E result) {
            if (this.requestFuture.isDone()) {
                this.connPool.release(result, true);
                return;
            }
            final NHttpClientConnection conn = result.getConnection();
            final BasicAsyncClientExchangeHandler<T> handler = new BasicAsyncClientExchangeHandler<T>(
                    this.requestProducer, this.responseConsumer,
                    new RequestExecutionCallback<T, E>(this.requestFuture, result, this.connPool),
                    this.context, conn, httpprocessor, connReuseStrategy);
            initExecution(handler, conn);
        }

        @Override
        public void failed(final Exception ex) {
            try {
                try {
                    this.responseConsumer.failed(ex);
                } finally {
                    releaseResources();
                }
            } finally {
                this.requestFuture.failed(ex);
            }
        }

        @Override
        public void cancelled() {
            try {
                try {
                    this.responseConsumer.cancel();
                } finally {
                    releaseResources();
                }
            } finally {
                this.requestFuture.cancel(true);
            }
        }

        public void releaseResources() {
            close(requestProducer);
            close(responseConsumer);
        }

    }

    class ConnPipelinedRequestCallback<T, E extends PoolEntry<HttpHost, NHttpClientConnection>> implements FutureCallback<E> {

        private final BasicFuture<List<T>> requestFuture;
        private final List<? extends HttpAsyncRequestProducer> requestProducers;
        private final List<? extends HttpAsyncResponseConsumer<T>> responseConsumers;
        private final ConnPool<HttpHost, E> connPool;
        private final HttpContext context;

        ConnPipelinedRequestCallback(
                final BasicFuture<List<T>> requestFuture,
                final List<? extends HttpAsyncRequestProducer> requestProducers,
                final List<? extends HttpAsyncResponseConsumer<T>> responseConsumers,
                final ConnPool<HttpHost, E> connPool,
                final HttpContext context) {
            super();
            this.requestFuture = requestFuture;
            this.requestProducers = requestProducers;
            this.responseConsumers = responseConsumers;
            this.connPool = connPool;
            this.context = context;
        }

        @Override
        public void completed(final E result) {
            if (this.requestFuture.isDone()) {
                this.connPool.release(result, true);
                return;
            }
            final NHttpClientConnection conn = result.getConnection();
            final PipeliningClientExchangeHandler<T> handler = new PipeliningClientExchangeHandler<T>(
                    this.requestProducers, this.responseConsumers,
                    new RequestExecutionCallback<List<T>, E>(this.requestFuture, result, this.connPool),
                    this.context, conn, httpprocessor, connReuseStrategy);
            initExecution(handler, conn);
        }

        @Override
        public void failed(final Exception ex) {
            try {
                try {
                    for (HttpAsyncResponseConsumer<T> responseConsumer: this.responseConsumers) {
                        responseConsumer.failed(ex);
                    }
                } finally {
                    releaseResources();
                }
            } finally {
                this.requestFuture.failed(ex);
            }
        }

        @Override
        public void cancelled() {
            try {
                try {
                    for (HttpAsyncResponseConsumer<T> responseConsumer: this.responseConsumers) {
                        responseConsumer.cancel();
                    }
                } finally {
                    releaseResources();
                }
            } finally {
                this.requestFuture.cancel(true);
            }
        }

        public void releaseResources() {
            for (HttpAsyncRequestProducer requestProducer: this.requestProducers) {
                close(requestProducer);
            }
            for (HttpAsyncResponseConsumer<T> responseConsumer: this.responseConsumers) {
                close(responseConsumer);
            }
        }

    }

    class RequestExecutionCallback<T, E extends PoolEntry<HttpHost, NHttpClientConnection>>
                                               implements FutureCallback<T> {

        private final BasicFuture<T> future;
        private final E poolEntry;
        private final ConnPool<HttpHost, E> connPool;

        RequestExecutionCallback(
                final BasicFuture<T> future,
                final E poolEntry,
                final ConnPool<HttpHost, E> connPool) {
            super();
            this.future = future;
            this.poolEntry = poolEntry;
            this.connPool = connPool;
        }

        @Override
        public void completed(final T result) {
            try {
                this.connPool.release(this.poolEntry, true);
            } finally {
                this.future.completed(result);
            }
        }

        @Override
        public void failed(final Exception ex) {
            try {
                this.connPool.release(this.poolEntry, false);
            } finally {
                this.future.failed(ex);
            }
        }

        @Override
        public void cancelled() {
            try {
                this.connPool.release(this.poolEntry, false);
            } finally {
                this.future.cancel(true);
            }
        }

    }

    /**
     * This method can be used to log I/O exception thrown while closing
     * {@link java.io.Closeable} objects (such as
     * {@link org.apache.http.HttpConnection}}).
     *
     * @param ex I/O exception thrown by {@link java.io.Closeable#close()}
     */
    protected void log(final Exception ex) {
        this.exceptionLogger.log(ex);
    }

    private void close(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ex) {
            log(ex);
        }
    }

}
