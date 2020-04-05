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

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncFilterChain;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * Factory for {@link AsyncServerExchangeHandler} instances that delegate request processing
 * to a {@link AsyncServerFilterChainElement}.
 *
 * @since 5.0
 */
public final class AsyncServerFilterChainExchangeHandlerFactory implements HandlerFactory<AsyncServerExchangeHandler> {

    private final AsyncServerFilterChainElement filterChain;
    private final Callback<Exception> exceptionCallback;

    public AsyncServerFilterChainExchangeHandlerFactory(final AsyncServerFilterChainElement filterChain,
                                                        final Callback<Exception> exceptionCallback) {
        this.filterChain = Args.notNull(filterChain, "Filter chain");
        this.exceptionCallback = exceptionCallback;
    }

    public AsyncServerFilterChainExchangeHandlerFactory(final AsyncServerFilterChainElement filterChain) {
        this(filterChain, null);
    }

    @Override
    public AsyncServerExchangeHandler create(final HttpRequest request, final HttpContext context) throws HttpException {
        return new AsyncServerExchangeHandler() {

            private final AtomicReference<AsyncDataConsumer> dataConsumerRef = new AtomicReference<>();
            private final AtomicReference<AsyncResponseProducer> responseProducerRef = new AtomicReference<>();

            @Override
            public void handleRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final ResponseChannel responseChannel,
                    final HttpContext context) throws HttpException, IOException {
                dataConsumerRef.set(filterChain.handle(request, entityDetails, context, new AsyncFilterChain.ResponseTrigger() {

                    @Override
                    public void sendInformation(
                            final HttpResponse response) throws HttpException, IOException {
                        responseChannel.sendInformation(response, context);
                    }

                    @Override
                    public void submitResponse(
                            final HttpResponse response,
                            final AsyncEntityProducer entityProducer) throws HttpException, IOException {
                        final AsyncResponseProducer responseProducer = new BasicResponseProducer(response, entityProducer);
                        responseProducerRef.set(responseProducer);
                        responseProducer.sendResponse(responseChannel, context);
                    }

                    @Override
                    public void pushPromise(final HttpRequest promise, final AsyncPushProducer responseProducer) throws HttpException, IOException {
                        responseChannel.pushPromise(promise, responseProducer, context);
                    }

                }));
            }

            @Override
            public void failed(final Exception cause) {
                if (exceptionCallback != null) {
                    exceptionCallback.execute(cause);
                }
                final AsyncResponseProducer handler = responseProducerRef.get();
                if (handler != null) {
                    handler.failed(cause);
                }
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                final AsyncDataConsumer dataConsumer = dataConsumerRef.get();
                if (dataConsumer != null) {
                    dataConsumer.updateCapacity(capacityChannel);
                } else {
                    capacityChannel.update(Integer.MAX_VALUE);
                }
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
                final AsyncDataConsumer dataConsumer = dataConsumerRef.get();
                if (dataConsumer != null) {
                    dataConsumer.consume(src);
                }
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                final AsyncDataConsumer dataConsumer = dataConsumerRef.get();
                if (dataConsumer != null) {
                    dataConsumer.streamEnd(trailers);
                }
            }

            @Override
            public int available() {
                final AsyncResponseProducer responseProducer = responseProducerRef.get();
                Asserts.notNull(responseProducer, "Response producer");
                return responseProducer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                final AsyncResponseProducer responseProducer = responseProducerRef.get();
                Asserts.notNull(responseProducer, "Response producer");
                responseProducer.produce(channel);
            }

            @Override
            public void releaseResources() {
                final AsyncDataConsumer dataConsumer = dataConsumerRef.getAndSet(null);
                if (dataConsumer != null) {
                    dataConsumer.releaseResources();
                }
                final AsyncResponseProducer responseProducer = responseProducerRef.getAndSet(null);
                if (responseProducer != null) {
                    responseProducer.releaseResources();
                }
            }
        };
    }

}
