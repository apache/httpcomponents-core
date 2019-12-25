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
import java.nio.channels.ReadableByteChannel;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.FileContentDecoder;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.util.Args;

/**
 * Content decoder that cuts off after a defined number of bytes. This class
 * is used to receive content of HTTP messages where the end of the content
 * entity is determined by the value of the {@code Content-Length header}.
 * Entities transferred using this stream can be maximum {@link Long#MAX_VALUE}
 * long.
 * <p>
 * This decoder is optimized to transfer data directly from the underlying
 * I/O session's channel to a {@link FileChannel}, whenever
 * possible avoiding intermediate buffering in the session buffer.
 *
 * @since 4.0
 */
public class LengthDelimitedDecoder extends AbstractContentDecoder implements FileContentDecoder {

    private final long contentLength;

    private long len;

    public LengthDelimitedDecoder(
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final BasicHttpTransportMetrics metrics,
            final long contentLength) {
        super(channel, buffer, metrics);
        Args.notNegative(contentLength, "Content length");
        this.contentLength = contentLength;
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        Args.notNull(dst, "Byte buffer");
        if (isCompleted()) {
            return -1;
        }
        final int chunk = (int) Math.min((this.contentLength - this.len), Integer.MAX_VALUE);

        final int bytesRead;
        if (this.buffer.hasData()) {
            final int maxLen = Math.min(chunk, this.buffer.length());
            bytesRead = this.buffer.read(dst, maxLen);
        } else {
            bytesRead = readFromChannel(dst, chunk);
        }
        if (bytesRead == -1) {
            setCompleted();
            if (this.len < this.contentLength) {
                throw new ConnectionClosedException(
                                "Premature end of Content-Length delimited message body (expected: %d; received: %d)",
                                this.contentLength, this.len);
            }
        }
        this.len += bytesRead;
        if (this.len >= this.contentLength) {
            this.completed = true;
        }
        if (this.completed && bytesRead == 0) {
            return -1;
        }
        return bytesRead;
    }

    @Override
    public long transfer(
            final FileChannel dst,
            final long position,
            final long count) throws IOException {

        if (dst == null) {
            return 0;
        }
        if (isCompleted()) {
            return -1;
        }

        final int chunk = (int) Math.min((this.contentLength - this.len), Integer.MAX_VALUE);

        final long bytesRead;
        if (this.buffer.hasData()) {
            final int maxLen = Math.min(chunk, this.buffer.length());
            dst.position(position);
            bytesRead = this.buffer.read(dst, count < maxLen ? (int)count : maxLen);
        } else {
            if (this.channel.isOpen()) {
                if (position > dst.size()) {
                    throw new IOException(String.format("Position past end of file [%d > %d]",
                                    position, dst.size()));
                }
                bytesRead = dst.transferFrom(this.channel, position, count < chunk ? count : chunk);
            } else {
                bytesRead = -1;
            }
            if (bytesRead > 0) {
                this.metrics.incrementBytesTransferred(bytesRead);
            }
        }
        if (bytesRead == -1) {
            setCompleted();
            if (this.len < this.contentLength) {
                throw new ConnectionClosedException(
                                "Premature end of Content-Length delimited message body (expected: %d; received: %d)",
                                this.contentLength, this.len);
            }
        }
        this.len += bytesRead;
        if (this.len >= this.contentLength) {
            this.completed = true;
        }
        return bytesRead;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[content length: ");
        sb.append(this.contentLength);
        sb.append("; pos: ");
        sb.append(this.len);
        sb.append("; completed: ");
        sb.append(this.completed);
        sb.append("]");
        return sb.toString();
    }
}
