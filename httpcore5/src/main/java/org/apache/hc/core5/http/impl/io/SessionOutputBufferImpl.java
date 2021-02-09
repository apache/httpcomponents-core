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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import org.apache.hc.core5.http.Chars;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.HttpTransportMetrics;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Abstract base class for session output buffers that stream data to
 * an arbitrary {@link OutputStream}. This class buffers small chunks of
 * output data in an internal byte array for optimal output performance.
 * <p>
 * {@link #writeLine(CharArrayBuffer, OutputStream)} method of this class uses CR-LF
 * as a line delimiter.
 *
 * @since 4.3
 */
public class SessionOutputBufferImpl implements SessionOutputBuffer {

    private static final byte[] CRLF = new byte[] {Chars.CR, Chars.LF};

    private final BasicHttpTransportMetrics metrics;
    private final ByteArrayBuffer buffer;
    private final int fragmentSizeHint;
    private final CharsetEncoder encoder;

    private ByteBuffer bbuf;

    /**
     * Creates new instance of SessionOutputBufferImpl.
     *
     * @param metrics HTTP transport metrics.
     * @param bufferSize buffer size. Must be a positive number.
     * @param fragmentSizeHint fragment size hint defining a minimal size of a fragment
     *   that should be written out directly to the socket bypassing the session buffer.
     *   Value {@code 0} disables fragment buffering.
     * @param charEncoder charEncoder to be used for encoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for char to byte conversion.
     */
    public SessionOutputBufferImpl(
            final BasicHttpTransportMetrics metrics,
            final int bufferSize,
            final int fragmentSizeHint,
            final CharsetEncoder charEncoder) {
        super();
        Args.positive(bufferSize, "Buffer size");
        Args.notNull(metrics, "HTTP transport metrics");
        this.metrics = metrics;
        this.buffer = new ByteArrayBuffer(bufferSize);
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : bufferSize;
        this.encoder = charEncoder;
    }

    public SessionOutputBufferImpl(final int bufferSize) {
        this(new BasicHttpTransportMetrics(), bufferSize, bufferSize, null);
    }

    public SessionOutputBufferImpl(final int bufferSize, final CharsetEncoder encoder) {
        this(new BasicHttpTransportMetrics(), bufferSize, bufferSize, encoder);
    }

    @Override
    public int capacity() {
        return this.buffer.capacity();
    }

    @Override
    public int length() {
        return this.buffer.length();
    }

    @Override
    public int available() {
        return capacity() - length();
    }

    private void flushBuffer(final OutputStream outputStream) throws IOException {
        final int len = this.buffer.length();
        if (len > 0) {
            outputStream.write(this.buffer.array(), 0, len);
            this.buffer.clear();
            this.metrics.incrementBytesTransferred(len);
        }
    }

    @Override
    public void flush(final OutputStream outputStream) throws IOException {
        Args.notNull(outputStream, "Output stream");
        flushBuffer(outputStream);
        outputStream.flush();
    }

    @Override
    public void write(final byte[] b, final int off, final int len, final OutputStream outputStream) throws IOException {
        if (b == null) {
            return;
        }
        Args.notNull(outputStream, "Output stream");
        // Do not want to buffer large-ish chunks
        // if the byte array is larger then MIN_CHUNK_LIMIT
        // write it directly to the output stream
        if (len > this.fragmentSizeHint || len > this.buffer.capacity()) {
            // flush the buffer
            flushBuffer(outputStream);
            // write directly to the out stream
            outputStream.write(b, off, len);
            this.metrics.incrementBytesTransferred(len);
        } else {
            // Do not let the buffer grow unnecessarily
            final int freecapacity = this.buffer.capacity() - this.buffer.length();
            if (len > freecapacity) {
                // flush the buffer
                flushBuffer(outputStream);
            }
            // buffer
            this.buffer.append(b, off, len);
        }
    }

    @Override
    public void write(final byte[] b, final OutputStream outputStream) throws IOException {
        if (b == null) {
            return;
        }
        write(b, 0, b.length, outputStream);
    }

    @Override
    public void write(final int b, final OutputStream outputStream) throws IOException {
        Args.notNull(outputStream, "Output stream");
        if (this.fragmentSizeHint > 0) {
            if (this.buffer.isFull()) {
                flushBuffer(outputStream);
            }
            this.buffer.append(b);
        } else {
            flushBuffer(outputStream);
            outputStream.write(b);
        }
    }

    /**
     * Writes characters from the specified char array followed by a line
     * delimiter to this session buffer.
     * <p>
     * This method uses CR-LF as a line delimiter.
     *
     * @param      charbuffer the buffer containing chars of the line.
     * @throws  IOException  if an I/O error occurs.
     */
    @Override
    public void writeLine(final CharArrayBuffer charbuffer, final OutputStream outputStream) throws IOException {
        if (charbuffer == null) {
            return;
        }
        Args.notNull(outputStream, "Output stream");
        if (this.encoder == null) {
            int off = 0;
            int remaining = charbuffer.length();
            while (remaining > 0) {
                int chunk = this.buffer.capacity() - this.buffer.length();
                chunk = Math.min(chunk, remaining);
                if (chunk > 0) {
                    this.buffer.append(charbuffer, off, chunk);
                }
                if (this.buffer.isFull()) {
                    flushBuffer(outputStream);
                }
                off += chunk;
                remaining -= chunk;
            }
        } else {
            final CharBuffer cbuf = CharBuffer.wrap(charbuffer.array(), 0, charbuffer.length());
            writeEncoded(cbuf, outputStream);
        }
        write(CRLF, outputStream);
    }

    private void writeEncoded(final CharBuffer cbuf, final OutputStream outputStream) throws IOException {
        if (!cbuf.hasRemaining()) {
            return;
        }
        if (this.bbuf == null) {
            this.bbuf = ByteBuffer.allocate(1024);
        }
        this.encoder.reset();
        while (cbuf.hasRemaining()) {
            final CoderResult result = this.encoder.encode(cbuf, this.bbuf, true);
            handleEncodingResult(result, outputStream);
        }
        final CoderResult result = this.encoder.flush(this.bbuf);
        handleEncodingResult(result, outputStream);
        this.bbuf.clear();
    }

    private void handleEncodingResult(final CoderResult result, final OutputStream outputStream) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
        this.bbuf.flip();
        while (this.bbuf.hasRemaining()) {
            write(this.bbuf.get(), outputStream);
        }
        this.bbuf.compact();
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return this.metrics;
    }

}
