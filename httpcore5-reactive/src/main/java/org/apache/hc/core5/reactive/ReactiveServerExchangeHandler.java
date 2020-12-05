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
package org.apache.hc.core5.reactive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.reactivestreams.Publisher;

/**
 * An implementation of {@link AsyncServerExchangeHandler} designed to work with reactive streams.
 *
 * @since 5.0
 */
public final class ReactiveServerExchangeHandler implements AsyncServerExchangeHandler {

    private final ReactiveRequestProcessor requestProcessor;
    private final AtomicReference<ReactiveDataProducer> responseProducer = new AtomicReference<>(null);
    private final ReactiveDataConsumer requestConsumer;
    private volatile DataStreamChannel channel;

    /**
     * Creates a {@code ReactiveServerExchangeHandler}.
     *
     * @param requestProcessor the {@link ReactiveRequestProcessor} instance to
     *                         invoke when the request is ready to be handled.
     */
    public ReactiveServerExchangeHandler(final ReactiveRequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
        this.requestConsumer = new ReactiveDataConsumer();
    }

    @Override
    public void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context
    ) throws HttpException, IOException {
        final Callback<Publisher<ByteBuffer>> callback = result -> {
            final ReactiveDataProducer producer = new ReactiveDataProducer(result);
            if (channel != null) {
                producer.setChannel(channel);
            }
            responseProducer.set(producer);
            result.subscribe(producer);
        };
        requestProcessor.processRequest(request, entityDetails, responseChannel, context, requestConsumer, callback);
    }

    @Override
    public void failed(final Exception cause) {
        requestConsumer.failed(cause);
        final ReactiveDataProducer p = responseProducer.get();
        if (p != null) {
            p.onError(cause);
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        requestConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        requestConsumer.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        requestConsumer.streamEnd(trailers);
    }

    @Override
    public int available() {
        final ReactiveDataProducer p = responseProducer.get();
        if (p == null) {
            return 0;
        } else {
            return p.available();
        }
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        this.channel = channel;
        final ReactiveDataProducer p = responseProducer.get();
        if (p != null) {
            p.produce(channel);
        }
    }

    @Override
    public void releaseResources() {
        final ReactiveDataProducer p = responseProducer.get();
        if (p != null) {
            p.releaseResources();
        }
        requestConsumer.releaseResources();
    }
}
