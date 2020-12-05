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
package org.apache.hc.core5.http.nio.support.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncEntityConsumer} implementation that acts as a compatibility
 * layer for classic {@link InputStream} based interfaces. Blocking input
 * processing is executed through an {@link Executor}.
 *
 * @param <T> entity representation.
 *
 * @since 5.0
 */
public abstract class AbstractClassicEntityConsumer<T> implements AsyncEntityConsumer<T> {

    private enum State { IDLE, ACTIVE, COMPLETED }

    private final Executor executor;
    private final SharedInputBuffer buffer;
    private final AtomicReference<State> state;
    private final AtomicReference<T> resultRef;
    private final AtomicReference<Exception> exceptionRef;

    public AbstractClassicEntityConsumer(final int initialBufferSize, final Executor executor) {
        this.executor = Args.notNull(executor, "Executor");
        this.buffer = new SharedInputBuffer(initialBufferSize);
        this.state = new AtomicReference<>(State.IDLE);
        this.resultRef = new AtomicReference<>(null);
        this.exceptionRef = new AtomicReference<>(null);
    }

    /**
     * Processes entity data from the given stream.
     *
     * @param contentType the entity content type
     * @param inputStream the input stream
     * @return the result of entity processing.
     */
    protected abstract T consumeData(ContentType contentType, InputStream inputStream) throws IOException;

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        buffer.updateCapacity(capacityChannel);
    }

    @Override
    public final void streamStart(final EntityDetails entityDetails, final FutureCallback<T> resultCallback) throws HttpException, IOException {
        final ContentType contentType;
        try {
            contentType = ContentType.parse(entityDetails.getContentType());
        } catch (final UnsupportedCharsetException ex) {
            throw new UnsupportedEncodingException(ex.getMessage());
        }
        if (state.compareAndSet(State.IDLE, State.ACTIVE)) {
            executor.execute(() -> {
                try {
                    final T result = consumeData(contentType, new ContentInputStream(buffer));
                    resultRef.set(result);
                    resultCallback.completed(result);
                } catch (final Exception ex) {
                    buffer.abort();
                    resultCallback.failed(ex);
                } finally {
                    state.set(State.COMPLETED);
                }
            });
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        buffer.fill(src);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        buffer.markEndStream();
    }

    @Override
    public final void failed(final Exception cause) {
        if (exceptionRef.compareAndSet(null, cause)) {
            releaseResources();
        }
    }

    public final Exception getException() {
        return exceptionRef.get();
    }

    @Override
    public final T getContent() {
        return resultRef.get();
    }

    @Override
    public void releaseResources() {
    }

}
