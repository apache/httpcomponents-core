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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.util.Args;

/**
 * Basic implementation of {@link HttpAsyncClientExchangeHandler} that executes
 * a single HTTP request / response exchange.
 *
 * @param <T> the result type of request execution.
 * @since 4.3
 */
public class BasicAsyncClientExchangeHandler<T> implements HttpAsyncClientExchangeHandler {

    private final HttpAsyncRequestProducer requestProducer;
    private final HttpAsyncResponseConsumer<T> responseConsumer;
    private final BasicFuture<T> future;
    private final HttpContext localContext;
    private final NHttpClientConnection conn;
    private final HttpProcessor httppocessor;
    private final ConnectionReuseStrategy connReuseStrategy;
    private final AtomicBoolean requestSent;
    private final AtomicBoolean keepAlive;
    private final AtomicBoolean closed;

    /**
     * Creates new instance of BasicAsyncRequestExecutionHandler.
     *
     * @param requestProducer the request producer.
     * @param responseConsumer the response consumer.
     * @param callback the future callback invoked when the operation is completed.
     * @param localContext the local execution context.
     * @param conn the actual connection.
     * @param httppocessor the HTTP protocol processor.
     * @param connReuseStrategy the connection re-use strategy.
     */
    public BasicAsyncClientExchangeHandler(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback,
            final HttpContext localContext,
            final NHttpClientConnection conn,
            final HttpProcessor httppocessor,
            final ConnectionReuseStrategy connReuseStrategy) {
        super();
        this.requestProducer = Args.notNull(requestProducer, "Request producer");
        this.responseConsumer = Args.notNull(responseConsumer, "Response consumer");
        this.future = new BasicFuture<T>(callback);
        this.localContext = Args.notNull(localContext, "HTTP context");
        this.conn = Args.notNull(conn, "HTTP connection");
        this.httppocessor = Args.notNull(httppocessor, "HTTP processor");
        this.connReuseStrategy = connReuseStrategy != null ? connReuseStrategy :
            DefaultConnectionReuseStrategy.INSTANCE;
        this.requestSent = new AtomicBoolean(false);
        this.keepAlive = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Creates new instance of BasicAsyncRequestExecutionHandler.
     *
     * @param requestProducer the request producer.
     * @param responseConsumer the response consumer.
     * @param localContext the local execution context.
     * @param conn the actual connection.
     * @param httppocessor the HTTP protocol processor.
     */
    public BasicAsyncClientExchangeHandler(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final HttpContext localContext,
            final NHttpClientConnection conn,
            final HttpProcessor httppocessor) {
        this(requestProducer, responseConsumer, null, localContext, conn, httppocessor, null);
    }

    public Future<T> getFuture() {
        return this.future;
    }

    private void releaseResources() {
        try {
            this.responseConsumer.close();
        } catch (final IOException ex) {
        }
        try {
            this.requestProducer.close();
        } catch (final IOException ex) {
        }
    }

    @Override
    public void close() throws IOException {
        if (this.closed.compareAndSet(false, true)) {
            releaseResources();
            if (!this.future.isDone()) {
                this.future.cancel();
            }
        }
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
        if (isDone()) {
            return null;
        }
        final HttpRequest request = this.requestProducer.generateRequest();
        this.localContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        this.localContext.setAttribute(HttpCoreContext.HTTP_CONNECTION, this.conn);
        this.httppocessor.process(request, this.localContext);
        return request;
    }

    @Override
    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        this.requestProducer.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted() {
        this.requestProducer.requestCompleted(this.localContext);
        this.requestSent.set(true);
    }

    @Override
    public void responseReceived(final HttpResponse response) throws IOException, HttpException {
        this.localContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        this.httppocessor.process(response, this.localContext);
        this.responseConsumer.responseReceived(response);
        this.keepAlive.set(this.connReuseStrategy.keepAlive(response, this.localContext));
    }

    @Override
    public void consumeContent(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        this.responseConsumer.consumeContent(decoder, ioctrl);
    }

    @Override
    public void responseCompleted() throws IOException {
        try {
            if (!this.keepAlive.get()) {
                this.conn.close();
            }
            this.responseConsumer.responseCompleted(this.localContext);
            final T result = this.responseConsumer.getResult();
            final Exception ex = this.responseConsumer.getException();
            if (result != null) {
                this.future.completed(result);
            } else {
                this.future.failed(ex);
            }
            if (this.closed.compareAndSet(false, true)) {
                releaseResources();
            }
        } catch (final RuntimeException ex) {
            failed(ex);
            throw ex;
        }
    }

    @Override
    public void inputTerminated() {
        failed(new ConnectionClosedException("Connection closed"));
    }

    @Override
    public void failed(final Exception ex) {
        if (this.closed.compareAndSet(false, true)) {
            try {
                if (!this.requestSent.get()) {
                    this.requestProducer.failed(ex);
                }
                this.responseConsumer.failed(ex);
            } finally {
                try {
                    this.future.failed(ex);
                } finally {
                    releaseResources();
                }
            }
        }
    }

    @Override
    public boolean cancel() {
        if (this.closed.compareAndSet(false, true)) {
            try {
                try {
                    return this.responseConsumer.cancel();
                } finally {
                    this.future.cancel();
                }
            } finally {
                releaseResources();
            }
        }
        return false;
    }

    @Override
    public boolean isDone() {
        return this.responseConsumer.isDone();
    }

}
