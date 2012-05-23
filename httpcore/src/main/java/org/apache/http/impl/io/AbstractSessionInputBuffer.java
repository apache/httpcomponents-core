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

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.io.BufferInfo;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
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
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MIN_CHUNK_LIMIT}</li>
 * </ul>
 * @since 4.0
 */
@NotThreadSafe
public abstract class AbstractSessionInputBuffer implements SessionInputBuffer, BufferInfo {

    private static final Charset ASCII = Charset.forName("US-ASCII");

    private InputStream instream;
    private byte[] buffer;
    private int bufferpos;
    private int bufferlen;

    private ByteArrayBuffer linebuffer = null;

    private Charset charset;
    private CharsetDecoder decoder;
    private CharBuffer cbuf;
    private boolean ascii = true;
    private int maxLineLen = -1;
    private int minChunkLimit = 512;

    private HttpTransportMetricsImpl metrics;

    private CodingErrorAction onMalformedInputAction;
    private CodingErrorAction onUnMappableInputAction;

    /**
     * Initializes this session input buffer.
     *
     * @param instream the source input stream.
     * @param buffersize the size of the internal buffer.
     * @param params HTTP parameters.
     */
    protected void init(final InputStream instream, int buffersize, final HttpParams params) {
        if (instream == null) {
            throw new IllegalArgumentException("Input stream may not be null");
        }
        if (buffersize <= 0) {
            throw new IllegalArgumentException("Buffer size may not be negative or zero");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.instream = instream;
        this.buffer = new byte[buffersize];
        this.bufferpos = 0;
        this.bufferlen = 0;
        this.linebuffer = new ByteArrayBuffer(buffersize);
        this.charset = Charset.forName(HttpProtocolParams.getHttpElementCharset(params));
        this.ascii = this.charset.equals(ASCII);
        this.decoder = null;
        this.maxLineLen = params.getIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, -1);
        this.minChunkLimit = params.getIntParameter(CoreConnectionPNames.MIN_CHUNK_LIMIT, 512);
        this.metrics = createTransportMetrics();
        this.onMalformedInputAction = HttpProtocolParams.getMalformedInputAction(params);
        this.onUnMappableInputAction = HttpProtocolParams.getUnmappableInputAction(params);
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
    public int capacity() {
        return this.buffer.length;
    }

    /**
     * @since 4.1
     */
    public int length() {
        return this.bufferlen - this.bufferpos;
    }

    /**
     * @since 4.1
     */
    public int available() {
        return capacity() - length();
    }

    protected int fillBuffer() throws IOException {
        // compact the buffer if necessary
        if (this.bufferpos > 0) {
            int len = this.bufferlen - this.bufferpos;
            if (len > 0) {
                System.arraycopy(this.buffer, this.bufferpos, this.buffer, 0, len);
            }
            this.bufferpos = 0;
            this.bufferlen = len;
        }
        int l;
        int off = this.bufferlen;
        int len = this.buffer.length - off;
        l = this.instream.read(this.buffer, off, len);
        if (l == -1) {
            return -1;
        } else {
            this.bufferlen = off + l;
            this.metrics.incrementBytesTransferred(l);
            return l;
        }
    }

    protected boolean hasBufferedData() {
        return this.bufferpos < this.bufferlen;
    }

    public int read() throws IOException {
        int noRead = 0;
        while (!hasBufferedData()) {
            noRead = fillBuffer();
            if (noRead == -1) {
                return -1;
            }
        }
        return this.buffer[this.bufferpos++] & 0xff;
    }

    public int read(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return 0;
        }
        if (hasBufferedData()) {
            int chunk = Math.min(len, this.bufferlen - this.bufferpos);
            System.arraycopy(this.buffer, this.bufferpos, b, off, chunk);
            this.bufferpos += chunk;
            return chunk;
        }
        // If the remaining capacity is big enough, read directly from the
        // underlying input stream bypassing the buffer.
        if (len > this.minChunkLimit) {
            int read = this.instream.read(b, off, len);
            if (read > 0) {
                this.metrics.incrementBytesTransferred(read);
            }
            return read;
        } else {
            // otherwise read to the buffer first
            while (!hasBufferedData()) {
                int noRead = fillBuffer();
                if (noRead == -1) {
                    return -1;
                }
            }
            int chunk = Math.min(len, this.bufferlen - this.bufferpos);
            System.arraycopy(this.buffer, this.bufferpos, b, off, chunk);
            this.bufferpos += chunk;
            return chunk;
        }
    }

    public int read(final byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }

