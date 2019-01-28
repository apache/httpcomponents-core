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
import java.util.Set;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Abstract binary entity content producer.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public abstract class AbstractBinAsyncEntityProducer implements AsyncEntityProducer {

    enum State { ACTIVE, FLUSHING, END_STREAM }

    private final int fragmentSizeHint;
    private final ByteBuffer bytebuf;
    private final ContentType contentType;

    private volatile State state;

    public AbstractBinAsyncEntityProducer(final int fragmentSizeHint, final ContentType contentType) {
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : 0;
        this.bytebuf = ByteBuffer.allocate(this.fragmentSizeHint);
        this.contentType = contentType;
        this.state = State.ACTIVE;
    }

    private void flush(final StreamChannel<ByteBuffer> channel) throws IOException {
        if (bytebuf.position() > 0) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
    }

    final int writeData(final StreamChannel<ByteBuffer> channel, final ByteBuffer src) throws IOException {
        final int chunk = src.remaining();
        if (chunk == 0) {
            return 0;
        }
        if (chunk > fragmentSizeHint) {
            // the data chunk is greater than the fragment hint
            // attempt to write it out to the channel directly

            // flush the buffer if not empty
            flush(channel);
            if (bytebuf.position() == 0) {
                return channel.write(src);
            }
        } else {
            // the data chunk is smaller than the fragment hint
            // attempt to buffer it

            // flush the buffer if there is not enough space to store the chunk
            if (bytebuf.remaining() < chunk) {
                flush(channel);
            }
            if (bytebuf.remaining() >= chunk) {
                bytebuf.put(src);
                if (!bytebuf.hasRemaining()) {
                    flush(channel);
                }
                return chunk;
            }
        }
        return 0;
    }

    final void streamEnd(final StreamChannel<ByteBuffer> channel) throws IOException {
        if (state == State.ACTIVE) {
            state = State.FLUSHING;
            flush(channel);
            if (bytebuf.position() == 0) {
                state = State.END_STREAM;
                channel.endStream();
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
     * Triggered to signal the ability of the underlying byte channel
     * to accept more data. The data producer can choose to write data
     * immediately inside the call or asynchronously at some later point.
     * <p>
     * {@link StreamChannel} passed to this method is threading-safe.
     *
     * @param channel the data channel capable to accepting more data.
     */
    protected abstract void produceData(StreamChannel<ByteBuffer> channel) throws IOException;

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
    public long getContentLength() {
        return -1;
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
                produceData(new StreamChannel<ByteBuffer>() {

                    @Override
                    public int write(final ByteBuffer src) throws IOException {
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
                flush(channel);
                if (bytebuf.position() == 0) {
                    state = State.END_STREAM;
                    channel.endStream();
                }
            }
        }
    }

    @Override
    public void releaseResources() {
        state = State.ACTIVE;
    }

}
