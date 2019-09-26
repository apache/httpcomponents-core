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
package org.apache.hc.core5.http.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * Abstract text data consumer.
 *
 * @since 5.0
 */
public abstract class AbstractCharDataConsumer implements AsyncDataConsumer {

    protected static final int DEF_BUF_SIZE = 8192;
    private static final ByteBuffer EMPTY_BIN = ByteBuffer.wrap(new byte[0]);

    private final CharBuffer charbuf;
    private final CharCodingConfig charCodingConfig;

    private volatile Charset charset;
    private volatile CharsetDecoder charsetDecoder;

    protected AbstractCharDataConsumer(final int bufSize, final CharCodingConfig charCodingConfig) {
        this.charbuf = CharBuffer.allocate(Args.positive(bufSize, "Buffer size"));
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
    }

    public AbstractCharDataConsumer() {
        this(DEF_BUF_SIZE, CharCodingConfig.DEFAULT);
    }
    /**
     * Triggered to obtain the capacity increment.
     *
     * @return the number of bytes this consumer is prepared to process.
     */
    protected abstract int capacityIncrement();

    /**
     * Triggered to pass incoming data packet to the data consumer.
     *
     * @param src the data packet.
     * @param endOfStream flag indicating whether this data packet is the last in the data stream.
     *
     */
    protected abstract void data(CharBuffer src, boolean endOfStream) throws IOException;

    /**
     * Triggered to signal completion of data processing.
     */
    protected abstract void completed() throws IOException;

    protected final void setCharset(final Charset charset) {
        this.charset = charset != null ? charset : charCodingConfig.getCharset();
        this.charsetDecoder = null;
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(capacityIncrement());
    }

    private void checkResult(final CoderResult result) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
    }

    private void doDecode(final boolean endOfStream) throws IOException {
        charbuf.flip();
        data(charbuf, endOfStream);
        charbuf.clear();
    }

    private CharsetDecoder getCharsetDecoder() {
        if (charsetDecoder == null) {
            Charset charset = this.charset;
            if (charset == null) {
                charset = charCodingConfig.getCharset();
            }
            if (charset == null) {
                charset = StandardCharsets.US_ASCII;
            }
            charsetDecoder = charset.newDecoder();
            if (charCodingConfig.getMalformedInputAction() != null) {
                charsetDecoder.onMalformedInput(charCodingConfig.getMalformedInputAction());
            }
            if (charCodingConfig.getUnmappableInputAction() != null) {
                charsetDecoder.onUnmappableCharacter(charCodingConfig.getUnmappableInputAction());
            }
        }
        return charsetDecoder;
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final CharsetDecoder charsetDecoder = getCharsetDecoder();
        while (src.hasRemaining()) {
            checkResult(charsetDecoder.decode(src, charbuf, false));
            doDecode(false);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final CharsetDecoder charsetDecoder = getCharsetDecoder();
        checkResult(charsetDecoder.decode(EMPTY_BIN, charbuf, true));
        doDecode(false);
        checkResult(charsetDecoder.flush(charbuf));
        doDecode(true);
        completed();
    }

}