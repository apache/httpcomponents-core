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
package org.apache.hc.core5.http.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public class BasicResponseConsumer<T> implements AsyncResponseConsumer<Message<HttpResponse, T>> {

    private final AsyncEntityConsumer<T> dataConsumer;
    private final AtomicReference<Exception> exception;

    public BasicResponseConsumer(final AsyncEntityConsumer<T> dataConsumer) {
        this.dataConsumer = Args.notNull(dataConsumer, "Consumer");
        this.exception = new AtomicReference<>(null);
    }

    @Override
    public void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final FutureCallback<Message<HttpResponse, T>> resultCallback) throws HttpException, IOException {
        Args.notNull(response, "Response");
        Args.notNull(resultCallback, "Result callback");

        if (entityDetails != null) {
            dataConsumer.streamStart(entityDetails, new FutureCallback<T>() {

                @Override
                public void completed(final T result) {
                    resultCallback.completed(new Message<>(response, result));
                }

                @Override
                public void failed(final Exception ex) {
                    resultCallback.failed(ex);
                }

                @Override
                public void cancelled() {
                    resultCallback.cancelled();
                }

            });
        } else {
            resultCallback.completed(new Message<HttpResponse, T>(response, null));
            dataConsumer.releaseResources();
        }

    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        dataConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public int consume(final ByteBuffer src) throws IOException {
        return dataConsumer.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        dataConsumer.streamEnd(trailers);
    }

    @Override
    public void failed(final Exception cause) {
        if (exception.compareAndSet(null, cause)) {
            dataConsumer.failed(cause);
        }
    }

    public Exception getException() {
        return exception.get();
    }

    public T getResponseContent() {
        return dataConsumer.getContent();
    }

    @Override
    public void releaseResources() {
        dataConsumer.releaseResources();
    }

}
