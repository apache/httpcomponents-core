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
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncServerExchangeHandler} implementation that adds support
 * for the Expect-Continue handshake to an existing
 * {@link AsyncServerExchangeHandler}.
 *
 * @since 5.0
 */
public class BasicAsyncServerExpectationDecorator implements AsyncServerExchangeHandler {

    private final AsyncServerExchangeHandler handler;
    private final Callback<Exception> exceptionCallback;
    private final AtomicReference<AsyncResponseProducer> responseProducerRef;

    public BasicAsyncServerExpectationDecorator(final AsyncServerExchangeHandler handler,
                                                final Callback<Exception> exceptionCallback) {
        this.handler = Args.notNull(handler, "Handler");
        this.exceptionCallback = exceptionCallback;
        this.responseProducerRef = new AtomicReference<>(null);
    }

    public BasicAsyncServerExpectationDecorator(final AsyncServerExchangeHandler handler) {
        this(handler, null);
    }

    protected AsyncResponseProducer verify(
            final HttpRequest request,
            final HttpContext context) throws IOException, HttpException {
        return null;
    }

    @Override
    public final void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context) throws HttpException, IOException {
        if (entityDetails != null) {
            final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
            if (h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue())) {
                final AsyncResponseProducer producer = verify(request, context);
                if (producer != null) {
                    responseProducerRef.set(producer);
                    producer.sendResponse(responseChannel, context);
                    return;
                }
                responseChannel.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE), context);
            }
        }
        handler.handleRequest(request, entityDetails, responseChannel, context);
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            handler.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            handler.consume(src);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            handler.streamEnd(trailers);
        }
    }

    @Override
    public final int available() {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        return responseProducer == null ? handler.available() : responseProducer.available();
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            handler.produce(channel);
        } else {
            responseProducer.produce(channel);
        }
    }

    @Override
    public final void failed(final Exception cause) {
        if (exceptionCallback != null) {
            exceptionCallback.execute(cause);
        }
        final AsyncResponseProducer dataProducer = responseProducerRef.get();
        if (dataProducer == null) {
            handler.failed(cause);
        } else {
            dataProducer.failed(cause);
        }
    }

    @Override
    public final void releaseResources() {
        handler.releaseResources();
        final AsyncResponseProducer dataProducer = responseProducerRef.getAndSet(null);
        if (dataProducer != null) {
            dataProducer.releaseResources();
        }
    }

}
