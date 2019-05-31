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
package org.apache.hc.core5.http.nio.support;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Asserts;

/**
 * Abstract server side message exchange handler.
 *
 * @since 5.0
 */
public abstract class AbstractServerExchangeHandler<T> implements AsyncServerExchangeHandler {

    private final AtomicReference<AsyncRequestConsumer<T>> requestConsumerRef;
    private final AtomicReference<AsyncResponseProducer> responseProducerRef;

    public AbstractServerExchangeHandler() {
        this.requestConsumerRef = new AtomicReference<>(null);
        this.responseProducerRef = new AtomicReference<>(null);
    }

    /**
     * Triggered to supply a request consumer to process the incoming request.
     * @param request the request message.
     * @param entityDetails the request entity details.
     * @param context the actual execution context.
     * @return the request consumer.
     */
    protected abstract AsyncRequestConsumer<T> supplyConsumer(
            HttpRequest request,
            EntityDetails entityDetails,
            HttpContext context) throws HttpException;

    /**
     * Triggered to handles the request object produced by the {@link AsyncRequestConsumer} returned
     * from the {@link #supplyConsumer(HttpRequest, EntityDetails, HttpContext)} method. The handler
     * can choose to send response messages immediately inside the call or asynchronously
     * at some later point.
     *
     * @param requestMessage the request message.
     * @param responseTrigger the response trigger.
     * @param context the actual execution context.
     */
    protected abstract void handle(
            T requestMessage,
            AsyncServerRequestHandler.ResponseTrigger responseTrigger,
            HttpContext context) throws HttpException, IOException;

    @Override
    public final void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context) throws HttpException, IOException {

        final AsyncRequestConsumer<T> requestConsumer = supplyConsumer(request, entityDetails, context);
        if (requestConsumer == null) {
            throw new HttpException("Unable to handle request");
        }
        requestConsumerRef.set(requestConsumer);
        final AsyncServerRequestHandler.ResponseTrigger responseTrigger = new AsyncServerRequestHandler.ResponseTrigger() {

            @Override
            public void sendInformation(final HttpResponse response, final HttpContext httpContext) throws HttpException, IOException {
                responseChannel.sendInformation(response, httpContext);
            }

            @Override
            public void submitResponse(
                    final AsyncResponseProducer producer, final HttpContext httpContext) throws HttpException, IOException {
                if (responseProducerRef.compareAndSet(null, producer)) {
                    producer.sendResponse(responseChannel, httpContext);
                }
            }

            @Override
            public void pushPromise(
                    final HttpRequest promise, final HttpContext httpContext, final AsyncPushProducer pushProducer) throws HttpException, IOException {
                responseChannel.pushPromise(promise, pushProducer, httpContext);
            }

            @Override
            public String toString() {
                return "Response trigger: " + responseChannel;
            }

        };
        requestConsumer.consumeRequest(request, entityDetails, context, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                try {
                    handle(result, responseTrigger, context);
                } catch (final HttpException ex) {
                    try {
                        responseTrigger.submitResponse(
                                AsyncResponseBuilder.create(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                                        .setEntity(ex.getMessage())
                                        .build(),
                                context);
                    } catch (final HttpException | IOException ex2) {
                        failed(ex2);
                    }
                } catch (final IOException ex) {
                    failed(ex);
                }
            }

            @Override
            public void failed(final Exception ex) {
                AbstractServerExchangeHandler.this.failed(ex);
            }

            @Override
            public void cancelled() {
                releaseResources();
            }

        });

    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.get();
        Asserts.notNull(requestConsumer, "Data consumer");
        requestConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.get();
        Asserts.notNull(requestConsumer, "Data consumer");
        requestConsumer.consume(src);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.get();
        Asserts.notNull(requestConsumer, "Data consumer");
        requestConsumer.streamEnd(trailers);
    }

    @Override
    public final int available() {
        final AsyncResponseProducer dataProducer = responseProducerRef.get();
        return dataProducer != null ? dataProducer.available() : 0;
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        final AsyncResponseProducer dataProducer = responseProducerRef.get();
        Asserts.notNull(dataProducer, "Data producer");
        dataProducer.produce(channel);
    }

    @Override
    public final void failed(final Exception cause) {
        try {
            final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.get();
            if (requestConsumer != null) {
                requestConsumer.failed(cause);
            }
            final AsyncResponseProducer dataProducer = responseProducerRef.get();
            if (dataProducer != null) {
                dataProducer.failed(cause);
            }
        } finally {
            releaseResources();
        }
    }

    @Override
    public final void releaseResources() {
        final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.getAndSet(null);
        if (requestConsumer != null) {
            requestConsumer.releaseResources();
        }
        final AsyncResponseProducer dataProducer = responseProducerRef.getAndSet(null);
        if (dataProducer != null) {
            dataProducer.releaseResources();
        }
    }

}
