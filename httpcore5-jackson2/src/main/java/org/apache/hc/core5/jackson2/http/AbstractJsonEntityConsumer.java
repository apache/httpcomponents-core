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
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonFactory;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.jackson2.JsonAsyncTokenizer;
import org.apache.hc.core5.jackson2.JsonContentException;
import org.apache.hc.core5.jackson2.JsonTokenConsumer;
import org.apache.hc.core5.util.Args;

abstract class AbstractJsonEntityConsumer<T> implements AsyncEntityConsumer<T> {

    private final JsonAsyncTokenizer jsonTokenizer;
    private final AtomicReference<FutureCallback<T>> resultCallbackRef;
    private final AtomicReference<T> resultRef;

    AbstractJsonEntityConsumer(final JsonFactory jsonFactory) {
        this.jsonTokenizer = new JsonAsyncTokenizer(Args.notNull(jsonFactory, "Json factory"));
        this.resultCallbackRef = new AtomicReference<>(null);
        this.resultRef = new AtomicReference<>(null);
    }

    abstract JsonTokenConsumer createJsonTokenConsumer(Consumer<T> resultConsumer);

    @Override
    public final void streamStart(final EntityDetails entityDetails, final FutureCallback<T> resultCallback) throws HttpException, IOException {
        final ContentType contentType = ContentType.parseLenient(entityDetails.getContentType());
        if (contentType != null && !ContentType.APPLICATION_JSON.isSameMimeType(contentType)) {
            throw new JsonContentException("Unexpected content type: " + contentType.getMimeType());
        }
        resultCallbackRef.set(resultCallback);
        jsonTokenizer.initialize(createJsonTokenConsumer(result -> {
            resultRef.set(result);
            final FutureCallback<T> resultCallback1 = resultCallbackRef.getAndSet(null);
            if (resultCallback1 != null) {
                resultCallback1.completed(result);
            }
        }));
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE);
    }

    @Override
    public final void consume(final ByteBuffer data) throws IOException {
        jsonTokenizer.consume(data);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        jsonTokenizer.streamEnd();
    }

    @Override
    public final void failed(final Exception cause) {
        final FutureCallback<T> resultCallback = resultCallbackRef.getAndSet(null);
        if (resultCallback != null) {
            resultCallback.failed(cause);
        }
    }

    @Override
    public final T getContent() {
        return resultRef.get();
    }

    @Override
    public void releaseResources() {
    }

}
