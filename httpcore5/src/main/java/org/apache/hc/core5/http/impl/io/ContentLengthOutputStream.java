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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.util.Args;

/**
 * Output stream that cuts off after a defined number of bytes. This class
 * is used to send content of HTTP messages where the end of the content entity
 * is determined by the value of the {@code Content-Length header}.
 * Entities transferred using this stream can be maximum {@link Long#MAX_VALUE}
 * long.
 * <p>
 * Note that this class NEVER closes the underlying stream, even when
 * {@link #close()} gets called.  Instead, the stream will be marked as closed
 * and no further output will be permitted.
 *
 * @since 4.0
 */
public class ContentLengthOutputStream extends OutputStream {

    private final SessionOutputBuffer buffer;
    private final OutputStream outputStream;

    /**
     * The maximum number of bytes that can be written the stream. Subsequent
     * write operations will be ignored.
     */
    private final long contentLength;

    /** Total bytes written */
    private long total;

    /** True if the stream is closed. */
    private boolean closed;

    /**
     * Default constructor.
     *
     * @param buffer Session output buffer
     * @param outputStream Output stream
     * @param contentLength The maximum number of bytes that can be written to
     * the stream. Subsequent write operations will be ignored.
     *
     * @since 4.0
     */
    public ContentLengthOutputStream(final SessionOutputBuffer buffer, final OutputStream outputStream, final long contentLength) {
        super();
        this.buffer = Args.notNull(buffer, "Session output buffer");
        this.outputStream = Args.notNull(outputStream, "Output stream");
        this.contentLength = Args.notNegative(contentLength, "Content length");
    }

    /**
     * Finishes writing to the underlying stream, but does NOT close the underlying stream.
     * @throws IOException If an I/O problem occurs.
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.buffer.flush(this.outputStream);
        }
    }

    @Override
    public void flush() throws IOException {
        this.buffer.flush(this.outputStream);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (this.closed) {
            throw new StreamClosedException();
        }
        if (this.total < this.contentLength) {
            final long max = this.contentLength - this.total;
            int chunk = len;
            if (chunk > max) {
                chunk = (int) max;
            }
            this.buffer.write(b, off, chunk, this.outputStream);
            this.total += chunk;
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final int b) throws IOException {
        if (this.closed) {
            throw new StreamClosedException();
        }
        if (this.total < this.contentLength) {
            this.buffer.write(b, this.outputStream);
            this.total++;
        }
    }

}
