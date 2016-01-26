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
import java.nio.channels.WritableByteChannel;

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.TrailerSupplier;
import org.apache.hc.core5.http.impl.io.HttpTransportMetricsImpl;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Implements chunked transfer coding. The content is sent in small chunks.
 * Entities transferred using this decoder can be of unlimited length.
 *
 * @since 4.0
 */
@NotThreadSafe
public class ChunkEncoder extends AbstractContentEncoder {
    private final int fragHint;
    private final CharArrayBuffer lineBuffer;
    private final TrailerSupplier trailers;

    /**
     * @param channel underlying channel.
     * @param buffer  session buffer.
     * @param metrics transport metrics.
     * @param fragementSizeHint fragment size hint defining an minimal size of a fragment
     *   that should be written out directly to the channel bypassing the session buffer.
     *   Value {@code 0} disables fragment buffering.
     *
     * @since 5.0
     */
    public ChunkEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics,
            final int fragementSizeHint,
            final TrailerSupplier trailers) {
        super(channel, buffer, metrics);
        this.fragHint = fragementSizeHint > 0 ? fragementSizeHint : 0;
        this.lineBuffer = new CharArrayBuffer(16);
        this.trailers = trailers;
    }

    public ChunkEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        this(channel, buffer, metrics, 0, null);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (src == null) {
            return 0;
        }
        assertNotCompleted();

        int total = 0;
        while (src.hasRemaining()) {
            int chunk = src.remaining();
            int avail;
            avail = this.buffer.available();

            // subtract the length of the longest chunk header
            // 12345678\r\n
            // <chunk-data>\r\n
            avail -= 12;
            if (avail > 0) {
                if (avail < chunk) {
                    // write no more than 'avail' bytes
                    chunk = avail;
                    this.lineBuffer.clear();
                    this.lineBuffer.append(Integer.toHexString(chunk));
                    this.buffer.writeLine(this.lineBuffer);
                    final int oldlimit = src.limit();
                    src.limit(src.position() + chunk);
                    this.buffer.write(src);
                    src.limit(oldlimit);
                } else {
                    // write all
                    this.lineBuffer.clear();
                    this.lineBuffer.append(Integer.toHexString(chunk));
                    this.buffer.writeLine(this.lineBuffer);
                    this.buffer.write(src);
                }
                this.lineBuffer.clear();
                this.buffer.writeLine(this.lineBuffer);
                total += chunk;
            }
            if (this.buffer.length() >= this.fragHint || src.hasRemaining()) {
                final int bytesWritten = flushToChannel();
                if (bytesWritten == 0) {
                    break;
                }
            }
        }
        return total;
    }

    @Override
    public void complete() throws IOException {
        assertNotCompleted();
        this.lineBuffer.clear();
        this.lineBuffer.append("0");
        this.buffer.writeLine(this.lineBuffer);
        writeTrailers();
        this.lineBuffer.clear();
        this.buffer.writeLine(this.lineBuffer);
        super.complete();
    }

    private void writeTrailers() throws IOException {
        final Header[] headers = this.trailers != null ? this.trailers.get() : null;
        if (headers != null) {
            for (final Header header: headers) {
                if (header instanceof FormattedHeader) {
                    final CharArrayBuffer chbuffer = ((FormattedHeader) header).getBuffer();
                    buffer.writeLine(chbuffer);
                } else {
                    this.lineBuffer.clear();
                    BasicLineFormatter.INSTANCE.formatHeader(this.lineBuffer, header);
                    buffer.writeLine(this.lineBuffer);
                }
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[chunk-coded; completed: ");
        sb.append(isCompleted());
        sb.append("]");
        return sb.toString();
    }

}
