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

package org.apache.hc.core5.http2.hpack;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * HPACK decoder.
 *
 * @since 5.0
 */
@Internal
public final class HPackDecoder {

    private static final String UNEXPECTED_EOS = "Unexpected end of HPACK data";
    private static final String MAX_LIMIT_EXCEEDED = "Max integer exceeded";

    private final InboundDynamicTable dynamicTable;
    private final ByteArrayBuffer contentBuf;
    private final CharsetDecoder charsetDecoder;
    private CharBuffer tmpBuf;
    private int maxTableSize;
    private int maxListSize;

    HPackDecoder(final InboundDynamicTable dynamicTable, final CharsetDecoder charsetDecoder) {
        this.dynamicTable = dynamicTable != null ? dynamicTable : new InboundDynamicTable();
        this.contentBuf = new ByteArrayBuffer(256);
        this.charsetDecoder = charsetDecoder;
        this.maxTableSize = dynamicTable != null ? dynamicTable.getMaxSize() : Integer.MAX_VALUE;
        this.maxListSize = Integer.MAX_VALUE;
    }

    HPackDecoder(final InboundDynamicTable dynamicTable, final Charset charset) {
        this(dynamicTable, charset != null && !StandardCharsets.US_ASCII.equals(charset) ? charset.newDecoder() : null);
    }

    public HPackDecoder(final Charset charset) {
        this(new InboundDynamicTable(), charset);
    }

    public HPackDecoder(final CharsetDecoder charsetDecoder) {
        this(new InboundDynamicTable(), charsetDecoder);
    }

    static int readByte(final ByteBuffer src) throws HPackException {

        if (!src.hasRemaining()) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        return src.get() & 0xff;
    }

    static int peekByte(final ByteBuffer src) throws HPackException {

        if (!src.hasRemaining()) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        final int pos = src.position();
        final int b = src.get() & 0xff;
        src.position(pos);
        return b;
    }

    static int decodeInt(final ByteBuffer src, final int n) throws HPackException {

        final int nbits = 0xff >>> (8 - n);
        int value = readByte(src) & nbits;
        if (value < nbits) {
            return value;
        }
        int m = 0;
        while (m < 32) {
            final int b = readByte(src);
            if ((b & 0x80) != 0) {
                value += (b & 0x7f) << m;
                m += 7;
            } else {
                if (m == 28 && (b & 0xf8) != 0) {
                    break;
                }
                value += b << m;
                return value;
            }
        }
        throw new HPackException(MAX_LIMIT_EXCEEDED);
    }

    static void decodePlainString(final ByteArrayBuffer buffer, final ByteBuffer src) throws HPackException {
        final int strLen = decodeInt(src, 7);
        final int remaining = src.remaining();
        if (strLen > remaining) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        final int originalLimit = src.limit();
        src.limit(originalLimit - (remaining - strLen));
        buffer.append(src);
        src.limit(originalLimit);
    }

    static void decodeHuffman(final ByteArrayBuffer buffer, final ByteBuffer src) throws HPackException {

        final int strLen = decodeInt(src, 7);
        if (strLen > src.remaining()) {
            throw new HPackException(UNEXPECTED_EOS);
        }
        final int limit = src.limit();
        src.limit(src.position() + strLen);
        Huffman.DECODER.decode(buffer, src);
        src.limit(limit);
    }

    void decodeString(final ByteArrayBuffer buffer, final ByteBuffer src) throws HPackException {

        final int firstByte = peekByte(src);
        if ((firstByte & 0x80) == 0x80) {
            decodeHuffman(buffer, src);
        } else {
            decodePlainString(buffer, src);
        }
    }

    private void clearState() {

        if (this.tmpBuf != null) {
            this.tmpBuf.clear();
        }
        if (this.charsetDecoder != null) {
            this.charsetDecoder.reset();
        }
        this.contentBuf.clear();
    }

    private void expandCapacity(final int capacity) {

        final CharBuffer previous = this.tmpBuf;
        this.tmpBuf = CharBuffer.allocate(capacity);
        previous.flip();
        this.tmpBuf.put(previous);
    }

    private void ensureCapacity(final int extra) {

        if (this.tmpBuf == null) {
            this.tmpBuf = CharBuffer.allocate(Math.max(256, extra));
        }
        final int requiredCapacity = this.tmpBuf.remaining() + extra;
        if (requiredCapacity > this.tmpBuf.capacity()) {
            expandCapacity(requiredCapacity);
        }
    }

