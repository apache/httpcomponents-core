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

import org.apache.hc.core5.concurrent.FutureCallback;
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

    private final AsyncEntityConsumer<E> entityConsumer;

    private volatile T result;

    public AbstractAsyncRequesterConsumer(final AsyncEntityConsumer<E> entityConsumer) {
        Args.notNull(entityConsumer, "Entity consumer");
        this.entityConsumer = entityConsumer;
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
            entityConsumer.streamStart(entityDetails, new FutureCallback<E>() {

                @Override
                public void completed(final E entity) {
                    final ContentType contentType;
                    try {
                        contentType = ContentType.parse(entityDetails.getContentType());
                        result = buildResult(request, entity, contentType);
                        resultCallback.completed(result);
                    } catch (final UnsupportedCharsetException ex) {
                        resultCallback.failed(null, ex);
                    }
                }

                @Override
                public void failed(final String message, final Exception ex) {
                    resultCallback.failed(message, ex);
                }

                @Override
                public void cancelled() {
                    resultCallback.cancelled();
                }

            });
        } else {
            resultCallback.completed(buildResult(request, null, null));
            entityConsumer.releaseResources();
        }

    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        entityConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        entityConsumer.consume(src);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        entityConsumer.streamEnd(trailers);
    }

    @Override
    public T getResult() {
        return result;
    }

    @Override
    public final void failed(final String message, final Exception cause) {
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        entityConsumer.releaseResources();
    }

}