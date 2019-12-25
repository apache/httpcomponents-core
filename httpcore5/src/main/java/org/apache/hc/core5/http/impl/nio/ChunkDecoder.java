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
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.TruncatedChunkException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Implements chunked transfer decoding. The content is received in small chunks.
 * Entities transferred using this encoder can be of unlimited length.
 *
 * @since 4.0
 */
public class ChunkDecoder extends AbstractContentDecoder {

    private enum State {
        READ_CONTENT, READ_FOOTERS, COMPLETED
    }

    private State state;
    private boolean endOfChunk;
    private boolean endOfStream;

    private CharArrayBuffer lineBuf;
    private long chunkSize;
    private long pos;

    private final Http1Config http1Config;
    private final List<CharArrayBuffer> trailerBufs;
    private final List<Header> trailers;

    /**
     * @since 4.4
     */
    public ChunkDecoder(
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final Http1Config http1Config,
            final BasicHttpTransportMetrics metrics) {
        super(channel, buffer, metrics);
        this.state = State.READ_CONTENT;
        this.chunkSize = -1L;
        this.pos = 0L;
        this.endOfChunk = false;
        this.endOfStream = false;
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.trailerBufs = new ArrayList<>();
        this.trailers = new ArrayList<>();
    }

    public ChunkDecoder(
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final BasicHttpTransportMetrics metrics) {
        this(channel, buffer, null, metrics);
    }

    private void readChunkHead() throws IOException {
        if (this.lineBuf == null) {
            this.lineBuf = new CharArrayBuffer(32);
        } else {
            this.lineBuf.clear();
        }
        if (this.endOfChunk) {
            if (this.buffer.readLine(this.lineBuf, this.endOfStream)) {
                if (!this.lineBuf.isEmpty()) {
                    throw new MalformedChunkCodingException("CRLF expected at end of chunk");
                }
            } else {
                if (this.buffer.length() > 2 || this.endOfStream) {
                    throw new MalformedChunkCodingException("CRLF expected at end of chunk");
                }
                return;
            }
            this.endOfChunk = false;
        }
        final boolean lineComplete = this.buffer.readLine(this.lineBuf, this.endOfStream);
        final int maxLineLen = this.http1Config.getMaxLineLength();
        if (maxLineLen > 0 &&
                (this.lineBuf.length() > maxLineLen ||
                        (!lineComplete && this.buffer.length() > maxLineLen))) {
            throw new MessageConstraintException("Maximum line length limit exceeded");
        }
        if (lineComplete) {
            int separator = this.lineBuf.indexOf(';');
            if (separator < 0) {
                separator = this.lineBuf.length();
            }
            final String s = this.lineBuf.substringTrimmed(0, separator);
            try {
                this.chunkSize = Long.parseLong(s, 16);
            } catch (final NumberFormatException e) {
                throw new MalformedChunkCodingException("Bad chunk header: " + s);
            }
            this.pos = 0L;
        } else if (this.endOfStream) {
            throw new ConnectionClosedException(
                            "Premature end of chunk coded message body: closing chunk expected");
        }
    }

    private void parseHeader() throws IOException {
        final CharArrayBuffer current = this.lineBuf;
        final int count = this.trailerBufs.size();
        if ((this.lineBuf.charAt(0) == ' ' || this.lineBuf.charAt(0) == '\t') && count > 0) {
            // Handle folded header line
            final CharArrayBuffer previous = this.trailerBufs.get(count - 1);
            int i = 0;
            while (i < current.length()) {
                final char ch = current.charAt(i);
                if (ch != ' ' && ch != '\t') {
                    break;
                }
                i++;
            }
            final int maxLineLen = this.http1Config.getMaxLineLength();
            if (maxLineLen > 0 && previous.length() + 1 + current.length() - i > maxLineLen) {
                throw new MessageConstraintException("Maximum line length limit exceeded");
            }
            previous.append(' ');
            previous.append(current, i, current.length() - i);
        } else {
            this.trailerBufs.add(current);
            this.lineBuf = null;
        }
    }

    private void processFooters() throws IOException {
        final int count = this.trailerBufs.size();
        if (count > 0) {
            this.trailers.clear();
            for (int i = 0; i < this.trailerBufs.size(); i++) {
                try {
                    this.trailers.add(new BufferedHeader(this.trailerBufs.get(i)));
                } catch (final ParseException ex) {
                    throw new IOException(ex);
                }
            }
        }
        this.trailerBufs.clear();
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        Args.notNull(dst, "Byte buffer");
        if (this.state == State.COMPLETED) {
            return -1;
        }

        int totalRead = 0;
        while (this.state != State.COMPLETED) {

            if (!this.buffer.hasData() || this.chunkSize == -1L) {
                final int bytesRead = fillBufferFromChannel();
                if (bytesRead == -1) {
                    this.endOfStream = true;
                }
            }

            switch (this.state) {
            case READ_CONTENT:

                if (this.chunkSize == -1L) {
                    readChunkHead();
                    if (this.chunkSize == -1L) {
                        // Unable to read a chunk head
                        return totalRead;
                    }
                    if (this.chunkSize == 0L) {
                        // Last chunk. Read footers
                        this.chunkSize = -1L;
                        this.state = State.READ_FOOTERS;
                        break;
                    }
                }
                final long maxLen = this.chunkSize - this.pos;
                final int len = this.buffer.read(dst, (int) Math.min(maxLen, Integer.MAX_VALUE));
                if (len > 0) {
                    this.pos += len;
                    totalRead += len;
                } else {
                    if (!this.buffer.hasData() && this.endOfStream) {
                        this.state = State.COMPLETED;
                        setCompleted();
                        throw new TruncatedChunkException(
                                        "Truncated chunk (expected size: %d; actual size: %d)",
                                        chunkSize, pos);
                    }
                }

                if (this.pos == this.chunkSize) {
                    // At the end of the chunk
                    this.chunkSize = -1L;
                    this.pos = 0L;
                    this.endOfChunk = true;
                    break;
                }
                return totalRead;
            case READ_FOOTERS:
                if (this.lineBuf == null) {
                    this.lineBuf = new CharArrayBuffer(32);
                } else {
                    this.lineBuf.clear();
                }
                if (!this.buffer.readLine(this.lineBuf, this.endOfStream)) {
                    // Unable to read a footer
                    if (this.endOfStream) {
                        this.state = State.COMPLETED;
                        setCompleted();
                    }
                    return totalRead;
                }
                if (this.lineBuf.length() > 0) {
                    final int maxHeaderCount = this.http1Config.getMaxHeaderCount();
                    if (maxHeaderCount > 0 && trailerBufs.size() >= maxHeaderCount) {
                        throw new MessageConstraintException("Maximum header count exceeded");
                    }
                    parseHeader();
                } else {
                    this.state = State.COMPLETED;
                    setCompleted();
                    processFooters();
                }
                break;
            }

        }
        return totalRead;
    }

    @Override
    public List<? extends Header> getTrailers() {
        return this.trailers.isEmpty() ? null : new ArrayList<>(this.trailers);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[chunk-coded; completed: ");
        sb.append(this.completed);
        sb.append("]");
        return sb.toString();
    }

}
