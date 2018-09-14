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

package org.apache.http.impl.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.apache.http.Consts;
import org.apache.http.io.BufferInfo;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.CharArrayBuffer;

/**
 * Abstract base class for session input buffers that stream data from
 * an arbitrary {@link InputStream}. This class buffers input data in
 * an internal byte array for optimal input performance.
 * <p>
 * {@link #readLine(CharArrayBuffer)} and {@link #readLine()} methods of this
 * class treat a lone LF as valid line delimiters in addition to CR-LF required
 * by the HTTP specification.
 *
 * @since 4.0
 *
 * @deprecated (4.3) use {@link SessionInputBufferImpl}
 */
@Deprecated
public abstract class AbstractSessionInputBuffer implements SessionInputBuffer, BufferInfo {

    private InputStream inStream;
    private byte[] buffer;
    private ByteArrayBuffer lineBuffer;
    private Charset charset;
    private boolean ascii;
    private int maxLineLen;
    private int minChunkLimit;
    private HttpTransportMetricsImpl metrics;
    private CodingErrorAction onMalformedCharAction;
    private CodingErrorAction onUnmappableCharAction;

    private int bufferPos;
    private int bufferLen;
    private CharsetDecoder decoder;
    private CharBuffer cbuf;

    public AbstractSessionInputBuffer() {
    }