    int decodeString(final ByteBuffer src, final StringBuilder buf) throws HPackException, CharacterCodingException {

        clearState();
        decodeString(this.contentBuf, src);
        final int binaryLen = this.contentBuf.length();
        if (this.charsetDecoder == null) {
            buf.ensureCapacity(binaryLen);
            for (int i = 0; i < binaryLen; i++) {
                buf.append((char) (this.contentBuf.byteAt(i) & 0xff));
            }
        } else {
            final ByteBuffer in = ByteBuffer.wrap(this.contentBuf.array(), 0, binaryLen);
            while (in.hasRemaining()) {
                ensureCapacity(in.remaining());
                final CoderResult result = this.charsetDecoder.decode(in, this.tmpBuf, true);
                if (result.isError()) {
                    result.throwException();
                }
            }
            ensureCapacity(8);
            final CoderResult result = this.charsetDecoder.flush(this.tmpBuf);
            if (result.isError()) {
                result.throwException();
            }
            this.tmpBuf.flip();
            buf.append(this.tmpBuf);
        }
        return binaryLen;
    }

    HPackHeader decodeLiteralHeader(
            final ByteBuffer src,
            final HPackRepresentation representation) throws HPackException, CharacterCodingException {

        final int n = representation == HPackRepresentation.WITH_INDEXING ? 6 : 4;
        final int index = decodeInt(src, n);
        final String name;
        final int nameLen;
        if (index == 0) {
            final StringBuilder buf = new StringBuilder();
            nameLen = decodeString(src, buf);
            name = buf.toString();
        } else {
            final HPackHeader existing =  this.dynamicTable.getHeader(index);
            if (existing == null) {
                throw new HPackException("Invalid header index");
            }
            name = existing.getName();
            nameLen = existing.getNameLen();
        }
        final StringBuilder buf = new StringBuilder();
        final int valueLen = decodeString(src, buf);
        final String value = buf.toString();
        final HPackHeader header = new HPackHeader(name, nameLen, value, valueLen, representation == HPackRepresentation.NEVER_INDEXED);
        if (representation == HPackRepresentation.WITH_INDEXING) {
            this.dynamicTable.add(header);
        }
        return header;
    }

    HPackHeader decodeIndexedHeader(final ByteBuffer src) throws HPackException {

        final int index = decodeInt(src, 7);
        final HPackHeader existing =  this.dynamicTable.getHeader(index);
        if (existing == null) {
            throw new HPackException("Invalid header index");
        }
        return existing;
    }

    public Header decodeHeader(final ByteBuffer src) throws HPackException {
        final HPackHeader header = decodeHPackHeader(src);
        return header != null ? new BasicHeader(header.getName(), header.getValue(), header.isSensitive()) : null;
    }

    HPackHeader decodeHPackHeader(final ByteBuffer src) throws HPackException {
        try {
            while (src.hasRemaining()) {
                final int b = peekByte(src);
                if ((b & 0x80) == 0x80) {
                    return decodeIndexedHeader(src);
                } else if ((b & 0xc0) == 0x40) {
                    return decodeLiteralHeader(src, HPackRepresentation.WITH_INDEXING);
                } else if ((b & 0xf0) == 0x00) {
                    return decodeLiteralHeader(src, HPackRepresentation.WITHOUT_INDEXING);
                } else if ((b & 0xf0) == 0x10) {
                    return decodeLiteralHeader(src, HPackRepresentation.NEVER_INDEXED);
                } else if ((b & 0xe0) == 0x20) {
                    final int maxSize = decodeInt(src, 5);
                    this.dynamicTable.setMaxSize(Math.min(this.maxTableSize, maxSize));
                } else {
                    throw new HPackException("Unexpected header first byte: 0x" + Integer.toHexString(b));
                }
            }
            return null;
        } catch (final CharacterCodingException ex) {
            throw new HPackException(ex.getMessage(), ex);
        }
    }

    public List<Header> decodeHeaders(final ByteBuffer src) throws HPackException {
        final boolean enforceSizeLimit = maxListSize < Integer.MAX_VALUE;
        int listSize = 0;

        final List<Header> list = new ArrayList<>();
        while (src.hasRemaining()) {
            final HPackHeader header = decodeHPackHeader(src);
            if (header == null) {
                break;
            }
            if (enforceSizeLimit) {
                listSize += header.getTotalSize();
                if (listSize >= maxListSize) {
                    throw new HeaderListConstraintException("Maximum header list size exceeded");
                }
            }
            list.add(new BasicHeader(header.getName(), header.getValue(), header.isSensitive()));
        }
        return list;
    }

    public int getMaxTableSize() {
        return this.maxTableSize;
    }

    public void setMaxTableSize(final int maxTableSize) {
        Args.notNegative(maxTableSize, "Max table size");
        this.maxTableSize = maxTableSize;
        this.dynamicTable.setMaxSize(maxTableSize);
    }

    public int getMaxListSize() {
        return maxListSize;
    }

    public void setMaxListSize(final int maxListSize) {
        Args.notNegative(maxListSize, "Max list size");
        this.maxListSize = maxListSize;
    }

}
