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
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Abstract asynchronous response consumer that makes use of {@link AsyncEntityConsumer}
 * to process response message content.
 *
 * @param <T> response processing result representation.
 * @param <E> response entity representation.
 *
 * @since 5.0
 */
public abstract class AbstractAsyncResponseConsumer<T, E> implements AsyncResponseConsumer<T> {

    private final Supplier<AsyncEntityConsumer<E>> dataConsumerSupplier;
    private final AtomicReference<AsyncEntityConsumer<E>> dataConsumerRef;

    public AbstractAsyncResponseConsumer(final Supplier<AsyncEntityConsumer<E>> dataConsumerSupplier) {
        this.dataConsumerSupplier = Args.notNull(dataConsumerSupplier, "Data consumer supplier");
        this.dataConsumerRef = new AtomicReference<>(null);
    }

    public AbstractAsyncResponseConsumer(final AsyncEntityConsumer<E> dataConsumer) {
        this(() -> dataConsumer);
    }

    /**
     * Triggered to generate object that represents a result of response message processing.
     * @param response the response message.
     * @param entity the response entity.
     * @param contentType the response content type.
     * @return the result of response processing.
     */
    protected abstract T buildResult(HttpResponse response, E entity, ContentType contentType);

    @Override
    public final void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final HttpContext httpContext, final FutureCallback<T> resultCallback) throws HttpException, IOException {
        if (entityDetails != null) {
            final AsyncEntityConsumer<E> dataConsumer = dataConsumerSupplier.get();
            if (dataConsumer == null) {
                throw new HttpException("Supplied data consumer is null");
            }
            dataConsumerRef.set(dataConsumer);
            dataConsumer.streamStart(entityDetails, new CallbackContribution<E>(resultCallback) {

                @Override
                public void completed(final E entity) {
                    final ContentType contentType;
                    try {
                        contentType = ContentType.parse(entityDetails.getContentType());
                        final T result = buildResult(response, entity, contentType);
                        if (resultCallback != null) {
                            resultCallback.completed(result);
                        }
                    } catch (final UnsupportedCharsetException ex) {
                        if (resultCallback != null) {
                            resultCallback.failed(ex);
                        }
                    }
                }

            });
        } else {
            final T result = buildResult(response, null, null);
            if (resultCallback != null) {
                resultCallback.completed(result);
            }
        }

    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        if (dataConsumer != null) {
            dataConsumer.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        if (dataConsumer != null) {
            dataConsumer.consume(src);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        if (dataConsumer != null) {
            dataConsumer.streamEnd(trailers);
        }
    }

    @Override
    public final void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.getAndSet(null);
        if (dataConsumer != null) {
            dataConsumer.releaseResources();
        }
    }

}