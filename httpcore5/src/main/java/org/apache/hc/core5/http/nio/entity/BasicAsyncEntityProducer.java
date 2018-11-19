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
package org.apache.hc.core5.http.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Basic {@link AsyncEntityProducer} implementation that generates data stream
 * from content of a byte array.
 *
 * @since 5.0
 */
public class BasicAsyncEntityProducer implements AsyncEntityProducer {

    private final ByteBuffer bytebuf;
    private final int length;
    private final ContentType contentType;
    private final boolean chunked;
    private final AtomicReference<Exception> exception;

    public BasicAsyncEntityProducer(final byte[] content, final ContentType contentType, final boolean chunked) {
        Args.notNull(content, "Content");
        this.bytebuf = ByteBuffer.wrap(content);
        this.length = this.bytebuf.remaining();
        this.contentType = contentType;
        this.chunked = chunked;
        this.exception = new AtomicReference<>(null);
    }

    public BasicAsyncEntityProducer(final byte[] content, final ContentType contentType) {
        this(content, contentType, false);
    }

    public BasicAsyncEntityProducer(final byte[] content) {
        this(content, ContentType.APPLICATION_OCTET_STREAM);
    }

    public BasicAsyncEntityProducer(final CharSequence content, final ContentType contentType, final boolean chunked) {
        Args.notNull(content, "Content");
        this.contentType = contentType;
        Charset charset = contentType != null ? contentType.getCharset() : null;
        if (charset == null) {
            charset = StandardCharsets.US_ASCII;
        }
        this.bytebuf = charset.encode(CharBuffer.wrap(content));
        this.length = this.bytebuf.remaining();
        this.chunked = chunked;
        this.exception = new AtomicReference<>(null);
    }

    public BasicAsyncEntityProducer(final CharSequence content, final ContentType contentType) {
        this(content, contentType, false);
    }

    public BasicAsyncEntityProducer(final CharSequence content) {
        this(content, ContentType.TEXT_PLAIN);
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public final String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public int available() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return chunked;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        if (bytebuf.hasRemaining()) {
            channel.write(bytebuf);
        }
        if (!bytebuf.hasRemaining()) {
            channel.endStream();
        }
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
        bytebuf.clear();
    }

}
