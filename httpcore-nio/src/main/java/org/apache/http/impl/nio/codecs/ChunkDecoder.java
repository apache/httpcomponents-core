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
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.MessageConstraintException;
import org.apache.http.ParseException;
import org.apache.http.TruncatedChunkException;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.message.BufferedHeader;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * Implements chunked transfer coding. The content is received in small chunks.
 * Entities transferred using this encoder can be of unlimited length.
 *
 * @since 4.0
 */
@NotThreadSafe
public class ChunkDecoder extends AbstractContentDecoder {

    private static final int READ_CONTENT   = 0;
    private static final int READ_FOOTERS  = 1;
    private static final int COMPLETED      = 2;

    private int state;
    private boolean endOfChunk;
    private boolean endOfStream;

    private CharArrayBuffer lineBuf;
    private int chunkSize;
    private int pos;

    private final MessageConstraints constraints;
    private final List<CharArrayBuffer> trailerBufs;

    private Header[] footers;

    /**
     * @since 4.4
     */
    public ChunkDecoder(
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final MessageConstraints constraints,
            final HttpTransportMetricsImpl metrics) {
        super(channel, buffer, metrics);
        this.state = READ_CONTENT;
        this.chunkSize = -1;
        this.pos = 0;
        this.endOfChunk = false;
        this.endOfStream = false;
        this.constraints = constraints != null ? constraints : MessageConstraints.DEFAULT;
        this.trailerBufs = new ArrayList<CharArrayBuffer>();
    }

    public ChunkDecoder(
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
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
        final int maxLineLen = this.constraints.getMaxLineLength();
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
            try {
                final String s = this.lineBuf.substringTrimmed(0, separator);
                this.chunkSize = Integer.parseInt(s, 16);
            } catch (final NumberFormatException e) {
                throw new MalformedChunkCodingException("Bad chunk header");
            }
            this.pos = 0;
        } else if (this.endOfStream) {
            throw new ConnectionClosedException("Premature end of chunk coded message body: " +
                    "closing chunk expected");
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
            final int maxLineLen = this.constraints.getMaxLineLength();
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
            this.footers = new Header[this.trailerBufs.size()];
            for (int i = 0; i < this.trailerBufs.size(); i++) {
                try {
                    this.footers[i] = new BufferedHeader(this.trailerBufs.get(i));
                } catch (final ParseException ex) {
                    throw new IOException(ex.getMessage());
                }
            }
        }
        this.trailerBufs.clear();
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        Args.notNull(dst, "Byte buffer");
        if (this.state == COMPLETED) {
            return -1;
        }

        int totalRead = 0;
        while (this.state != COMPLETED) {

            if (!this.buffer.hasData() || this.chunkSize == -1) {
                final int bytesRead = fillBufferFromChannel();
                if (bytesRead == -1) {
                    this.endOfStream = true;
                }
            }

            switch (this.state) {
            case READ_CONTENT:

                if (this.chunkSize == -1) {
                    readChunkHead();
                    if (this.chunkSize == -1) {
                        // Unable to read a chunk head
                        return totalRead;
                    }
                    if (this.chunkSize == 0) {
                        // Last chunk. Read footers
                        this.chunkSize = -1;
                        this.state = READ_FOOTERS;
                        break;
                    }
                }
                final int maxLen = this.chunkSize - this.pos;
                final int len = this.buffer.read(dst, maxLen);
                if (len > 0) {
                    this.pos += len;
                    totalRead += len;
                } else {
                    if (!this.buffer.hasData() && this.endOfStream) {
                        this.state = COMPLETED;
                        this.completed = true;
                        throw new TruncatedChunkException("Truncated chunk "
                                + "( expected size: " + this.chunkSize
                                + "; actual size: " + this.pos + ")");
                    }
                }

                if (this.pos == this.chunkSize) {
                    // At the end of the chunk
                    this.chunkSize = -1;
                    this.pos = 0;
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
                        this.state = COMPLETED;
                        this.completed = true;
                    }
                    return totalRead;
                }
                if (this.lineBuf.length() > 0) {
                    final int maxHeaderCount = this.constraints.getMaxHeaderCount();
                    if (maxHeaderCount > 0 && trailerBufs.size() >= maxHeaderCount) {
                        throw new MessageConstraintException("Maximum header count exceeded");
                    }
                    parseHeader();
                } else {
                    this.state = COMPLETED;
                    this.completed = true;
                    processFooters();
                }
                break;
            }

        }
        return totalRead;
    }

    public Header[] getFooters() {
        if (this.footers != null) {
            return this.footers.clone();
        } else {
            return new Header[] {};
        }
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
