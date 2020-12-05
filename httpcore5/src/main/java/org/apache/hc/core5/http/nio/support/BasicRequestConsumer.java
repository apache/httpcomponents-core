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

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Basic implementation of {@link AsyncRequestConsumer} that represents the request message as
 * a {@link Message} and relies on a {@link AsyncEntityConsumer} to process request entity stream.
 *
 * @since 5.0
 */
public class BasicRequestConsumer<T> implements AsyncRequestConsumer<Message<HttpRequest, T>> {

    private final Supplier<AsyncEntityConsumer<T>> dataConsumerSupplier;
    private final AtomicReference<AsyncEntityConsumer<T>> dataConsumerRef;

    public BasicRequestConsumer(final Supplier<AsyncEntityConsumer<T>> dataConsumerSupplier) {
        this.dataConsumerSupplier = Args.notNull(dataConsumerSupplier, "Data consumer supplier");
        this.dataConsumerRef = new AtomicReference<>(null);
    }

    public BasicRequestConsumer(final AsyncEntityConsumer<T> dataConsumer) {
        this(() -> dataConsumer);
    }

    @Override
    public void consumeRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final HttpContext httpContext,
            final FutureCallback<Message<HttpRequest, T>> resultCallback) throws HttpException, IOException {
        Args.notNull(request, "Request");
        if (entityDetails != null) {
            final AsyncEntityConsumer<T> dataConsumer = dataConsumerSupplier.get();
            if (dataConsumer == null) {
                throw new HttpException("Supplied data consumer is null");
            }
            dataConsumerRef.set(dataConsumer);

            dataConsumer.streamStart(entityDetails, new CallbackContribution<T>(resultCallback) {

                @Override
                public void completed(final T body) {
                    final Message<HttpRequest, T> result = new Message<>(request, body);
                    if (resultCallback != null) {
                        resultCallback.completed(result);
                    }
                }

            });
        } else {
            final Message<HttpRequest, T> result = new Message<>(request, null);
            if (resultCallback != null) {
                resultCallback.completed(result);
            }
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncEntityConsumer<T> dataConsumer = dataConsumerRef.get();
        dataConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        final AsyncEntityConsumer<T> dataConsumer = dataConsumerRef.get();
        dataConsumer.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncEntityConsumer<T> dataConsumer = dataConsumerRef.get();
        dataConsumer.streamEnd(trailers);
    }

    @Override
    public void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public void releaseResources() {
        final AsyncEntityConsumer<T> dataConsumer = dataConsumerRef.getAndSet(null);
        if (dataConsumer != null) {
            dataConsumer.releaseResources();
        }
    }

}
