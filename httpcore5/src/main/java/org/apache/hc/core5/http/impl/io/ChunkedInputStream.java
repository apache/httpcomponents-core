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

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.TruncatedChunkException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Implements chunked transfer coding. The content is received in small chunks.
 * Entities transferred using this input stream can be of unlimited length.
 * After the stream is read to the end, it provides access to the trailers,
 * if any.
 * <p>
 * Note that this class NEVER closes the underlying stream, even when
 * {@link #close()} gets called.  Instead, it will read until the "end" of its
 * chunking on close, which allows for the seamless execution of subsequent
 * HTTP 1.1 requests, while not requiring the client to remember to read the
 * entire contents of the response.
 *
 *
 * @since 4.0
 *
 */
public class ChunkedInputStream extends InputStream {

    private enum State {
        CHUNK_LEN, CHUNK_DATA, CHUNK_CRLF, CHUNK_INVALID
    }

    private static final int BUFFER_SIZE = 2048;
    private static final Header[] EMPTY_FOOTERS = new Header[0];

    /** The session input buffer */
    private final SessionInputBuffer buffer;
    private final InputStream inputStream;
    private final CharArrayBuffer lineBuffer;
    private final Http1Config http1Config;

    private State state;

    /** The chunk size */
    private long chunkSize;

    /** The current position within the current chunk */
    private long pos;

    /** True if we've reached the end of stream */
    private boolean eof;

    /** True if this stream is closed */
    private boolean closed;

    private Header[] footers = EMPTY_FOOTERS;

    /**
     * Default constructor.
     *
     * @param buffer Session input buffer
     * @param inputStream Input stream
     * @param http1Config Message http1Config. If {@code null} {@link Http1Config#DEFAULT} will be used.
     *
     * @since 4.4
     */
    public ChunkedInputStream(final SessionInputBuffer buffer, final InputStream inputStream, final Http1Config http1Config) {
        super();
        this.buffer = Args.notNull(buffer, "Session input buffer");
        this.inputStream = Args.notNull(inputStream, "Input stream");
        this.pos = 0L;
        this.lineBuffer = new CharArrayBuffer(16);
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.state = State.CHUNK_LEN;
    }

    /**
     * Wraps session input stream and reads chunk coded input.
     *
     * @param buffer Session input buffer
     * @param inputStream Input stream
     */
    public ChunkedInputStream(final SessionInputBuffer buffer, final InputStream inputStream) {
        this(buffer, inputStream, null);
    }

    @Override
    public int available() throws IOException {
        final int len = this.buffer.length();
        return (int) Math.min(len, this.chunkSize - this.pos);
    }

    /**
     * <p> Returns all the data in a chunked stream in coalesced form. A chunk
     * is followed by a CRLF. The method returns -1 as soon as a chunksize of 0
     * is detected.</p>
     *
     * <p> Trailer headers are read automatically at the end of the stream and
     * can be obtained with the getResponseFooters() method.</p>
     *
     * @return -1 of the end of the stream has been reached or the next data
     * byte
     * @throws IOException in case of an I/O error
     */
    @Override
    public int read() throws IOException {
        if (this.closed) {
            throw new StreamClosedException();
        }
        if (this.eof) {
            return -1;
        }
        if (state != State.CHUNK_DATA) {
            nextChunk();
            if (this.eof) {
                return -1;
            }
        }
        final int b = buffer.read(inputStream);
        if (b != -1) {
            pos++;
            if (pos >= chunkSize) {
                state = State.CHUNK_CRLF;
            }
        }
        return b;
    }

    /**
     * Read some bytes from the stream.
     * @param b The byte array that will hold the contents from the stream.
     * @param off The offset into the byte array at which bytes will start to be
     * placed.
     * @param len the maximum number of bytes that can be returned.
     * @return The number of bytes returned or -1 if the end of stream has been
     * reached.
     * @throws IOException in case of an I/O error
     */
    @Override
    public int read (final byte[] b, final int off, final int len) throws IOException {

        if (closed) {
            throw new StreamClosedException();
        }

        if (eof) {
            return -1;
        }
        if (state != State.CHUNK_DATA) {
            nextChunk();
            if (eof) {
                return -1;
            }
        }
        final int bytesRead = buffer.read(b, off, (int) Math.min(len, chunkSize - pos), inputStream);
        if (bytesRead != -1) {
            pos += bytesRead;
            if (pos >= chunkSize) {
                state = State.CHUNK_CRLF;
            }
            return bytesRead;
        }
        eof = true;
        throw new TruncatedChunkException("Truncated chunk (expected size: %d; actual size: %d)",
                        chunkSize, pos);
    }

    /**
     * Read some bytes from the stream.
     * @param b The byte array that will hold the contents from the stream.
     * @return The number of bytes returned or -1 if the end of stream has been
     * reached.
     * @throws IOException in case of an I/O error
     */
    @Override
    public int read (final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Read the next chunk.
     * @throws IOException in case of an I/O error
     */
    private void nextChunk() throws IOException {
        if (state == State.CHUNK_INVALID) {
            throw new MalformedChunkCodingException("Corrupt data stream");
        }
        try {
            chunkSize = getChunkSize();
            if (chunkSize < 0L) {
                throw new MalformedChunkCodingException("Negative chunk size");
            }
            state = State.CHUNK_DATA;
            pos = 0L;
            if (chunkSize == 0L) {
                eof = true;
                parseTrailerHeaders();
            }
        } catch (final MalformedChunkCodingException ex) {
            state = State.CHUNK_INVALID;
            throw ex;
        }
    }

    /**
     * Expects the stream to start with a chunksize in hex with optional
     * comments after a semicolon. The line must end with a CRLF: "a3; some
     * comment\r\n" Positions the stream at the start of the next line.
     */
    private long getChunkSize() throws IOException {
        final State st = this.state;
        switch (st) {
        case CHUNK_CRLF:
            lineBuffer.clear();
            final int bytesRead1 = this.buffer.readLine(lineBuffer, inputStream);
            if (bytesRead1 == -1) {
                throw new MalformedChunkCodingException(
                    "CRLF expected at end of chunk");
            }
            if (!lineBuffer.isEmpty()) {
                throw new MalformedChunkCodingException(
                    "Unexpected content at the end of chunk");
            }
            state = State.CHUNK_LEN;
            //$FALL-THROUGH$
        case CHUNK_LEN:
            lineBuffer.clear();
            final int bytesRead2 = this.buffer.readLine(lineBuffer, inputStream);
            if (bytesRead2 == -1) {
                throw new ConnectionClosedException(
                                "Premature end of chunk coded message body: closing chunk expected");
            }
            int separator = lineBuffer.indexOf(';');
            if (separator < 0) {
                separator = lineBuffer.length();
            }
            final String s = this.lineBuffer.substringTrimmed(0, separator);
            try {
                return Long.parseLong(s, 16);
            } catch (final NumberFormatException e) {
                throw new MalformedChunkCodingException("Bad chunk header: " + s);
            }
        default:
            throw new IllegalStateException("Inconsistent codec state");
        }
    }

    /**
     * Reads and stores the Trailer headers.
     * @throws IOException in case of an I/O error
     */
    private void parseTrailerHeaders() throws IOException {
        try {
            this.footers = AbstractMessageParser.parseHeaders(buffer, inputStream,
                    http1Config.getMaxHeaderCount(),
                    http1Config.getMaxLineLength(),
                    null);
        } catch (final HttpException ex) {
            final IOException ioe = new MalformedChunkCodingException("Invalid trailing header: "
                    + ex.getMessage());
            ioe.initCause(ex);
            throw ioe;
        }
    }

    /**
     * Reads the remainder of the chunked message, leaving the underlying
     * stream at a position to start reading the next response without
     * scanning. But does NOT close the underlying stream.
     * @throws IOException in case of an I/O error
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                if (!eof && state != State.CHUNK_INVALID) {
                    // Optimistically check if the content has been fully read
                    // when there's no data remaining in the current chunk.
                    // This is common when self-terminating content (e.g. JSON)
                    // is parsed from response streams.
                    if (chunkSize == pos && chunkSize > 0 && read() == -1) {
                        return;
                    }
                    // read and discard the remainder of the message
                    final byte[] buff = new byte[BUFFER_SIZE];
                    while (read(buff) >= 0) {
                    }
                }
            } finally {
                eof = true;
                closed = true;
            }
        }
    }

    public Header[] getFooters() {
        return footers.length > 0 ? footers.clone() : EMPTY_FOOTERS;
    }

}
