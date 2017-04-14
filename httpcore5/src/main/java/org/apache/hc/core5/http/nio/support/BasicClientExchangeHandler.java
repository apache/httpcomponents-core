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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public class BasicClientExchangeHandler<T> implements AsyncClientExchangeHandler {

    private final AsyncRequestProducer requestProducer;
    private final AsyncResponseConsumer<T> responseConsumer;
    private final FutureCallback<T> resultCallback;
    private final AtomicBoolean outputTerminated;

    public BasicClientExchangeHandler(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> resultCallback) {
        this.requestProducer = Args.notNull(requestProducer, "Request producer");
        this.responseConsumer = Args.notNull(responseConsumer, "Response consumer");
        this.resultCallback = resultCallback;
        this.outputTerminated = new AtomicBoolean(false);
    }

    @Override
    public void produceRequest(final RequestChannel requestChannel) throws HttpException, IOException {
        final HttpRequest request = requestProducer.produceRequest();
        final EntityDetails entityDetails = requestProducer.getEntityDetails();
        requestChannel.sendRequest(request, entityDetails);
    }

    @Override
    public int available() {
        return requestProducer.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (outputTerminated.get()) {
            channel.endStream();
            return;
        }
        requestProducer.produce(channel);
    }

    @Override
    public void consumeInformation(final HttpResponse response) throws HttpException, IOException {
    }

    @Override
    public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
        if (response.getCode() >= HttpStatus.SC_CLIENT_ERROR) {
            outputTerminated.set(true);
            requestProducer.releaseResources();
        }
        responseConsumer.consumeResponse(response, entityDetails, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                releaseResources();
                if (resultCallback != null) {
                    resultCallback.completed(result);
                }
            }

            @Override
            public void failed(final Exception ex) {
                releaseResources();
                if (resultCallback != null) {
                    resultCallback.failed(ex);
                }
            }

            @Override
            public void cancelled() {
                releaseResources();
                if (resultCallback != null) {
                    resultCallback.cancelled();
                }
            }

        });
    }

    @Override
    public void cancel() {
        releaseResources();
        if (resultCallback != null) {
            resultCallback.cancelled();
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        responseConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public int consume(final ByteBuffer src) throws IOException {
        return responseConsumer.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        responseConsumer.streamEnd(trailers);
    }

    @Override
    public final void failed(final Exception cause) {
        try {
            requestProducer.failed(cause);
            responseConsumer.failed(cause);
        } finally {
            releaseResources();
            if (resultCallback != null) {
                resultCallback.failed(cause);
            }
        }
    }

    @Override
    public final void releaseResources() {
        requestProducer.releaseResources();
        responseConsumer.releaseResources();
    }

}
