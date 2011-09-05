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

import java.io.IOException;
import java.util.concurrent.Future;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.params.HttpParams;
import org.apache.http.pool.ConnPool;
import org.apache.http.pool.PoolEntry;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * @since 4.2
 */
@Immutable
public class HttpAsyncRequestExecutor {

    private final HttpProcessor httppocessor;
    private final ConnectionReuseStrategy reuseStrategy;
    private final HttpParams params;

    public HttpAsyncRequestExecutor(
            final HttpProcessor httppocessor,
            final ConnectionReuseStrategy reuseStrategy,
            final HttpParams params) {
        super();
        this.httppocessor = httppocessor;
        this.reuseStrategy = reuseStrategy;
        this.params = params;
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn,
            final HttpContext context,
            final FutureCallback<T> callback) {
        if (conn == null) {
            throw new IllegalArgumentException("HTTP connection may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        BasicFuture<T> future = new BasicFuture<T>(callback);
        HttpAsyncClientExchangeHandler<T> handler = new HttpAsyncClientExchangeHandlerImpl<T>(
                future, requestProducer, responseConsumer, context,
                this.httppocessor, conn, this.reuseStrategy, this.params);
        conn.getContext().setAttribute(HttpAsyncClientProtocolHandler.HTTP_HANDLER, handler);
        conn.requestOutput();
        return future;
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn,
            final HttpContext context) {
        return execute(requestProducer, responseConsumer, conn, context);
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn) {
        return execute(requestProducer, responseConsumer, conn, new BasicHttpContext());
    }

    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final ConnPool<HttpHost, E> connPool,
            final HttpContext context,
            final FutureCallback<T> callback) {
        if (connPool == null) {
            throw new IllegalArgumentException("HTTP connection pool may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        BasicFuture<T> future = new BasicFuture<T>(callback);
        HttpHost target = requestProducer.getTarget();
        connPool.lease(target, null, new ConnRequestCallback<T, E>(
                future, requestProducer, responseConsumer, connPool, context));
        return future;
    }

    public <T, E extends PoolEntry<HttpHost, NHttpClientConnection>> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final ConnPool<HttpHost, E> connPool,
            final HttpContext context) {
        return execute(requestProducer, responseConsumer, connPool, context, null);
    }

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
            BasicFuture<T> execFuture = new BasicFuture<T>(new RequestExecutionCallback<T, E>(
                    this.requestFuture, result, this.connPool));
            HttpAsyncClientExchangeHandler<T> handler = new HttpAsyncClientExchangeHandlerImpl<T>(
                    execFuture, this.requestProducer, this.responseConsumer, this.context,
                    httppocessor, conn, reuseStrategy, params);
            conn.getContext().setAttribute(HttpAsyncClientProtocolHandler.HTTP_HANDLER, handler);
            conn.requestOutput();
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
                onException(ioex);
            }
            try {
                this.responseConsumer.close();
            } catch (IOException ioex) {
                onException(ioex);
            }
        }

    }

    class RequestExecutionCallback<T, E extends PoolEntry<HttpHost, NHttpClientConnection>> implements FutureCallback<T> {

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

    protected void onException(Exception ex) {
    }

}
