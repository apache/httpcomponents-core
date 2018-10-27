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

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Abstract push response handler.
 *
 * @since 5.0
 *
 * @param <T> response message representation.
 */
public abstract class AbstractAsyncPushHandler<T> implements AsyncPushConsumer {

    private final AsyncResponseConsumer<T> responseConsumer;

    public AbstractAsyncPushHandler(final AsyncResponseConsumer<T> responseConsumer) {
        this.responseConsumer = Args.notNull(responseConsumer, "Response consumer");
    }

    /**
     * Triggered to handle the push message with the given promised request.
     *
     * @param promise the promised request message.
     * @param responseMessage the pushed response message.
     */
    protected abstract void handleResponse(
            final HttpRequest promise, final T responseMessage) throws IOException, HttpException;

    /**
     * Triggered to handle the exception thrown while processing a push response.
     *
     * @param promise the promised request message.
     * @param cause the cause of error.
     */
    protected void handleError(final HttpRequest promise, final Exception cause) {
    }

    @Override
    public final void consumePromise(
            final HttpRequest promise,
            final HttpResponse response,
            final EntityDetails entityDetails,
            final HttpContext httpContext) throws HttpException, IOException {
        responseConsumer.consumeResponse(response, entityDetails, httpContext, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                try {
                    handleResponse(promise, result);
                } catch (final Exception ex) {
                    failed(ex);
                }
            }

            @Override
            public void failed(final Exception cause) {
                handleError(promise, cause);
                releaseResources();
            }

            @Override
            public void cancelled() {
                releaseResources();
            }

        });
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        responseConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        responseConsumer.consume(src);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        responseConsumer.streamEnd(trailers);
    }

    @Override
    public final void failed(final Exception cause) {
        responseConsumer.failed(cause);
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        if (responseConsumer != null) {
            responseConsumer.releaseResources();
        }
    }

}
