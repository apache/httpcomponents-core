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
import java.util.concurrent.Future;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
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
 * <tt>HttpAsyncRequester</tt> is a utility class that can be used
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

    private final HttpProcessor httppocessor;
    private final ConnectionReuseStrategy reuseStrategy;

    /**
     * @deprecated (4.3) use {@link HttpAsyncRequester#HttpAsyncRequester(HttpProcessor,
     *   ConnectionReuseStrategy)}
     */
    public HttpAsyncRequester(
            final HttpProcessor httppocessor,
            final ConnectionReuseStrategy reuseStrategy,
            final HttpParams params) {
        this(httppocessor, reuseStrategy);
    }

    /**
     * Creates new instance of HttpAsyncRequester.
     *
     * @param httppocessor the HTTP protocol processor.
     * @param reuseStrategy the connection re-use strategy. If <code>null</code>
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     *
     * @since 4.3
     */
    public HttpAsyncRequester(
            final HttpProcessor httppocessor,
            final ConnectionReuseStrategy reuseStrategy) {
        super();
        this.httppocessor = httppocessor;
        this.reuseStrategy = reuseStrategy != null ? reuseStrategy :
            DefaultConnectionReuseStrategy.INSTANCE;
    }

    /**
     * Creates new instance of HttpAsyncRequester.
     *
     * @param httppocessor the HTTP protocol processor.
     *
     * @since 4.3
     */
    public HttpAsyncRequester(final HttpProcessor httppocessor) {
        this(httppocessor, null);
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callaback.
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
        BasicAsyncRequestExecutionHandler<T> handler = new BasicAsyncRequestExecutionHandler<T>(
                requestProducer, responseConsumer, callback, context,
                this.httppocessor, this.reuseStrategy);
        initExection(handler, conn);
        return handler.getFuture();
    }

    private <T> void initExection(
            final HttpAsyncRequestExecutionHandler<T> handler, final NHttpClientConnection conn) {
        conn.getContext().setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, handler);
        conn.requestOutput();
        if (!conn.isOpen()) {
            handler.failed(new ConnectionClosedException("Connection closed"));
            try {
                handler.close();
            } catch (IOException ex) {
                log(ex);
            }
        }
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callaback.
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
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callaback.
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
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callaback.
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
        BasicFuture<T> future = new BasicFuture<T>(callback);
        HttpHost target = requestProducer.getTarget();
        connPool.lease(target, null, new ConnRequestCallback<T, E>(
                future, requestProducer, responseConsumer, connPool, context));
        return future;
    }

    /**
     * Initiates asynchronous HTTP request execution. This method automatically releases
     * the given pool entry once request execution is completed (successfully or unsuccessfully).
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callaback.
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
        BasicFuture<T> future = new BasicFuture<T>(callback);
        NHttpClientConnection conn = poolEntry.getConnection();
        BasicAsyncRequestExecutionHandler<T> handler = new BasicAsyncRequestExecutionHandler<T>(
                requestProducer, responseConsumer,
                new RequestExecutionCallback<T, E>(future, poolEntry, connPool),
                context,
                this.httppocessor, this.reuseStrategy);
        initExection(handler, conn);
        return future;
    }

    /**
     * Initiates asynchronous HTTP request execution.
     *
     * @param <T> the result type of request execution.
     * @param <E> the connection pool entry type.
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callaback.
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
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callaback.
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

        public void completed(final E result) {
            if (this.requestFuture.isDone()) {
                this.connPool.release(result, true);
                return;
            }
            NHttpClientConnection conn = result.getConnection();
            BasicAsyncRequestExecutionHandler<T> handler = new BasicAsyncRequestExecutionHandler<T>(
                    this.requestProducer, this.responseConsumer,
                    new RequestExecutionCallback<T, E>(this.requestFuture, result, this.connPool),
                    this.context, httppocessor, reuseStrategy);
            initExection(handler, conn);
        }

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
            try {
                this.requestProducer.close();
            } catch (IOException ioex) {
                log(ioex);
            }
            try {
                this.responseConsumer.close();
            } catch (IOException ioex) {
                log(ioex);
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

        public void completed(final T result) {
            try {
                this.connPool.release(this.poolEntry, true);
            } finally {
                this.future.completed(result);
            }
        }

        public void failed(final Exception ex) {
            try {
                this.connPool.release(this.poolEntry, false);
            } finally {
                this.future.failed(ex);
            }
        }

        public void cancelled() {
            try {
                this.connPool.release(this.poolEntry, false);
            } finally {
                this.future.cancel(true);
            }
        }

    }

    /**
     * This method can be used to log I/O exception thrown while closing {@link Closeable}
     * objects (such as {@link HttpConnection}}).
     *
     * @param ex I/O exception thrown by {@link Closeable#close()}
     */
    protected void log(Exception ex) {
    }

}
