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
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.HttpContextAware;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.BasicResponseProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Asserts;

/**
 * @since 5.0
 */
public abstract class AbstractServerExchangeHandler<T> implements HttpContextAware, AsyncServerExchangeHandler {

    private final AtomicReference<AsyncRequestConsumer<T>> requestConsumerRef;
    private final AtomicReference<AsyncResponseProducer> responseProducerRef;

    private volatile HttpContext context;
    private volatile boolean expectationFailed;

    public AbstractServerExchangeHandler() {
        this.requestConsumerRef = new AtomicReference<>(null);
        this.responseProducerRef = new AtomicReference<>(null);
    }

    protected AsyncResponseProducer verify(
            final HttpRequest request,
            final HttpContext context) throws IOException, HttpException {
        return null;
    }

    protected abstract AsyncRequestConsumer<T> supplyConsumer(
            HttpRequest request,
            HttpContext context) throws HttpException;

    protected abstract void handle(
            T requestMessage,
            ResponseTrigger responseTrigger,
            HttpContext context) throws HttpException, IOException;

    @Override
    public void setContext(final HttpContext context) {
        this.context = context;
    }

    @Override
    public final void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel) throws HttpException, IOException {

        final AsyncRequestConsumer<T> requestConsumer = supplyConsumer(request, context);
        if (requestConsumer == null) {
            throw new HttpException("Unable to handle request");
        }
        requestConsumerRef.set(requestConsumer);

        if (entityDetails != null) {
            final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
            if (h != null && "100-continue".equalsIgnoreCase(h.getValue())) {
                final AsyncResponseProducer producer = verify(request, context);
                if (producer != null) {
                    expectationFailed = true;
                    responseProducerRef.set(producer);
                    responseChannel.sendResponse(producer.produceResponse(), producer.getEntityDetails());
                    return;
                } else {
                    responseChannel.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE));
                }
            }
        }
        final ResponseTrigger responseTrigger = new ResponseTrigger() {

            @Override
            public void submitResponse(
                    final AsyncResponseProducer producer) throws HttpException, IOException {
                if (responseProducerRef.compareAndSet(null, producer)) {
                    responseChannel.sendResponse(producer.produceResponse(), producer.getEntityDetails());
                }
            }

            @Override
            public void pushPromise(
                    final HttpRequest promise, final AsyncPushProducer pushProducer) throws HttpException, IOException {
                responseChannel.pushPromise(promise, pushProducer);
            }

            @Override
            public String toString() {
                return "Response trigger: " + responseChannel;
            }

        };
        requestConsumer.consumeRequest(request, entityDetails, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                try {
                    handle(result, responseTrigger, context);
                } catch (final HttpException ex) {
                    try {
                        responseTrigger.submitResponse(
                                new BasicResponseProducer(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getMessage()));
                    } catch (HttpException | IOException ex2) {
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
        if (!expectationFailed) {
            final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.get();
            Asserts.notNull(requestConsumer, "Data consumer");
            requestConsumer.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public final int consume(final ByteBuffer src) throws IOException {
        if (!expectationFailed) {
            final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.get();
            Asserts.notNull(requestConsumer, "Data consumer");
            return requestConsumer.consume(src);
        } else {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (!expectationFailed) {
            final AsyncRequestConsumer<T> requestConsumer = requestConsumerRef.get();
            Asserts.notNull(requestConsumer, "Data consumer");
            requestConsumer.streamEnd(trailers);
        }
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
