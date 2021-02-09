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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import org.apache.hc.core5.http.Chars;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.HttpTransportMetrics;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Abstract base class for session input buffers that stream data from
 * an arbitrary {@link InputStream}. This class buffers input data in
 * an internal byte array for optimal input performance.
 * <p>
 * {@link #readLine(CharArrayBuffer, InputStream)} method of this class treat a lone
 * LF as valid line delimiters in addition to CR-LF required
 * by the HTTP specification.
 *
 * @since 4.3
 */
public class SessionInputBufferImpl implements SessionInputBuffer {

    private final BasicHttpTransportMetrics metrics;
    private final byte[] buffer;
    private final ByteArrayBuffer lineBuffer;
    private final int minChunkLimit;
    private final int maxLineLen;
    private final CharsetDecoder decoder;

    private int bufferPos;
    private int bufferLen;
    private CharBuffer cbuf;

    /**
     * Creates new instance of SessionInputBufferImpl.
     *
     * @param metrics HTTP transport metrics.
     * @param bufferSize buffer size. Must be a positive number.
     * @param minChunkLimit size limit below which data chunks should be buffered in memory
     *   in order to minimize native method invocations on the underlying network socket.
     *   The optimal value of this parameter can be platform specific and defines a trade-off
     *   between performance of memory copy operations and that of native method invocation.
     *   If negative default chunk limited will be used.
     * @param maxLineLen maximum line length.
     * @param charDecoder charDecoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     */
    public SessionInputBufferImpl(
            final BasicHttpTransportMetrics metrics,
            final int bufferSize,
            final int minChunkLimit,
            final int maxLineLen,
            final CharsetDecoder charDecoder) {
        Args.notNull(metrics, "HTTP transport metrics");
        Args.positive(bufferSize, "Buffer size");
        this.metrics = metrics;
        this.buffer = new byte[bufferSize];
        this.bufferPos = 0;
        this.bufferLen = 0;
        this.minChunkLimit = minChunkLimit >= 0 ? minChunkLimit : 512;
        this.maxLineLen = maxLineLen > 0 ? maxLineLen : 0;
        this.lineBuffer = new ByteArrayBuffer(bufferSize);
        this.decoder = charDecoder;
    }

    public SessionInputBufferImpl(
            final BasicHttpTransportMetrics metrics,
            final int bufferSize) {
        this(metrics, bufferSize, bufferSize, 0, null);
    }

    public SessionInputBufferImpl(final int bufferSize, final int maxLineLen) {
        this(new BasicHttpTransportMetrics(), bufferSize, bufferSize, maxLineLen, null);
    }

    public SessionInputBufferImpl(final int bufferSize, final CharsetDecoder decoder) {
        this(new BasicHttpTransportMetrics(), bufferSize, bufferSize, 0, decoder);
    }

    public SessionInputBufferImpl(final int bufferSize) {
        this(new BasicHttpTransportMetrics(), bufferSize, bufferSize, 0, null);
    }

    @Override
    public int capacity() {
        return this.buffer.length;
    }

    @Override
    public int length() {
        return this.bufferLen - this.bufferPos;
    }

    @Override
    public int available() {
        return capacity() - length();
    }

    public int fillBuffer(final InputStream inputStream) throws IOException {
        Args.notNull(inputStream, "Input stream");
        // compact the buffer if necessary
        if (this.bufferPos > 0) {
            final int len = this.bufferLen - this.bufferPos;
            if (len > 0) {
                System.arraycopy(this.buffer, this.bufferPos, this.buffer, 0, len);
            }
            this.bufferPos = 0;
            this.bufferLen = len;
        }
        final int readLen;
        final int off = this.bufferLen;
        final int len = this.buffer.length - off;
        readLen = inputStream.read(this.buffer, off, len);
        if (readLen == -1) {
            return -1;
        }
        this.bufferLen = off + readLen;
        this.metrics.incrementBytesTransferred(readLen);
        return readLen;
    }

    public boolean hasBufferedData() {
        return this.bufferPos < this.bufferLen;
    }

    public void clear() {
        this.bufferPos = 0;
        this.bufferLen = 0;
    }

    @Override
    public int read(final InputStream inputStream) throws IOException {
        Args.notNull(inputStream, "Input stream");
        int readLen;
        while (!hasBufferedData()) {
            readLen = fillBuffer(inputStream);
            if (readLen == -1) {
                return -1;
            }
        }
        return this.buffer[this.bufferPos++] & 0xff;
    }

    @Override
    public int read(final byte[] b, final int off, final int len, final InputStream inputStream) throws IOException {
        Args.notNull(inputStream, "Input stream");
        if (b == null) {
            return 0;
        }
        if (hasBufferedData()) {
            final int chunk = Math.min(len, this.bufferLen - this.bufferPos);
            System.arraycopy(this.buffer, this.bufferPos, b, off, chunk);
            this.bufferPos += chunk;
            return chunk;
        }
        // If the remaining capacity is big enough, read directly from the
        // underlying input stream bypassing the buffer.
        if (len > this.minChunkLimit) {
            final int read = inputStream.read(b, off, len);
            if (read > 0) {
                this.metrics.incrementBytesTransferred(read);
            }
            return read;
        }
        // otherwise read to the buffer first
        while (!hasBufferedData()) {
            final int readLen = fillBuffer(inputStream);
            if (readLen == -1) {
                return -1;
            }
        }
        final int chunk = Math.min(len, this.bufferLen - this.bufferPos);
        System.arraycopy(this.buffer, this.bufferPos, b, off, chunk);
        this.bufferPos += chunk;
        return chunk;
    }

    @Override
    public int read(final byte[] b, final InputStream inputStream) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length, inputStream);
    }

    /**
     * Reads a complete line of characters up to a line delimiter from this
     * session buffer into the given line buffer. The number of chars actually
     * read is returned as an integer. The line delimiter itself is discarded.
     * If no char is available because the end of the stream has been reached,
     * the value {@code -1} is returned. This method blocks until input
     * data is available, end of file is detected, or an exception is thrown.
     * <p>
     * This method treats a lone LF as a valid line delimiters in addition
     * to CR-LF required by the HTTP specification.
     *
     * @param     charBuffer   the line buffer, one line of characters upon return
     * @return     the total number of bytes read into the buffer, or
     *             {@code -1} is there is no more data because the end of
     *             the stream has been reached.
     * @throws  IOException  if an I/O error occurs.
     */
    @Override
    public int readLine(final CharArrayBuffer charBuffer, final InputStream inputStream) throws IOException {
        Args.notNull(charBuffer, "Char array buffer");
        Args.notNull(inputStream, "Input stream");
        int readLen = 0;
        boolean retry = true;
        while (retry) {
            // attempt to find end of line (LF)
            int pos = -1;
            for (int i = this.bufferPos; i < this.bufferLen; i++) {
                if (this.buffer[i] == Chars.LF) {
                    pos = i;
                    break;
                }
            }

            if (this.maxLineLen > 0) {
                final int currentLen = this.lineBuffer.length()
                        + (pos >= 0 ? pos : this.bufferLen) - this.bufferPos;
                if (currentLen >= this.maxLineLen) {
                    throw new MessageConstraintException("Maximum line length limit exceeded");
                }
            }

            if (pos != -1) {
                // end of line found.
                if (this.lineBuffer.isEmpty()) {
                    // the entire line is preset in the read buffer
                    return lineFromReadBuffer(charBuffer, pos);
                }
                retry = false;
                final int len = pos + 1 - this.bufferPos;
                this.lineBuffer.append(this.buffer, this.bufferPos, len);
                this.bufferPos = pos + 1;
            } else {
                // end of line not found
                if (hasBufferedData()) {
                    final int len = this.bufferLen - this.bufferPos;
                    this.lineBuffer.append(this.buffer, this.bufferPos, len);
                    this.bufferPos = this.bufferLen;
                }
                readLen = fillBuffer(inputStream);
                if (readLen == -1) {
                    retry = false;
                }
            }
        }
        if (readLen == -1 && this.lineBuffer.isEmpty()) {
            // indicate the end of stream
            return -1;
        }
        return lineFromLineBuffer(charBuffer);
    }

    /**
     * Reads a complete line of characters up to a line delimiter from this
     * session buffer. The line delimiter itself is discarded. If no char is
     * available because the end of the stream has been reached,
     * {@code null} is returned. This method blocks until input data is
     * available, end of file is detected, or an exception is thrown.
     * <p>
     * This method treats a lone LF as a valid line delimiters in addition
     * to CR-LF required by the HTTP specification.
     *
     * @return HTTP line as a string
     * @throws  IOException  if an I/O error occurs.
     */
    private int lineFromLineBuffer(final CharArrayBuffer charBuffer)
            throws IOException {
        // discard LF if found
        int len = this.lineBuffer.length();
        if (len > 0) {
            if (this.lineBuffer.byteAt(len - 1) == Chars.LF) {
                len--;
            }
            // discard CR if found
            if (len > 0 && this.lineBuffer.byteAt(len - 1) == Chars.CR) {
                len--;
            }
        }
        if (this.decoder == null) {
            charBuffer.append(this.lineBuffer, 0, len);
        } else {
            final ByteBuffer bbuf =  ByteBuffer.wrap(this.lineBuffer.array(), 0, len);
            len = appendDecoded(charBuffer, bbuf);
        }
        this.lineBuffer.clear();
        return len;
    }

    private int lineFromReadBuffer(final CharArrayBuffer charbuffer, final int position)
            throws IOException {
        int pos = position;
        final int off = this.bufferPos;
        int len;
        this.bufferPos = pos + 1;
        if (pos > off && this.buffer[pos - 1] == Chars.CR) {
            // skip CR if found
            pos--;
        }
        len = pos - off;
        if (this.decoder == null) {
            charbuffer.append(this.buffer, off, len);
        } else {
            final ByteBuffer bbuf =  ByteBuffer.wrap(this.buffer, off, len);
            len = appendDecoded(charbuffer, bbuf);
        }
        return len;
    }

    private int appendDecoded(
            final CharArrayBuffer charbuffer, final ByteBuffer bbuf) throws IOException {
        if (!bbuf.hasRemaining()) {
            return 0;
        }
        if (this.cbuf == null) {
            this.cbuf = CharBuffer.allocate(1024);
        }
        this.decoder.reset();
        int len = 0;
        while (bbuf.hasRemaining()) {
            final CoderResult result = this.decoder.decode(bbuf, this.cbuf, true);
            len += handleDecodingResult(result, charbuffer);
        }
        final CoderResult result = this.decoder.flush(this.cbuf);
        len += handleDecodingResult(result, charbuffer);
        this.cbuf.clear();
        return len;
    }

    private int handleDecodingResult(
            final CoderResult result,
            final CharArrayBuffer charBuffer) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
        this.cbuf.flip();
        final int len = this.cbuf.remaining();
        while (this.cbuf.hasRemaining()) {
            charBuffer.append(this.cbuf.get());
        }
        this.cbuf.compact();
        return len;
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return this.metrics;
    }

}
