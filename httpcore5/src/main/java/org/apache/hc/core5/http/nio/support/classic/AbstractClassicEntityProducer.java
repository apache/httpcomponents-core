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
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncEntityProducer} implementation that acts as a compatibility
 * layer for classic {@link OutputStream} based interfaces. Blocking output
 * processing is executed through an {@link Executor}.
 *
 * @since 5.0
 */
public abstract class AbstractClassicEntityProducer implements AsyncEntityProducer {

    private enum State { IDLE, ACTIVE, COMPLETED }

    private final SharedOutputBuffer buffer;
    private final ContentType contentType;
    private final Executor executor;
    private final AtomicReference<State> state;
    private final AtomicReference<Exception> exception;

    public AbstractClassicEntityProducer(final int initialBufferSize, final ContentType contentType, final Executor executor) {
        this.buffer = new SharedOutputBuffer(initialBufferSize);
        this.contentType = contentType;
        this.executor = Args.notNull(executor, "Executor");
        this.state = new AtomicReference<>(State.IDLE);
        this.exception = new AtomicReference<>(null);
    }

    /**
     * Writes out entity data into the given stream.
     *
     * @param contentType the entity content type
     * @param outputStream the output stream
     */
    protected abstract void produceData(ContentType contentType, OutputStream outputStream) throws IOException;

    @Override
    public final boolean isRepeatable() {
        return false;
    }

    @Override
    public final int available() {
        return buffer.length();
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        if (state.compareAndSet(State.IDLE, State.ACTIVE)) {
            executor.execute(() -> {
                try {
                    produceData(contentType, new ContentOutputStream(buffer));
                    buffer.writeCompleted();
                } catch (final Exception ex) {
                    buffer.abort();
                } finally {
                    state.set(State.COMPLETED);
                }
            });
        }
        buffer.flush(channel);
    }

    @Override
    public final long getContentLength() {
        return -1;
    }

    @Override
    public final String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public final boolean isChunked() {
        return false;
    }

    @Override
    public final Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public final void failed(final Exception cause) {
        if (exception.compareAndSet(null, cause)) {
            releaseResources();
        }
    }

    public final Exception getException() {
        return exception.get();
    }

    @Override
    public void releaseResources() {
    }

}
