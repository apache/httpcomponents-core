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

import org.apache.http.Header;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.TruncatedChunkException;
import org.apache.http.message.BufferedHeader;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.ParseException;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

/**
 * Implements chunked transfer coding. The content is received in small chunks.
 * Entities transferred using this encoder can be of unlimited length.
 *
 * @since 4.0
 */
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

    private final List<CharArrayBuffer> trailerBufs;

    private Header[] footers;

    public ChunkDecoder(
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        super(channel, buffer, metrics);
        this.state = READ_CONTENT;
        this.chunkSize = -1;
        this.pos = 0;
        this.endOfChunk = false;
        this.endOfStream = false;
        this.trailerBufs = new ArrayList<CharArrayBuffer>();
    }

    private void readChunkHead() throws IOException {
        if (this.endOfChunk) {
            if (this.buffer.length() < 2) {
                return;
            }
            int cr = this.buffer.read();
            int lf = this.buffer.read();
            if (cr != HTTP.CR || lf != HTTP.LF) {
                throw new MalformedChunkCodingException("CRLF expected at end of chunk");
            }
            this.endOfChunk = false;
        }
        if (this.lineBuf == null) {
            this.lineBuf = new CharArrayBuffer(32);
        } else {
            this.lineBuf.clear();
        }
        if (this.buffer.readLine(this.lineBuf, this.endOfStream)) {
            int separator = this.lineBuf.indexOf(';');
            if (separator < 0) {
                separator = this.lineBuf.length();
            }
            try {
                String s = this.lineBuf.substringTrimmed(0, separator);
                this.chunkSize = Integer.parseInt(s, 16);
            } catch (NumberFormatException e) {
                throw new MalformedChunkCodingException("Bad chunk header");
            }
            this.pos = 0;
        }
    }

    private void parseHeader() {
        CharArrayBuffer current = this.lineBuf;
        int count = this.trailerBufs.size();
        if ((this.lineBuf.charAt(0) == ' ' || this.lineBuf.charAt(0) == '\t') && count > 0) {
            // Handle folded header line
            CharArrayBuffer previous = this.trailerBufs.get(count - 1);
            int i = 0;
            while (i < current.length()) {
                char ch = current.charAt(i);
                if (ch != ' ' && ch != '\t') {
                    break;
                }
                i++;
            }
            previous.append(' ');
            previous.append(current, i, current.length() - i);
        } else {
            this.trailerBufs.add(current);
            this.lineBuf = null;
        }
    }

    private void processFooters() throws IOException {
        int count = this.trailerBufs.size();
        if (count > 0) {
            this.footers = new Header[this.trailerBufs.size()];
            for (int i = 0; i < this.trailerBufs.size(); i++) {
                CharArrayBuffer buffer = this.trailerBufs.get(i);
                try {
                    this.footers[i] = new BufferedHeader(buffer);
                } catch (ParseException ex) {
                    throw new IOException(ex.getMessage());
                }
            }
        }
        this.trailerBufs.clear();
    }

    public int read(final ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new IllegalArgumentException("Byte buffer may not be null");
        }
        if (this.state == COMPLETED) {
            return -1;
        }

        int totalRead = 0;
        while (this.state != COMPLETED) {

            if (!this.buffer.hasData() || this.chunkSize == -1) {
                int bytesRead = this.buffer.fill(this.channel);
                if (bytesRead > 0) {
                    this.metrics.incrementBytesTransferred(bytesRead);
                }
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
                        if (this.endOfStream) {
                            this.state = COMPLETED;
                            this.completed = true;
                        }
                        return totalRead;
                    }
                    if (this.chunkSize == 0) {
                        // Last chunk. Read footers
                        this.chunkSize = -1;
                        this.state = READ_FOOTERS;
                        break;
                    }
                }
                int maxLen = this.chunkSize - this.pos;
                int len = this.buffer.read(dst, maxLen);
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
        StringBuffer buffer = new StringBuffer();
        buffer.append("[chunk-coded; completed: ");
        buffer.append(this.completed);
        buffer.append("]");
        return buffer.toString();
    }

}
