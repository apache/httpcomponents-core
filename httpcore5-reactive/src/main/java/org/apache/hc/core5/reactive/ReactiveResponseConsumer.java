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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.reactivestreams.Publisher;

/**
 * An {@link AsyncResponseConsumer} that publishes the response body through
 * a {@link Publisher}, as defined by the Reactive Streams specification. The
 * response is represented as a {@link Message} consisting of a {@link
 * HttpResponse} representing the headers and a {@link Publisher} representing
 * the response body as an asynchronous stream of {@link ByteBuffer} instances.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class ReactiveResponseConsumer implements AsyncResponseConsumer<Void> {

    private final ReactiveDataConsumer reactiveDataConsumer = new ReactiveDataConsumer();
    private final List<Header> trailers = Collections.synchronizedList(new ArrayList<>());
    private final BasicFuture<Message<HttpResponse, Publisher<ByteBuffer>>> responseFuture;

    private volatile BasicFuture<Void> responseCompletion;
    private volatile HttpResponse informationResponse;
    private volatile EntityDetails entityDetails;

    /**
     * Creates a {@code ReactiveResponseConsumer}.
     */
    public ReactiveResponseConsumer() {
        this.responseFuture = new BasicFuture<>(null);
    }

    /**
     * Creates a {@code ReactiveResponseConsumer} that will call back the supplied {@link FutureCallback} with a
     * streamable response.
     *
     * @param responseCallback the callback to invoke when the response is available for consumption.
     */
    public ReactiveResponseConsumer(final FutureCallback<Message<HttpResponse, Publisher<ByteBuffer>>> responseCallback) {
        this.responseFuture = new BasicFuture<>(Args.notNull(responseCallback, "responseCallback"));
    }

    public Future<Message<HttpResponse, Publisher<ByteBuffer>>> getResponseFuture() {
        return responseFuture;
    }

    /**
     * Returns the intermediate (1xx) HTTP response if one was received.
     *
     * @return the information response, or {@code null} if none.
     */
    public HttpResponse getInformationResponse() {
        return informationResponse;
    }

    /**
     * Returns the response entity details.
     *
     * @return the entity details, or {@code null} if none.
     */
    public EntityDetails getEntityDetails() {
        return entityDetails;
    }

    /**
     * Returns the trailers received at the end of the response.
     *
     * @return a non-null list of zero or more trailers.
     */
    public List<Header> getTrailers() {
        return trailers;
    }

    @Override
    public void consumeResponse(
        final HttpResponse response,
        final EntityDetails entityDetails,
        final HttpContext httpContext,
        final FutureCallback<Void> resultCallback
    ) {
        this.entityDetails = entityDetails;
        this.responseCompletion = new BasicFuture<>(resultCallback);
        this.responseFuture.completed(new Message<>(response, reactiveDataConsumer));
        if (entityDetails == null) {
            streamEnd(null);
        }
    }

    @Override
    public void informationResponse(final HttpResponse response, final HttpContext httpContext) {
        this.informationResponse = response;
    }

    @Override
    public void failed(final Exception cause) {
        reactiveDataConsumer.failed(cause);
        responseFuture.failed(cause);
        if (responseCompletion != null) {
            responseCompletion.failed(cause);
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        reactiveDataConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        reactiveDataConsumer.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) {
        if (trailers != null) {
            this.trailers.addAll(trailers);
        }
        reactiveDataConsumer.streamEnd(trailers);
        responseCompletion.completed(null);
    }

    @Override
    public void releaseResources() {
        reactiveDataConsumer.releaseResources();
        responseFuture.cancel();
        if (responseCompletion != null) {
            responseCompletion.cancel();
        }
    }
}
