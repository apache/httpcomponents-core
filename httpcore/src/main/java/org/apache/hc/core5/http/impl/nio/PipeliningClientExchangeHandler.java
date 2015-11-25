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

package org.apache.hc.core5.http.impl.nio;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HttpAsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.HttpAsyncRequestProducer;
import org.apache.hc.core5.http.nio.HttpAsyncResponseConsumer;
import org.apache.hc.core5.http.nio.IOControl;
import org.apache.hc.core5.http.nio.NHttpClientConnection;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * Pipelining implementation of {@link HttpAsyncClientExchangeHandler}
 * that executes a series of pipelined HTTP requests.
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

    private final AtomicReference<HttpAsyncRequestProducer> requestProducerRef;
    private final AtomicReference<HttpAsyncResponseConsumer<T>> responseConsumerRef;
    private final AtomicBoolean closed;

    /**
     * Creates new instance of {@code PipeliningClientExchangeHandler}.
     *
     * @param requestProducers the request producers.
     * @param responseConsumers the response consumers.
     * @param callback the future callback invoked when the operation is completed.
     * @param localContext the local execution context.
     * @param conn the actual connection.
     * @param httppocessor the HTTP protocol processor.
     */
    public PipeliningClientExchangeHandler(
            final List<? extends HttpAsyncRequestProducer> requestProducers,
            final List<? extends HttpAsyncResponseConsumer<T>> responseConsumers,
            final FutureCallback<List<T>> callback,
            final HttpContext localContext,
            final NHttpClientConnection conn,
            final HttpProcessor httppocessor) {
        super();
        Args.notEmpty(requestProducers, "Request producer list");
        Args.notEmpty(responseConsumers, "Response consumer list");
        Args.check(requestProducers.size() == responseConsumers.size(),
                "Number of request producers does not match that of response consumers");
        this.requestProducerQueue = new ConcurrentLinkedQueue<>(requestProducers);
        this.responseConsumerQueue = new ConcurrentLinkedQueue<>(responseConsumers);
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.resultQueue = new ConcurrentLinkedQueue<>();
        this.future = new BasicFuture<>(callback);
        this.localContext = Args.notNull(localContext, "HTTP context");
        this.conn = Args.notNull(conn, "HTTP connection");
        this.httppocessor = Args.notNull(httppocessor, "HTTP processor");
        this.localContext.setAttribute(HttpCoreContext.HTTP_CONNECTION, this.conn);
        this.requestProducerRef = new AtomicReference<>(null);
        this.responseConsumerRef = new AtomicReference<>(null);
        this.closed = new AtomicBoolean(false);
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
        this(requestProducers, responseConsumers, null, localContext, conn, httppocessor);
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
        closeQuietly(this.requestProducerRef.getAndSet(null));
        closeQuietly(this.responseConsumerRef.getAndSet(null));
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
        if (this.closed.compareAndSet(false, true)) {
            releaseResources();
            if (!this.future.isDone()) {
                this.future.cancel();
            }
        }
    }

    @Override
    public HttpContext getContext() {
        return localContext;
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
        Asserts.check(this.requestProducerRef.get() == null, "Inconsistent state: request producer is not null");
        final HttpAsyncRequestProducer requestProducer = this.requestProducerQueue.poll();
        if (requestProducer == null) {
            return null;
        }
        this.requestProducerRef.set(requestProducer);
        final HttpRequest request = requestProducer.generateRequest();
        this.httppocessor.process(request, this.localContext);
        this.requestQueue.add(request);
        return request;
    }

    @Override
    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        final HttpAsyncRequestProducer requestProducer = this.requestProducerRef.get();
        Asserts.check(requestProducer != null, "Inconsistent state: request producer is null");
        requestProducer.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted() {
        final HttpAsyncRequestProducer requestProducer = this.requestProducerRef.getAndSet(null);
        Asserts.check(requestProducer != null, "Inconsistent state: request producer is null");
        requestProducer.requestCompleted(this.localContext);
    }

    @Override
    public void responseReceived(final HttpResponse response) throws IOException, HttpException {
        Asserts.check(this.responseConsumerRef.get() == null, "Inconsistent state: response consumer is not null");

        final HttpAsyncResponseConsumer<T> responseConsumer = this.responseConsumerQueue.poll();
        Asserts.check(responseConsumer != null, "Inconsistent state: response consumer queue is empty");
        this.responseConsumerRef.set(responseConsumer);

        final HttpRequest request = this.requestQueue.poll();
        Asserts.check(request != null, "Inconsistent state: request queue is empty");

        this.localContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        this.localContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        this.httppocessor.process(response, this.localContext);

        responseConsumer.responseReceived(response);
    }

    @Override
    public void consumeContent(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        final HttpAsyncResponseConsumer<T> responseConsumer = this.responseConsumerRef.get();
        Asserts.check(responseConsumer != null, "Inconsistent state: response consumer is null");
        responseConsumer.consumeContent(decoder, ioctrl);
    }

    @Override
    public void responseCompleted() throws IOException {
        final HttpAsyncResponseConsumer<T> responseConsumer = this.responseConsumerRef.getAndSet(null);
        Asserts.check(responseConsumer != null, "Inconsistent state: response consumer is null");
        try {
            responseConsumer.responseCompleted(this.localContext);
            final T result = responseConsumer.getResult();
            final Exception ex = responseConsumer.getException();
            if (result != null) {
                this.resultQueue.add(result);
            } else {
                this.future.failed(ex);
                this.conn.shutdown();
            }
            if (!conn.isOpen()) {
                if (this.closed.compareAndSet(false, true)) {
                    releaseResources();
                }
            }
            if (!this.future.isDone() && this.responseConsumerQueue.isEmpty()) {
                this.future.completed(new ArrayList<>(this.resultQueue));
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
        if (this.closed.compareAndSet(false, true)) {
            try {
                final HttpAsyncRequestProducer requestProducer = this.requestProducerRef.get();
                if (requestProducer != null) {
                    requestProducer.failed(ex);
                }
                final HttpAsyncResponseConsumer<T> responseConsumer = this.responseConsumerRef.get();
                if (responseConsumer != null) {
                    responseConsumer.failed(ex);
                }
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
                    final HttpAsyncResponseConsumer<T> responseConsumer = this.responseConsumerRef.get();
                    return responseConsumer != null && responseConsumer.cancel();
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
        return this.future.isDone();
    }

}
