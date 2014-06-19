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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

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
import org.apache.http.util.Asserts;

/**
 * Basic implementation of {@link org.apache.http.nio.protocol.HttpAsyncClientExchangeHandler} that executes
 * a single HTTP request / response exchange.
 *
 * @param <T> the result type of request execution.
 * @since 4.4
 */
@Pipelined()
public class PipeliningClientExchangeHandler<T> implements HttpAsyncClientExchangeHandler {

    private final Queue<HttpAsyncRequestProducer> requestProducerQueue;
    private final Queue<HttpAsyncResponseConsumer<T>> responseConsumerQueue;
    private final Queue<HttpRequest> requestQueue;
    private final Queue<T> resultQueue;
    private final BasicFuture<List<T>> future;
    private final HttpContext localContext;
    private final NHttpClientConnection conn;
    private final HttpProcessor httppocessor;
    private final ConnectionReuseStrategy connReuseStrategy;

    private volatile HttpAsyncRequestProducer requestProducer;
    private volatile HttpAsyncResponseConsumer<T> responseConsumer;
    private volatile boolean keepAlive;
    private volatile boolean done;

    /**
     * Creates new instance of {@code PipeliningClientExchangeHandler}.
     *
     * @param requestProducers the request producers.
     * @param responseConsumers the response consumers.
     * @param callback the future callback invoked when the operation is completed.
     * @param localContext the local execution context.
     * @param conn the actual connection.
     * @param httppocessor the HTTP protocol processor.
     * @param connReuseStrategy the connection re-use strategy.
     */
    public PipeliningClientExchangeHandler(
            final List<? extends HttpAsyncRequestProducer> requestProducers,
            final List<? extends HttpAsyncResponseConsumer<T>> responseConsumers,
            final FutureCallback<List<T>> callback,
            final HttpContext localContext,
            final NHttpClientConnection conn,
            final HttpProcessor httppocessor,
            final ConnectionReuseStrategy connReuseStrategy) {
        super();
        Args.notEmpty(requestProducers, "Request producer list");
        Args.notEmpty(responseConsumers, "Response consumer list");
        Args.check(requestProducers.size() == responseConsumers.size(),
                "Number of request producers does not match that of response consumers");
        this.requestProducerQueue = new ConcurrentLinkedQueue<HttpAsyncRequestProducer>(requestProducers);
        this.responseConsumerQueue = new ConcurrentLinkedQueue<HttpAsyncResponseConsumer<T>>(responseConsumers);
        this.requestQueue = new ConcurrentLinkedQueue<HttpRequest>();
        this.resultQueue = new ConcurrentLinkedQueue<T>();
        this.future = new BasicFuture<List<T>>(callback);
        this.localContext = Args.notNull(localContext, "HTTP context");
        this.conn = Args.notNull(conn, "HTTP connection");
        this.httppocessor = Args.notNull(httppocessor, "HTTP processor");
        this.connReuseStrategy = connReuseStrategy != null ? connReuseStrategy :
            DefaultConnectionReuseStrategy.INSTANCE;
        this.localContext.setAttribute(HttpCoreContext.HTTP_CONNECTION, this.conn);
    }

    /**
     * Creates new instance of {@code PipeliningClientExchangeHandler}.
     *
     * @param requestProducers the request producers.
     * @param responseConsumers the response consumers.
     * @param localContext the local execution context.
     * @param conn the actual connection.
     * @param httppocessor the HTTP protocol processor.
     */
    public PipeliningClientExchangeHandler(
            final List<? extends HttpAsyncRequestProducer> requestProducers,
            final List<? extends HttpAsyncResponseConsumer<T>> responseConsumers,
            final HttpContext localContext,
            final NHttpClientConnection conn,
            final HttpProcessor httppocessor) {
        this(requestProducers, responseConsumers, null, localContext, conn, httppocessor, null);
    }

    public Future<List<T>> getFuture() {
        return this.future;
    }

    private static void closeQuietly(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ex) {
            }
        }
    }

    private void releaseResources() {
        closeQuietly(this.requestProducer);
        this.requestProducer = null;
        closeQuietly(this.responseConsumer);
        this.responseConsumer = null;
        while (!this.requestProducerQueue.isEmpty()) {
            closeQuietly(this.requestProducerQueue.remove());
        }
        while (!this.responseConsumerQueue.isEmpty()) {
            closeQuietly(this.responseConsumerQueue.remove());
        }
        this.requestQueue.clear();
        this.resultQueue.clear();
    }

    @Override
    public void close() throws IOException {
        releaseResources();
        if (!this.future.isDone()) {
            this.future.cancel();
        }
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
        Asserts.check(this.requestProducer == null, "Inconsistent state: request producer is not null");
        this.requestProducer = this.requestProducerQueue.poll();
        if (this.requestProducer == null) {
            return null;
        }
        final HttpRequest request = this.requestProducer.generateRequest();
        this.httppocessor.process(request, this.localContext);
        this.requestQueue.add(request);
        return request;
    }

    @Override
    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        Asserts.check(this.requestProducer != null, "Inconsistent state: request producer is null");
        this.requestProducer.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted() {
        Asserts.check(this.requestProducer != null, "Inconsistent state: request producer is null");
        this.requestProducer.requestCompleted(this.localContext);
        this.requestProducer = null;
    }

    @Override
    public void responseReceived(final HttpResponse response) throws IOException, HttpException {
        Asserts.check(this.responseConsumer == null, "Inconsistent state: response consumer is not null");

        this.responseConsumer = this.responseConsumerQueue.poll();
        Asserts.check(this.responseConsumer != null, "Inconsistent state: response consumer queue is empty");

        final HttpRequest request = this.requestQueue.poll();
        Asserts.check(request != null, "Inconsistent state: request queue is empty");

        this.localContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        this.localContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        this.httppocessor.process(response, this.localContext);

        this.responseConsumer.responseReceived(response);
        this.keepAlive = this.connReuseStrategy.keepAlive(response, this.localContext);
    }

    @Override
    public void consumeContent(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        Asserts.check(this.responseConsumer != null, "Inconsistent state: response consumer is null");
        this.responseConsumer.consumeContent(decoder, ioctrl);
    }

    @Override
    public void responseCompleted() throws IOException {
        Asserts.check(this.responseConsumer != null, "Inconsistent state: response consumer is null");
        try {
            if (!this.keepAlive) {
                this.conn.close();
            }
            this.responseConsumer.responseCompleted(this.localContext);
            final T result = this.responseConsumer.getResult();
            final Exception ex = this.responseConsumer.getException();
            this.responseConsumer = null;
            if (result != null) {
                this.resultQueue.add(result);
            } else {
                this.future.failed(ex);
                this.conn.shutdown();
            }
            if (!conn.isOpen()) {
                this.done = true;
                releaseResources();
            }
            if (!this.future.isDone() && this.responseConsumerQueue.isEmpty()) {
                this.future.completed(new ArrayList<T>(this.resultQueue));
                this.resultQueue.clear();
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
        this.done = true;
        try {
            if (this.requestProducer != null) {
                this.requestProducer.failed(ex);
            }
            if (this.responseConsumer != null) {
                this.responseConsumer.failed(ex);
            }
        } finally {
            try {
                this.future.failed(ex);
            } finally {
                releaseResources();
            }
        }
    }

    @Override
    public boolean cancel() {
        this.done = true;
        try {
            final boolean cancelled = this.responseConsumer.cancel();
            this.future.cancel();
            releaseResources();
            return cancelled;
        } catch (final RuntimeException ex) {
            failed(ex);
            throw ex;
        }
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

}
