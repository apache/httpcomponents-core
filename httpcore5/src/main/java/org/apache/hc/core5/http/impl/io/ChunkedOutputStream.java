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
import java.util.List;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Implements chunked transfer coding. The content is sent in small chunks.
 * Entities transferred using this output stream can be of unlimited length.
 * Writes are buffered to an internal buffer (2048 default size).
 * <p>
 * Note that this class NEVER closes the underlying stream, even when
 * {@link #close()} gets called.  Instead, the stream will be marked as
 * closed and no further output will be permitted.
 *
 *
 * @since 4.0
 */
public class ChunkedOutputStream extends OutputStream {

    private final SessionOutputBuffer buffer;
    private final OutputStream outputStream;

    private final byte[] cache;
    private int cachePosition;
    private boolean wroteLastChunk;
    private boolean closed;
    private final CharArrayBuffer lineBuffer;
    private final Supplier<List<? extends Header>> trailerSupplier;

    /**
     * Default constructor.
     *
     * @param buffer Session output buffer
     * @param outputStream Output stream
     * @param chunkCache Buffer used to aggregate smaller writes into chunks.
     * @param trailerSupplier Trailer supplier. May be {@code null}
     *
     * @since 5.1
     */
    public ChunkedOutputStream(
            final SessionOutputBuffer buffer,
            final OutputStream outputStream,
            final byte[] chunkCache,
            final Supplier<List<? extends Header>> trailerSupplier) {
        super();
        this.buffer = Args.notNull(buffer, "Session output buffer");
        this.outputStream = Args.notNull(outputStream, "Output stream");
        this.cache = Args.notNull(chunkCache, "Chunk cache");
        this.lineBuffer = new CharArrayBuffer(32);
        this.trailerSupplier = trailerSupplier;
    }

    /**
     * Constructor taking an integer chunk size hint.
     *
     * @param buffer Session output buffer
     * @param outputStream Output stream
     * @param chunkSizeHint minimal chunk size hint
     * @param trailerSupplier Trailer supplier. May be {@code null}
     *
     * @since 5.0
     */
    public ChunkedOutputStream(
            final SessionOutputBuffer buffer,
            final OutputStream outputStream,
            final int chunkSizeHint,
            final Supplier<List<? extends Header>> trailerSupplier) {
        this(buffer, outputStream, new byte[chunkSizeHint > 0 ? chunkSizeHint : 8192], trailerSupplier);
    }

    /**
     * Constructor with no trailers.
     *
     * @param buffer Session output buffer
     * @param outputStream Output stream
     * @param chunkSizeHint minimal chunk size hint
     */
    public ChunkedOutputStream(final SessionOutputBuffer buffer, final OutputStream outputStream, final int chunkSizeHint) {
        this(buffer, outputStream, chunkSizeHint, null);
    }

    /**
     * Writes the cache out onto the underlying stream
     */
    private void flushCache() throws IOException {
        if (this.cachePosition > 0) {
            this.lineBuffer.clear();
            this.lineBuffer.append(Integer.toHexString(this.cachePosition));
            this.buffer.writeLine(this.lineBuffer, this.outputStream);
            this.buffer.write(this.cache, 0, this.cachePosition, this.outputStream);
            this.lineBuffer.clear();
            this.buffer.writeLine(this.lineBuffer, this.outputStream);
            this.cachePosition = 0;
        }
    }

    /**
     * Writes the cache and bufferToAppend to the underlying stream
     * as one large chunk
     */
    private void flushCacheWithAppend(final byte[] bufferToAppend, final int off, final int len) throws IOException {
        this.lineBuffer.clear();
        this.lineBuffer.append(Integer.toHexString(this.cachePosition + len));
        this.buffer.writeLine(this.lineBuffer, this.outputStream);
        this.buffer.write(this.cache, 0, this.cachePosition, this.outputStream);
        this.buffer.write(bufferToAppend, off, len, this.outputStream);
        this.lineBuffer.clear();
        this.buffer.writeLine(this.lineBuffer, this.outputStream);
        this.cachePosition = 0;
    }

    private void writeClosingChunk() throws IOException {
        // Write the final chunk.
        this.lineBuffer.clear();
        this.lineBuffer.append('0');
        this.buffer.writeLine(this.lineBuffer, this.outputStream);
        writeTrailers();
        this.lineBuffer.clear();
        this.buffer.writeLine(this.lineBuffer, this.outputStream);
    }

    private void writeTrailers() throws IOException {
        final List<? extends Header> trailers = this.trailerSupplier != null ? this.trailerSupplier.get() : null;
        if (trailers != null) {
            for (int i = 0; i < trailers.size(); i++) {
                final Header header = trailers.get(i);
                if (header instanceof FormattedHeader) {
                    final CharArrayBuffer chbuffer = ((FormattedHeader) header).getBuffer();
                    this.buffer.writeLine(chbuffer, this.outputStream);
                } else {
                    this.lineBuffer.clear();
                    BasicLineFormatter.INSTANCE.formatHeader(this.lineBuffer, header);
                    this.buffer.writeLine(this.lineBuffer, this.outputStream);
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
            throw new StreamClosedException();
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
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes the array. If the array does not fit within the buffer, it is
     * not split, but rather written out as one large chunk.
     */
    @Override
    public void write(final byte[] src, final int off, final int len) throws IOException {
        if (this.closed) {
            throw new StreamClosedException();
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
        this.buffer.flush(this.outputStream);
    }

    /**
     * Finishes writing to the underlying stream, but does NOT close the underlying stream.
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            finish();
            this.buffer.flush(this.outputStream);
        }
    }
}
