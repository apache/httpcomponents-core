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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Abstract text entity content producer.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public abstract class AbstractCharAsyncEntityProducer implements AsyncEntityProducer {

    private static final CharBuffer EMPTY = CharBuffer.wrap(new char[0]);

    enum State { ACTIVE, FLUSHING, END_STREAM }

    private final ByteBuffer bytebuf;
    private final int fragmentSizeHint;
    private final ContentType contentType;
    private final CharsetEncoder charsetEncoder;

    private volatile State state;

    public AbstractCharAsyncEntityProducer(
            final int bufferSize,
            final int fragmentSizeHint,
            final ContentType contentType) {
        Args.positive(bufferSize, "Buffer size");
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : 0;
        this.bytebuf = ByteBuffer.allocate(bufferSize);
        this.contentType = contentType;
        Charset charset = contentType != null ? contentType.getCharset() : null;
        if (charset == null) {
            charset = StandardCharsets.US_ASCII;
        }
        this.charsetEncoder = charset.newEncoder();
        this.state = State.ACTIVE;
    }

    private void flush(final StreamChannel<ByteBuffer> channel) throws IOException {
        if (bytebuf.position() > 0) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
    }

    final int writeData(final StreamChannel<ByteBuffer> channel, final CharBuffer src) throws IOException {

        final int chunk = src.remaining();
        if (chunk == 0) {
            return 0;
        }

        final int p = src.position();
        final CoderResult result = charsetEncoder.encode(src, bytebuf, false);
        if (result.isError()) {
            result.throwException();
        }

        if (!bytebuf.hasRemaining() || bytebuf.position() >= fragmentSizeHint) {
            flush(channel);
        }

        return src.position() - p;
    }

    final void streamEnd(final StreamChannel<ByteBuffer> channel) throws IOException {
        if (state == State.ACTIVE) {
            state = State.FLUSHING;
            if (!bytebuf.hasRemaining()) {
                flush(channel);
            }

            final CoderResult result = charsetEncoder.encode(EMPTY, bytebuf, true);
            if (result.isError()) {
                result.throwException();
            }
            final CoderResult result2 = charsetEncoder.flush(bytebuf);
            if (result2.isError()) {
                result.throwException();
            } else if (result.isUnderflow()) {
                flush(channel);
                if (bytebuf.position() == 0) {
                    state = State.END_STREAM;
                    channel.endStream();
                }
            }
        }

    }

    /**
     * Returns the number of bytes immediately available for output.
     * This method can be used as a hint to control output events
     * of the underlying I/O session.
     *
     * @return the number of bytes immediately available for output
     */
    protected abstract int availableData();

    /**
     * Triggered to signal the ability of the underlying char channel
     * to accept more data. The data producer can choose to write data
     * immediately inside the call or asynchronously at some later point.
     * <p>
     * {@link StreamChannel} passed to this method is threading-safe.
     *
     * @param channel the data channel capable to accepting more data.
     */
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
    public long getContentLength() {
        return -1;
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
    public final int available() {
        if (state == State.ACTIVE) {
            return availableData();
        } else {
            synchronized (bytebuf) {
                return bytebuf.position();
            }
        }
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        synchronized (bytebuf) {
            if (state == State.ACTIVE) {
                produceData(new StreamChannel<CharBuffer>() {

                    @Override
                    public int write(final CharBuffer src) throws IOException {
                        Args.notNull(src, "Buffer");
                        synchronized (bytebuf) {
                            return writeData(channel, src);
                        }
                    }

                    @Override
                    public void endStream() throws IOException {
                        synchronized (bytebuf) {
                            streamEnd(channel);
                        }
                    }

                });
            }
            if (state == State.FLUSHING) {
                final CoderResult result = charsetEncoder.flush(bytebuf);
                if (result.isError()) {
                    result.throwException();
                } else if (result.isOverflow()) {
                    flush(channel);
                } else if (result.isUnderflow()) {
                    flush(channel);
                    if (bytebuf.position() == 0) {
                        state = State.END_STREAM;
                        channel.endStream();
                    }
                }

            }

        }
    }

    @Override
    public void releaseResources() {
        state = State.ACTIVE;
        charsetEncoder.reset();
    }

}
