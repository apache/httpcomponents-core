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

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.nio.FileContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;

/**
 * Content encoder that cuts off after a defined number of bytes. This class
 * is used to send content of HTTP messages where the end of the content entity
 * is determined by the value of the <code>Content-Length header</code>.
 * Entities transferred using this stream can be maximum {@link Long#MAX_VALUE}
 * long.
 * <p>
 * This decoder is optimized to transfer data directly from
 * a {@link FileChannel} to the underlying I/O session's channel whenever
 * possible avoiding intermediate buffering in the session buffer.
 *
 * @since 4.0
 */
public class LengthDelimitedEncoder extends AbstractContentEncoder
        implements FileContentEncoder {

    private final long contentLength;

    private long len;

    public LengthDelimitedEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics,
            long contentLength) {
        super(channel, buffer, metrics);
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length may not be negative");
        }
        this.contentLength = contentLength;
        this.len = 0;
    }

    public int write(final ByteBuffer src) throws IOException {
        if (src == null) {
            return 0;
        }
        assertNotCompleted();
        int lenRemaining = (int) (this.contentLength - this.len);

        int bytesWritten;
        if (src.remaining() > lenRemaining) {
            int oldLimit = src.limit();
            int newLimit = oldLimit - (src.remaining() - lenRemaining);
            src.limit(newLimit);
            bytesWritten = this.channel.write(src);
            src.limit(oldLimit);
        } else {
            bytesWritten = this.channel.write(src);
        }
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        this.len += bytesWritten;
        if (this.len >= this.contentLength) {
            this.completed = true;
        }
        return bytesWritten;
    }

    public long transfer(
            final FileChannel src,
            long position,
            long count) throws IOException {

        if (src == null) {
            return 0;
        }
        assertNotCompleted();
        int lenRemaining = (int) (this.contentLength - this.len);

        long bytesWritten;
        if (count > lenRemaining) {
            count = lenRemaining;
        }
        bytesWritten = src.transferTo(position, count, this.channel);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        this.len += bytesWritten;
        if (this.len >= this.contentLength) {
            this.completed = true;
        }
        return bytesWritten;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[content length: ");
        buffer.append(this.contentLength);
        buffer.append("; pos: ");
        buffer.append(this.len);
        buffer.append("; completed: ");
        buffer.append(this.completed);
        buffer.append("]");
        return buffer.toString();
    }

}
