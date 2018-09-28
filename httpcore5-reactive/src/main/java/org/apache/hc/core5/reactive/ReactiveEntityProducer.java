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
package org.apache.hc.core5.reactive;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * An {@link AsyncEntityProducer} that subscribes to a {@code Publisher}
 * instance, as defined by the Reactive Streams specification.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class ReactiveEntityProducer implements AsyncEntityProducer {

    private final ReactiveDataProducer reactiveDataProducer;

    private final long contentLength;
    private final ContentType contentType;
    private final String contentEncoding;

    /**
     * Creates a new {@code ReactiveEntityProducer} with the given parameters.
     *
     * @param publisher the publisher of the entity stream.
     * @param contentLength the length of the entity, or -1 if unknown (implies chunked encoding).
     * @param contentType the {@code Content-Type} of the entity, or null if none.
     * @param contentEncoding the {@code Content-Encoding} of the entity, or null if none.
     */
    public ReactiveEntityProducer(
        final Publisher<ByteBuffer> publisher,
        final long contentLength,
        final ContentType contentType,
        final String contentEncoding
    ) {
        this.reactiveDataProducer = new ReactiveDataProducer(publisher);
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.contentEncoding = contentEncoding;
    }

    @Override
    public int available() {
        return reactiveDataProducer.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        reactiveDataProducer.produce(channel);
    }

    @Override
    public void releaseResources() {
        reactiveDataProducer.releaseResources();
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return contentEncoding;
    }

    @Override
    public boolean isChunked() {
        return contentLength == -1;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }
}
