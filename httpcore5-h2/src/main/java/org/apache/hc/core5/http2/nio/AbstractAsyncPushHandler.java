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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public abstract class AbstractAsyncPushHandler<T> implements AsyncPushConsumer {

    private final AsyncResponseConsumer<Message<HttpResponse, T>> responseConsumer;
    private final AtomicReference<Exception> exception;

    public AbstractAsyncPushHandler(final AsyncResponseConsumer<Message<HttpResponse, T>> responseConsumer) {
        this.responseConsumer = Args.notNull(responseConsumer, "Response consumer");
        this.exception = new AtomicReference<>(null);
    }

    protected abstract void handleResponse(HttpRequest promise, Message<HttpResponse, T> responseMessage) throws IOException, HttpException;

    @Override
    public void consumePromise(
            final HttpRequest promise,
            final HttpResponse response) throws HttpException, IOException {
        responseConsumer.consumeResponse(response, new FutureCallback<Message<HttpResponse, T>>() {

            @Override
            public void completed(final Message<HttpResponse, T> result) {
                try {
                    handleResponse(promise, result);
                } catch (Exception ex) {
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
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        responseConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        responseConsumer.consume(src);
    }

    @Override
    public final void streamEnd(final List<Header> trailers) throws HttpException, IOException {
        responseConsumer.streamEnd(trailers);
    }

    @Override
    public final void failed(final Exception cause) {
        exception.compareAndSet(null, cause);
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        if (responseConsumer != null) {
            responseConsumer.releaseResources();
        }
    }

}
