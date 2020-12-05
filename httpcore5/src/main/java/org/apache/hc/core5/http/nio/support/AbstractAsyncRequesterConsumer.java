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
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Abstract asynchronous request consumer that makes use of {@link AsyncEntityConsumer}
 * to process request message content.
 *
 * @param <T> request processing result representation.
 * @param <E> request entity representation.
 *
 * @since 5.0
 */
public abstract class AbstractAsyncRequesterConsumer<T, E> implements AsyncRequestConsumer<T> {

    private final Supplier<AsyncEntityConsumer<E>> dataConsumerSupplier;
    private final AtomicReference<AsyncEntityConsumer<E>> dataConsumerRef;

    public AbstractAsyncRequesterConsumer(final Supplier<AsyncEntityConsumer<E>> dataConsumerSupplier) {
        this.dataConsumerSupplier = Args.notNull(dataConsumerSupplier, "Data consumer supplier");
        this.dataConsumerRef = new AtomicReference<>(null);
    }

    public AbstractAsyncRequesterConsumer(final AsyncEntityConsumer<E> dataConsumer) {
        this(() -> dataConsumer);
    }

    /**
     * Triggered to generate object that represents a result of request message processing.
     * @param request the request message.
     * @param entity the request entity.
     * @param contentType the request content type.
     * @return the result of request processing.
     */
    protected abstract T buildResult(HttpRequest request, E entity, ContentType contentType);

    @Override
    public final void consumeRequest(
            final HttpRequest request,
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
                        final T result = buildResult(request, entity, contentType);
                        resultCallback.completed(result);
                    } catch (final UnsupportedCharsetException ex) {
                        resultCallback.failed(ex);
                    }
                }

            });
        } else {
            resultCallback.completed(buildResult(request, null, null));
        }

    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        dataConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        dataConsumer.consume(src);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        dataConsumer.streamEnd(trailers);
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