    private int locateLF() {
        for (int i = this.bufferpos; i < this.bufferlen; i++) {
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
     * the value <code>-1</code> is returned. This method blocks until input
     * data is available, end of file is detected, or an exception is thrown.
     * <p>
     * This method treats a lone LF as a valid line delimiters in addition
     * to CR-LF required by the HTTP specification.
     *
     * @param      charbuffer   the line buffer.
     * @return     one line of characters
     * @exception  IOException  if an I/O error occurs.
     */
    public int readLine(final CharArrayBuffer charbuffer) throws IOException {
        if (charbuffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
        }
        int noRead = 0;
        boolean retry = true;
        while (retry) {
            // attempt to find end of line (LF)
            int i = locateLF();
            if (i != -1) {
                // end of line found.
                if (this.linebuffer.isEmpty()) {
                    // the entire line is preset in the read buffer
                    return lineFromReadBuffer(charbuffer, i);
                }
                retry = false;
                int len = i + 1 - this.bufferpos;
                this.linebuffer.append(this.buffer, this.bufferpos, len);
                this.bufferpos = i + 1;
            } else {
                // end of line not found
                if (hasBufferedData()) {
                    int len = this.bufferlen - this.bufferpos;
                    this.linebuffer.append(this.buffer, this.bufferpos, len);
                    this.bufferpos = this.bufferlen;
                }
                noRead = fillBuffer();
                if (noRead == -1) {
                    retry = false;
                }
            }
            if (this.maxLineLen > 0 && this.linebuffer.length() >= this.maxLineLen) {
                throw new IOException("Maximum line length limit exceeded");
            }
        }
        if (noRead == -1 && this.linebuffer.isEmpty()) {
            // indicate the end of stream
            return -1;
        }
        return lineFromLineBuffer(charbuffer);
    }

    /**
     * Reads a complete line of characters up to a line delimiter from this
     * session buffer. The line delimiter itself is discarded. If no char is
     * available because the end of the stream has been reached,
     * <code>null</code> is returned. This method blocks until input data is
     * available, end of file is detected, or an exception is thrown.
     * <p>
     * This method treats a lone LF as a valid line delimiters in addition
     * to CR-LF required by the HTTP specification.
     *
     * @return HTTP line as a string
     * @exception  IOException  if an I/O error occurs.
     */
    private int lineFromLineBuffer(final CharArrayBuffer charbuffer)
            throws IOException {
        // discard LF if found
        int len = this.linebuffer.length();
        if (len > 0) {
            if (this.linebuffer.byteAt(len - 1) == HTTP.LF) {
                len--;
            }
            // discard CR if found
            if (len > 0) {
                if (this.linebuffer.byteAt(len - 1) == HTTP.CR) {
                    len--;
                }
            }
        }
        if (this.ascii) {
            charbuffer.append(this.linebuffer, 0, len);
        } else {
            ByteBuffer bbuf =  ByteBuffer.wrap(this.linebuffer.buffer(), 0, len);
            len = appendDecoded(charbuffer, bbuf);
        }
        this.linebuffer.clear();
        return len;
    }

    private int lineFromReadBuffer(final CharArrayBuffer charbuffer, int pos)
            throws IOException {
        int off = this.bufferpos;
        int len;
        this.bufferpos = pos + 1;
        if (pos > off && this.buffer[pos - 1] == HTTP.CR) {
            // skip CR if found
            pos--;
        }
        len = pos - off;
        if (this.ascii) {
            charbuffer.append(this.buffer, off, len);
        } else {
            ByteBuffer bbuf =  ByteBuffer.wrap(this.buffer, off, len);
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
            this.decoder.onMalformedInput(this.onMalformedInputAction);
            this.decoder.onUnmappableCharacter(this.onUnMappableInputAction);
        }
        if (this.cbuf == null) {
            this.cbuf = CharBuffer.allocate(1024);
        }
        this.decoder.reset();
        int len = 0;
        while (bbuf.hasRemaining()) {
            CoderResult result = this.decoder.decode(bbuf, this.cbuf, true);
            len += handleDecodingResult(result, charbuffer, bbuf);
        }
        CoderResult result = this.decoder.flush(this.cbuf);
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
        int len = this.cbuf.remaining();
        while (this.cbuf.hasRemaining()) {
            charbuffer.append(this.cbuf.get());
        }
        this.cbuf.compact();
        return len;
    }

    public String readLine() throws IOException {
        CharArrayBuffer charbuffer = new CharArrayBuffer(64);
        int l = readLine(charbuffer);
        if (l != -1) {
            return charbuffer.toString();
        } else {
            return null;
        }
    }

    public HttpTransportMetrics getMetrics() {
        return this.metrics;
    }

}
