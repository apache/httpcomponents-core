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
package org.apache.hc.core5.jackson2.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.UnsupportedMediaTypeException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

abstract class AbstractJsonMessageConsumer<H extends HttpMessage, T> implements AsyncDataConsumer {

    private final Supplier<AsyncEntityConsumer<T>> jsonConsumerSupplier;
    private final AtomicReference<AsyncEntityConsumer<?>> entityConsumerRef;

    public AbstractJsonMessageConsumer(final Supplier<AsyncEntityConsumer<T>> jsonConsumerSupplier) {
        this.jsonConsumerSupplier = Args.notNull(jsonConsumerSupplier, "Json consumer supplier");
        this.entityConsumerRef = new AtomicReference<>();
    }

    protected <E> void handleContent(final AsyncEntityConsumer<E> entityConsumer,
                                     final EntityDetails entityDetails,
                                     final FutureCallback<E> resultCallback) throws HttpException, IOException {
        entityConsumerRef.set(entityConsumer);
        entityConsumer.streamStart(entityDetails, resultCallback);
    }

    protected void consumeMessage(final H messageHead,
                                  final EntityDetails entityDetails,
                                  final HttpContext context,
                                  final FutureCallback<T> resultCallback) throws HttpException, IOException {
        if (entityDetails == null) {
            resultCallback.completed(null);
            return;
        }

        final ContentType contentType = ContentType.parseLenient(entityDetails.getContentType());
        if (contentType == null || ContentType.APPLICATION_JSON.isSameMimeType(contentType)) {
            final AsyncEntityConsumer<T> entityConsumer = jsonConsumerSupplier.get();
            entityConsumerRef.set(entityConsumer);
            entityConsumer.streamStart(entityDetails, new CallbackContribution<T>(resultCallback) {

                @Override
                public void completed(final T result) {
                    resultCallback.completed(result);
                }

            });
        } else {
            final AsyncEntityConsumer<T> entityConsumer = new NoopJsonEntityConsumer<>();
            entityConsumerRef.set(entityConsumer);
            entityConsumer.streamStart(entityDetails, new CallbackContribution<T>(resultCallback) {

                @Override
                public void completed(final T ignore) {
                    resultCallback.failed(new UnsupportedMediaTypeException(contentType));
                }

            });
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public void consume(final ByteBuffer data) throws IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.consume(data);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.streamEnd(trailers);
        }
    }

    public void failed(final Exception cause) {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.failed(cause);
        }
        releaseResources();
    }

    @Override
    public void releaseResources() {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.getAndSet(null);
        if (entityConsumer != null) {
            entityConsumer.releaseResources();
        }
    }

}
