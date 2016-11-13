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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public abstract class AbstractCharAsyncEntityProducer implements AsyncEntityProducer {

    private enum State { ACTIVE, FLUSHING, END_STREAM }

    private static final CharBuffer EMPTY = CharBuffer.wrap(new char[0]);

    private final ByteBuffer bytebuf;
    private final int fragmentSizeHint;
    private final ContentType contentType;
    private final CharsetEncoder charsetEncoder;
    private final StreamChannel<CharBuffer> charDataStream;

    private volatile State state;

    public AbstractCharAsyncEntityProducer(
            final int bufferSize,
            final int fragmentSizeHint,
            final ContentType contentType) {
        Args.positive(bufferSize, "Buffer size");
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : bufferSize / 2;
        this.bytebuf = ByteBuffer.allocate(bufferSize);
        this.contentType = contentType;
        Charset charset = contentType != null ? contentType.getCharset() : null;
        if (charset == null) {
            charset = StandardCharsets.US_ASCII;
        }
        this.charsetEncoder = charset.newEncoder();
        this.charDataStream = new StreamChannel<CharBuffer>() {

            @Override
            public int write(final CharBuffer src) throws IOException {
                Args.notNull(src, "Buffer");
                final int p = src.position();
                final CoderResult result = charsetEncoder.encode(src, bytebuf, false);
                if (result.isError()) {
                    result.throwException();
                }
                return src.position() - p;
            }

            @Override
            public void endStream() throws IOException {
                state = State.FLUSHING;
            }

        };
        this.state = State.ACTIVE;
    }

    protected abstract void produceData(StreamChannel<CharBuffer> channel) throws IOException;

    @Override
    public final String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        if (state.compareTo(State.ACTIVE) == 0) {
            produceData(charDataStream);
        }
        if (state.compareTo(State.ACTIVE) > 0 || !bytebuf.hasRemaining() || bytebuf.position() >= fragmentSizeHint) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
        if (state.compareTo(State.FLUSHING) == 0) {
            final CoderResult result = charsetEncoder.encode(EMPTY, bytebuf, true);
            if (result.isError()) {
                result.throwException();
            } else if (result.isUnderflow()) {
                final CoderResult result2 = charsetEncoder.flush(bytebuf);
                if (result2.isError()) {
                    result.throwException();
                } else if (result2.isUnderflow()) {
                    state = State.END_STREAM;
                }
            }
        }
        if (bytebuf.position() == 0 && state.compareTo(State.END_STREAM) == 0) {
            channel.endStream();
        }
    }

}
