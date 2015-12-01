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

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.TrailerSupplier;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Implements chunked transfer coding. The content is sent in small chunks.
 * Entities transferred using this output stream can be of unlimited length.
 * Writes are buffered to an internal buffer (2048 default size).
 * <p>
 * Note that this class NEVER closes the underlying stream, even when close
 * gets called.  Instead, the stream will be marked as closed and no further
 * output will be permitted.
 *
 *
 * @since 4.0
 */
@NotThreadSafe
public class ChunkedOutputStream extends OutputStream {

    // ----------------------------------------------------- Instance Variables
    private final SessionOutputBuffer out;

    private final byte[] cache;

    private int cachePosition = 0;

    private boolean wroteLastChunk = false;

    /** True if the stream is closed. */
    private boolean closed = false;

    private final CharArrayBuffer linebuffer;

    private final TrailerSupplier trailers;

    /**
     * Wraps a session output buffer and chunk-encodes the output.
     *
     * @param bufferSize The minimum chunk size (excluding last chunk)
     * @param out The session output buffer
     *
     * @since 5.0
     */
    public ChunkedOutputStream(final int bufferSize, final SessionOutputBuffer out, final TrailerSupplier trailers) {
        super();
        this.cache = new byte[bufferSize];
        this.out = out;
        this.linebuffer = new CharArrayBuffer(32);
        this.trailers = trailers;
    }

    /**
     * Wraps a session output buffer and chunk-encodes the output.
     *
     * @param bufferSize The minimum chunk size (excluding last chunk)
     * @param out The session output buffer
     */
    public ChunkedOutputStream(final int bufferSize, final SessionOutputBuffer out) {
        this(bufferSize, out, null);
    }

    /**
     * Writes the cache out onto the underlying stream
     */
    protected void flushCache() throws IOException {
        if (this.cachePosition > 0) {
            this.linebuffer.clear();
            this.linebuffer.append(Integer.toHexString(this.cachePosition));
            this.out.writeLine(this.linebuffer);
            this.out.write(this.cache, 0, this.cachePosition);
            this.linebuffer.clear();
            this.out.writeLine(this.linebuffer);
            this.cachePosition = 0;
        }
    }

    /**
     * Writes the cache and bufferToAppend to the underlying stream
     * as one large chunk
     */
    protected void flushCacheWithAppend(final byte bufferToAppend[], final int off, final int len) throws IOException {
        this.linebuffer.clear();
        this.linebuffer.append(Integer.toHexString(this.cachePosition + len));
        this.out.writeLine(this.linebuffer);
        this.out.write(this.cache, 0, this.cachePosition);
        this.out.write(bufferToAppend, off, len);
        this.linebuffer.clear();
        this.out.writeLine(this.linebuffer);
        this.cachePosition = 0;
    }

    protected void writeClosingChunk() throws IOException {
        // Write the final chunk.
        this.linebuffer.clear();
        this.linebuffer.append('0');
        this.out.writeLine(this.linebuffer);
        writeTrailers();
        this.linebuffer.clear();
        this.out.writeLine(this.linebuffer);
    }

    private void writeTrailers() throws IOException {
        final Header[] headers = this.trailers != null ? this.trailers.get() : null;
        if (headers != null) {
            for (final Header header: headers) {
                if (header instanceof FormattedHeader) {
                    final CharArrayBuffer chbuffer = ((FormattedHeader) header).getBuffer();
                    this.out.writeLine(chbuffer);
                } else {
                    this.linebuffer.clear();
                    BasicLineFormatter.INSTANCE.formatHeader(this.linebuffer, header);
                    this.out.writeLine(this.linebuffer);
                }
            }
        }
    }

    // ----------------------------------------------------------- Public Methods
    /**
     * Must be called to ensure the internal cache is flushed and the closing
     * chunk is written.
     * @throws IOException in case of an I/O error
     */
    public void finish() throws IOException {
        if (!this.wroteLastChunk) {
            flushCache();
            writeClosingChunk();
            this.wroteLastChunk = true;
        }
    }

    // -------------------------------------------- OutputStream Methods
    @Override
    public void write(final int b) throws IOException {
        if (this.closed) {
            throw new IOException("Attempted write to closed stream.");
        }
        this.cache[this.cachePosition] = (byte) b;
        this.cachePosition++;
        if (this.cachePosition == this.cache.length) {
            flushCache();
        }
    }

    /**
     * Writes the array. If the array does not fit within the buffer, it is
     * not split, but rather written out as one large chunk.
     */
    @Override
    public void write(final byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes the array. If the array does not fit within the buffer, it is
     * not split, but rather written out as one large chunk.
     */
    @Override
    public void write(final byte src[], final int off, final int len) throws IOException {
        if (this.closed) {
            throw new IOException("Attempted write to closed stream.");
        }
        if (len >= this.cache.length - this.cachePosition) {
            flushCacheWithAppend(src, off, len);
        } else {
            System.arraycopy(src, off, cache, this.cachePosition, len);
            this.cachePosition += len;
        }
    }

    /**
     * Flushes the content buffer and the underlying stream.
     */
    @Override
    public void flush() throws IOException {
        flushCache();
        this.out.flush();
    }

    /**
     * Finishes writing to the underlying stream, but does NOT close the underlying stream.
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            finish();
            this.out.flush();
        }
    }
}
