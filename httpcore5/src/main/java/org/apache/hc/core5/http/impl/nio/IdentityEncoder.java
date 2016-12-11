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

package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.FileContentEncoder;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;

/**
 * Content encoder that writes data without any transformation. The end of
 * the content entity is demarcated by closing the underlying connection
 * (EOF condition). Entities transferred using this input stream can be of
 * unlimited length.
 * <p>
 * This decoder is optimized to transfer data directly from
 * a {@link FileChannel} to the underlying I/O session's channel whenever
 * possible avoiding intermediate buffering in the session buffer.
 *
 * @since 4.0
 */
public class IdentityEncoder extends AbstractContentEncoder implements FileContentEncoder {

    private final int fragHint;

    /**
     * @since 4.3
     *
     * @param channel underlying channel.
     * @param buffer  session buffer.
     * @param metrics transport metrics.
     * @param chunkSizeHint fragment size hint defining an minimal size of a fragment
     *   that should be written out directly to the channel bypassing the session buffer.
     *   Value {@code 0} disables fragment buffering.
     */
    public IdentityEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final BasicHttpTransportMetrics metrics,
            final int chunkSizeHint) {
        super(channel, buffer, metrics);
        this.fragHint = chunkSizeHint > 0 ? chunkSizeHint : 0;
    }

    public IdentityEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final BasicHttpTransportMetrics metrics) {
        this(channel, buffer, metrics, 0);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (src == null) {
            return 0;
        }
        assertNotCompleted();

        int total = 0;
        while (src.hasRemaining()) {
            if (this.buffer.hasData() || this.fragHint > 0) {
                if (src.remaining() <= this.fragHint) {
                    final int capacity = this.fragHint - this.buffer.length();
                    if (capacity > 0) {
                        final int limit = Math.min(capacity, src.remaining());
                        final int bytesWritten = writeToBuffer(src, limit);
                        total += bytesWritten;
                    }
                }
            }
            if (this.buffer.hasData()) {
                if (this.buffer.length() >= this.fragHint || src.hasRemaining()) {
                    final int bytesWritten = flushToChannel();
                    if (bytesWritten == 0) {
                        break;
                    }
                }
            }
            if (!this.buffer.hasData() && src.remaining() > this.fragHint) {
                final int bytesWritten = writeToChannel(src);
                total += bytesWritten;
                if (bytesWritten == 0) {
                    break;
                }
            }
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

        final long bytesWritten = src.transferTo(position, count, this.channel);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        return bytesWritten;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[identity; completed: ");
        sb.append(isCompleted());
        sb.append("]");
        return sb.toString();
    }

}
