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

package org.apache.http.impl.nio.codecs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * Abstract {@link ContentEncoder} that serves as a base for all content
 * encoder implementations.
 *
 * @since 4.0
 */
@NotThreadSafe
public abstract class AbstractContentEncoder implements ContentEncoder {

    protected final WritableByteChannel channel;
    protected final SessionOutputBuffer buffer;
    protected final HttpTransportMetricsImpl metrics;

    /**
     * TODO: make private
     */
    protected boolean completed;

    /**
     * Creates an instance of this class.
     *
     * @param channel the destination channel.
     * @param buffer the session output buffer that can be used to store
     *    session data for intermediate processing.
     * @param metrics Transport metrics of the underlying HTTP transport.
     */
    public AbstractContentEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        super();
        Args.notNull(channel, "Channel");
        Args.notNull(buffer, "Session input buffer");
        Args.notNull(metrics, "Transport metrics");
        this.buffer = buffer;
        this.channel = channel;
        this.metrics = metrics;
    }

    @Override
    public boolean isCompleted() {
        return this.completed;
    }

    @Override
    public void complete() throws IOException {
        this.completed = true;
    }

    protected void assertNotCompleted() {
        Asserts.check(!this.completed, "Encoding process already completed");
    }

    /**
     * Flushes content of the session buffer to the channel and updates transport metrics.
     *
     * @return number of bytes written to the channel.
     *
     * @since 4.3
     */
    protected int flushToChannel() throws IOException {
        if (!this.buffer.hasData()) {
            return 0;
        }
        final int bytesWritten = this.buffer.flush(this.channel);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        return bytesWritten;
    }

    /**
     * Flushes content of the given buffer to the channel and updates transport metrics.
     *
     * @return number of bytes written to the channel.
     *
     * @since 4.3
     */
    protected int writeToChannel(final ByteBuffer src) throws IOException {
        if (!src.hasRemaining()) {
            return 0;
        }
        final int bytesWritten = this.channel.write(src);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        return bytesWritten;
    }

    /**
     * Transfers content of the source to the channel and updates transport metrics.
     *
     * @param src source.
     * @param limit max number of bytes to transfer.
     * @return number of bytes transferred.
     *
     * @since 4.3
     */
    protected int writeToChannel(final ByteBuffer src, final int limit) throws IOException {
        return doWriteChunk(src, limit, true);
    }

    /**
     * Transfers content of the source to the buffer and updates transport metrics.
     *
     * @param src source.
     * @param limit max number of bytes to transfer.
     * @return number of bytes transferred.
     *
     * @since 4.3
     */
    protected int writeToBuffer(final ByteBuffer src, final int limit) throws IOException {
        return doWriteChunk(src, limit, false);
    }

    private int doWriteChunk(
        final ByteBuffer src, final int chunk, final boolean direct) throws IOException {
        final int bytesWritten;
        if (src.remaining() > chunk) {
            final int oldLimit = src.limit();
            final int newLimit = oldLimit - (src.remaining() - chunk);
            src.limit(newLimit);
            bytesWritten = doWriteChunk(src, direct);
            src.limit(oldLimit);
        } else {
            bytesWritten = doWriteChunk(src, direct);
        }
        return bytesWritten;
    }

    private int doWriteChunk(final ByteBuffer src, final boolean direct) throws IOException {
        if (direct) {
            final int bytesWritten = this.channel.write(src);
            if (bytesWritten > 0) {
                this.metrics.incrementBytesTransferred(bytesWritten);
            }
            return bytesWritten;
        } else {
            final int chunk = src.remaining();
            this.buffer.write(src);
            return chunk;
        }
    }

}
