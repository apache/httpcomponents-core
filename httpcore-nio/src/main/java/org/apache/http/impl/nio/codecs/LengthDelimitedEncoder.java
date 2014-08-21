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
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.nio.FileContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.util.Args;

/**
 * Content encoder that cuts off after a defined number of bytes. This class
 * is used to send content of HTTP messages where the end of the content entity
 * is determined by the value of the {@code Content-Length header}.
 * Entities transferred using this stream can be maximum {@link Long#MAX_VALUE}
 * long.
 * <p>
 * This decoder is optimized to transfer data directly from
 * a {@link FileChannel} to the underlying I/O session's channel whenever
 * possible avoiding intermediate buffering in the session buffer.
 *
 * @since 4.0
 */
@NotThreadSafe
public class LengthDelimitedEncoder extends AbstractContentEncoder
        implements FileContentEncoder {

    private final long contentLength;
    private final int fragHint;

    private long remaining;

    /**
     * @since 4.3
     *
     * @param channel underlying channel.
     * @param buffer  session buffer.
     * @param metrics transport metrics.
     * @param contentLength content length.
     * @param fragementSizeHint fragment size hint defining an minimal size of a fragment
     *   that should be written out directly to the channel bypassing the session buffer.
     *   Value {@code 0} disables fragment buffering.
     */
    public LengthDelimitedEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics,
            final long contentLength,
            final int fragementSizeHint) {
        super(channel, buffer, metrics);
        Args.notNegative(contentLength, "Content length");
        this.contentLength = contentLength;
        this.fragHint = fragementSizeHint > 0 ? fragementSizeHint : 0;
        this.remaining = contentLength;
    }

    public LengthDelimitedEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics,
            final long contentLength) {
        this(channel, buffer, metrics, contentLength, 0);
    }

    private int nextChunk(final ByteBuffer src) {
        return (int) Math.min(Math.min(this.remaining, Integer.MAX_VALUE), src.remaining());
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (src == null) {
            return 0;
        }
        assertNotCompleted();

        int total = 0;
        while (src.hasRemaining() && this.remaining > 0) {
            if (this.buffer.hasData() || this.fragHint > 0) {
                final int chunk = nextChunk(src);
                if (chunk <= this.fragHint) {
                    final int capacity = this.fragHint - this.buffer.length();
                    if (capacity > 0) {
                        final int limit = Math.min(capacity, chunk);
                        final int bytesWritten = writeToBuffer(src, limit);
                        this.remaining -= bytesWritten;
                        total += bytesWritten;
                    }
                }
            }
            if (this.buffer.hasData()) {
                final int chunk = nextChunk(src);
                if (this.buffer.length() >= this.fragHint || chunk > 0) {
                    final int bytesWritten = flushToChannel();
                    if (bytesWritten == 0) {
                        break;
                    }
                }
            }
            if (!this.buffer.hasData()) {
                final int chunk = nextChunk(src);
                if (chunk > this.fragHint) {
                    final int bytesWritten = writeToChannel(src, chunk);
                    this.remaining -= bytesWritten;
                    total += bytesWritten;
                    if (bytesWritten == 0) {
                        break;
                    }
                }
            }
        }
        if (this.remaining <= 0) {
            super.complete();
        }
        return total;
    }

    @Override
    public long transfer(
            final FileChannel src,
            final long position,
            final long count) throws IOException {

        if (src == null) {
            return 0;
        }
        assertNotCompleted();

        flushToChannel();
        if (this.buffer.hasData()) {
            return 0;
        }

        final long chunk = Math.min(this.remaining, count);
        final long bytesWritten = src.transferTo(position, chunk, this.channel);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        this.remaining -= bytesWritten;
        if (this.remaining <= 0) {
            super.complete();
        }
        return bytesWritten;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[content length: ");
        sb.append(this.contentLength);
        sb.append("; pos: ");
        sb.append(this.contentLength - this.remaining);
        sb.append("; completed: ");
        sb.append(isCompleted());
        sb.append("]");
        return sb.toString();
    }

}