    /**
     * Initializes this session input buffer.
     *
     * @param inputStream the source input stream.
     * @param bufferSize the size of the internal buffer.
     * @param params HTTP parameters.
     */
    protected void init(final InputStream inputStream, final int bufferSize, final HttpParams params) {
        Args.notNull(inputStream, "Input stream");
        Args.notNegative(bufferSize, "Buffer size");
        Args.notNull(params, "HTTP parameters");
        this.inStream = inputStream;
        this.buffer = new byte[bufferSize];
        this.bufferPos = 0;
        this.bufferLen = 0;
        this.lineBuffer = new ByteArrayBuffer(bufferSize);
        final String charset = (String) params.getParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET);
        this.charset = charset != null ? Charset.forName(charset) : Consts.ASCII;
        this.ascii = this.charset.equals(Consts.ASCII);
        this.decoder = null;
        this.maxLineLen = params.getIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, -1);
        this.minChunkLimit = params.getIntParameter(CoreConnectionPNames.MIN_CHUNK_LIMIT, 512);
        this.metrics = createTransportMetrics();
        final CodingErrorAction a1 = (CodingErrorAction) params.getParameter(
                CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION);
        this.onMalformedCharAction = a1 != null ? a1 : CodingErrorAction.REPORT;
        final CodingErrorAction a2 = (CodingErrorAction) params.getParameter(
                CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION);
        this.onUnmappableCharAction = a2 != null? a2 : CodingErrorAction.REPORT;
    }

    /**
     * @since 4.1
     */
    protected HttpTransportMetricsImpl createTransportMetrics() {
        return new HttpTransportMetricsImpl();
    }

    /**
     * @since 4.1
     */
    @Override
    public int capacity() {
        return this.buffer.length;
    }

    /**
     * @since 4.1
     */
    @Override
    public int length() {
        return this.bufferLen - this.bufferPos;
    }

    /**
     * @since 4.1
     */
    @Override
    public int available() {
        return capacity() - length();
    }

    protected int fillBuffer() throws IOException {
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
        readLen = this.inStream.read(this.buffer, off, len);
        if (readLen == -1) {
            return -1;
        }
        this.bufferLen = off + readLen;
        this.metrics.incrementBytesTransferred(readLen);
        return readLen;
    }

    protected boolean hasBufferedData() {
        return this.bufferPos < this.bufferLen;
    }

    @Override
    public int read() throws IOException {
        int noRead;
        while (!hasBufferedData()) {
            noRead = fillBuffer();
            if (noRead == -1) {
                return -1;
            }
        }
        return this.buffer[this.bufferPos++] & 0xff;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
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
            final int read = this.inStream.read(b, off, len);
            if (read > 0) {
                this.metrics.incrementBytesTransferred(read);
            }
            return read;
        }
        // otherwise read to the buffer first
        while (!hasBufferedData()) {
            final int noRead = fillBuffer();
            if (noRead == -1) {
                return -1;
            }
        }
        final int chunk = Math.min(len, this.bufferLen - this.bufferPos);
        System.arraycopy(this.buffer, this.bufferPos, b, off, chunk);
        this.bufferPos += chunk;
        return chunk;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }

    private int locateLF() {
        for (int i = this.bufferPos; i < this.bufferLen; i++) {
            if (this.buffer[i] == HTTP.LF) {
                return i;
            }
        }
        return -1;
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
     * @param      charbuffer   the line buffer.
     * @return     one line of characters
     * @throws  IOException  if an I/O error occurs.
     */
    @Override
    public int readLine(final CharArrayBuffer charbuffer) throws IOException {
        Args.notNull(charbuffer, "Char array buffer");
        int noRead = 0;
        boolean retry = true;
        while (retry) {
            // attempt to find end of line (LF)
            final int i = locateLF();
            if (i != -1) {
                // end of line found.
                if (this.lineBuffer.isEmpty()) {
                    // the entire line is preset in the read buffer
                    return lineFromReadBuffer(charbuffer, i);
                }
                retry = false;
                final int len = i + 1 - this.bufferPos;
                this.lineBuffer.append(this.buffer, this.bufferPos, len);
                this.bufferPos = i + 1;
            } else {
                // end of line not found
                if (hasBufferedData()) {
                    final int len = this.bufferLen - this.bufferPos;
                    this.lineBuffer.append(this.buffer, this.bufferPos, len);
                    this.bufferPos = this.bufferLen;
                }
                noRead = fillBuffer();
                if (noRead == -1) {
                    retry = false;
                }
            }
            if (this.maxLineLen > 0 && this.lineBuffer.length() >= this.maxLineLen) {
                throw new IOException("Maximum line length limit exceeded");
            }
        }
        if (noRead == -1 && this.lineBuffer.isEmpty()) {
            // indicate the end of stream
            return -1;
        }
        return lineFromLineBuffer(charbuffer);
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
    private int lineFromLineBuffer(final CharArrayBuffer charbuffer)
            throws IOException {
        // discard LF if found
        int len = this.lineBuffer.length();
        if (len > 0) {
            if (this.lineBuffer.byteAt(len - 1) == HTTP.LF) {
                len--;
            }
            // discard CR if found
            if (len > 0) {
                if (this.lineBuffer.byteAt(len - 1) == HTTP.CR) {
                    len--;
                }
            }
        }
        if (this.ascii) {
            charbuffer.append(this.lineBuffer, 0, len);
        } else {
            final ByteBuffer bbuf =  ByteBuffer.wrap(this.lineBuffer.buffer(), 0, len);
            len = appendDecoded(charbuffer, bbuf);
        }
        this.lineBuffer.clear();
        return len;
    }

    private int lineFromReadBuffer(final CharArrayBuffer charbuffer, final int position)
            throws IOException {
        final int off = this.bufferPos;
        int i = position;
        this.bufferPos = i + 1;
        if (i > off && this.buffer[i - 1] == HTTP.CR) {
            // skip CR if found
            i--;
        }
        int len = i - off;
        if (this.ascii) {
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
        if (this.decoder == null) {
            this.decoder = this.charset.newDecoder();
            this.decoder.onMalformedInput(this.onMalformedCharAction);
            this.decoder.onUnmappableCharacter(this.onUnmappableCharAction);
        }
        if (this.cbuf == null) {
            this.cbuf = CharBuffer.allocate(1024);
        }
        this.decoder.reset();
        int len = 0;
        while (bbuf.hasRemaining()) {
            final CoderResult result = this.decoder.decode(bbuf, this.cbuf, true);
            len += handleDecodingResult(result, charbuffer, bbuf);
        }
        final CoderResult result = this.decoder.flush(this.cbuf);
        len += handleDecodingResult(result, charbuffer, bbuf);
        this.cbuf.clear();
        return len;
    }

    private int handleDecodingResult(
            final CoderResult result,
            final CharArrayBuffer charbuffer,
            final ByteBuffer bbuf) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
        this.cbuf.flip();
        final int len = this.cbuf.remaining();
        while (this.cbuf.hasRemaining()) {
            charbuffer.append(this.cbuf.get());
        }
        this.cbuf.compact();
        return len;
    }

    @Override
    public String readLine() throws IOException {
        final CharArrayBuffer charbuffer = new CharArrayBuffer(64);
        final int readLen = readLine(charbuffer);
        if (readLen != -1) {
            return charbuffer.toString();
        }
        return null;
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return this.metrics;
    }

}
