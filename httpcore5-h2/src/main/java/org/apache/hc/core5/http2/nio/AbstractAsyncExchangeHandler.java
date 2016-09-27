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
package org.apache.hc.core5.http2.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http2.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * @since 5.0
 */
public abstract class AbstractAsyncExchangeHandler<T> implements AsyncExchangeHandler {

    private final AsyncRequestConsumer<Message<HttpRequest, T>> requestConsumer;
    private final AtomicReference<AsyncResponseProducer> responseProducer;
    private final AtomicBoolean dataStarted;
    private final AtomicReference<Exception> exception;

    public AbstractAsyncExchangeHandler(final AsyncRequestConsumer<Message<HttpRequest, T>> requestConsumer) {
        this.requestConsumer = Args.notNull(requestConsumer, "Request consumer");
        this.responseProducer = new AtomicReference<>(null);
        this.dataStarted = new AtomicBoolean(false);
        this.exception = new AtomicReference<>(null);
    }

    public AbstractAsyncExchangeHandler(final AsyncEntityConsumer<T> requestEntityConsumer) {
        this(new BasicRequestConsumer<>(requestEntityConsumer));
    }

    public Exception getException() {
        return exception.get();
    }

    protected abstract void handle(Message<HttpRequest, T> request, AsyncResponseTrigger responseTrigger) throws IOException, HttpException;

    @Override
    public void verify(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ExpectationChannel expectationChannel) throws HttpException, IOException {
        expectationChannel.sendContinue();
    }

    @Override
    public final void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel) throws HttpException, IOException {

        final AsyncResponseTrigger responseTrigger = new AsyncResponseTrigger() {

            @Override
            public void submitResponse(
                    final AsyncResponseProducer producer) throws HttpException, IOException {
                try {
                    if (responseProducer.compareAndSet(null, producer)) {
                        responseChannel.sendResponse(producer.produceResponse(), producer.getEntityDetails());
                    }
                } finally {
                    requestConsumer.releaseResources();
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
        requestConsumer.consumeRequest(request, entityDetails, new FutureCallback<Message<HttpRequest, T>>() {

            @Override
            public void completed(final Message<HttpRequest, T> message) {
                try {
                    handle(message, responseTrigger);
                } catch (HttpException ex) {
                    try {
                        responseTrigger.submitResponse(new BasicResponseProducer(
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                new StringAsyncEntityProducer(ex.getMessage(), ContentType.TEXT_PLAIN)));
                    } catch (HttpException | IOException ex2) {
                        failed(ex2);
                    }
                } catch (IOException ex) {
                    failed(ex);
                }
            }

            @Override
            public void failed(final Exception ex) {
                exception.compareAndSet(null, ex);
                releaseResources();
            }

            @Override
            public void cancelled() {
                releaseResources();
            }

        });

    }

    @Override
    public int capacity() {
        return requestConsumer.capacity();
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        requestConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        requestConsumer.consume(src);
    }

    @Override
    public final void streamEnd(final List<Header> trailers) throws HttpException, IOException {
        requestConsumer.streamEnd(trailers);
    }

    @Override
    public final int available() {
        final AsyncResponseProducer dataProducer = responseProducer.get();
        return dataProducer != null ? dataProducer.available() : 0;
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        final AsyncResponseProducer dataProducer = responseProducer.get();
        Asserts.notNull(dataProducer, "Data producer");
        if (dataStarted.compareAndSet(false, true)) {
            dataProducer.dataStart(channel);
        }
        dataProducer.produce(channel);
    }

    @Override
    public final void failed(final Exception cause) {
        if (exception.compareAndSet(null, cause)) {
            final AsyncResponseProducer dataProducer = responseProducer.get();
            if (dataProducer != null) {
                dataProducer.failed(cause);
            }
        }
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        requestConsumer.releaseResources();
        final AsyncResponseProducer dataProducer = responseProducer.get();
        if (dataProducer != null) {
            dataProducer.releaseResources();
        }
    }

}
