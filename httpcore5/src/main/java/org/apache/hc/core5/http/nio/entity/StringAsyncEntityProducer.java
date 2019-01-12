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
import java.nio.CharBuffer;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * Basic {@link org.apache.hc.core5.http.nio.AsyncDataProducer} implementation that
 * generates data stream from content of a string.
 *
 * @since 5.0
 */
public class StringAsyncEntityProducer extends AbstractCharAsyncEntityProducer {

    private final CharBuffer content;
    private final AtomicReference<Exception> exception;

    public StringAsyncEntityProducer(
            final CharSequence content,
            final int bufferSize,
            final int fragmentSizeHint,
            final ContentType contentType) {
        super(bufferSize, fragmentSizeHint, contentType);
        Args.notNull(content, "Content");
        this.content = CharBuffer.wrap(content);
        this.exception = new AtomicReference<>(null);
    }

    public StringAsyncEntityProducer(final CharSequence content, final int bufferSize, final ContentType contentType) {
        this(content, bufferSize, -1, contentType);
    }

    public StringAsyncEntityProducer(final CharSequence content, final ContentType contentType) {
        this(content, 4096, contentType);
    }

    public StringAsyncEntityProducer(final CharSequence content) {
        this(content, ContentType.TEXT_PLAIN);
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    protected int availableData() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void produceData(final StreamChannel<CharBuffer> channel) throws IOException {
        Asserts.notNull(channel, "Channel");
        channel.write(content);
        if (!content.hasRemaining()) {
            channel.endStream();
        }
    }

    @Override
    public void failed(final Exception cause) {
        if (exception.compareAndSet(null, cause)) {
            releaseResources();
        }
    }

    public Exception getException() {
        return exception.get();
    }

    @Override
    public void releaseResources() {
        this.content.clear();
        super.releaseResources();
    }

}
