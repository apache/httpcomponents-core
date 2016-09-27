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
package org.apache.hc.core5.http2.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http2.nio.AsyncEntityProducer;
import org.apache.hc.core5.http2.nio.DataStreamChannel;
import org.apache.hc.core5.http2.nio.StreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public abstract class AbstractCharAsyncEntityProducer implements AsyncEntityProducer {

    private static final CharBuffer EMPTY = CharBuffer.wrap(new char[0]);

    private final ContentType contentType;
    private final CharsetEncoder charsetEncoder;
    private final ByteBuffer bytebuf;
    private final StreamChannel<CharBuffer> charDataStream;

    private volatile boolean endStream;

    public AbstractCharAsyncEntityProducer(final int bufferSize, final ContentType contentType) {
        Args.positive(bufferSize, "Buffer size");
        this.contentType = contentType;
        Charset charset = contentType != null ? contentType.getCharset() : null;
        if (charset == null) {
            charset = StandardCharsets.US_ASCII;
        }
        this.charsetEncoder = charset.newEncoder();
        this.bytebuf = ByteBuffer.allocate(bufferSize);
        this.charDataStream = new StreamChannel<CharBuffer>() {

            @Override
            public int write(final CharBuffer src) throws IOException {
                Args.notNull(src, "CharBuffer");
                final int p = src.position();
                checkResult(charsetEncoder.encode(src, bytebuf, false));
                return src.position() - p;
            }

            @Override
            public void endStream() throws IOException {
                endStream = true;
                checkResult(charsetEncoder.encode(EMPTY, bytebuf, true));
                checkResult(charsetEncoder.flush(bytebuf));
            }

        };
    }

    protected abstract void dataStart(StreamChannel<CharBuffer> channel) throws IOException;

    protected abstract void produceData(StreamChannel<CharBuffer> channel) throws IOException;

    @Override
    public final String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    private void checkResult(final CoderResult result) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
    }

    private void flushData(final DataStreamChannel channel) throws IOException {
        if (!endStream) {
            produceData(charDataStream);
        }
        if (bytebuf.position() > 0) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
        if (bytebuf.position() == 0 && endStream) {
            channel.endStream();
        }
    }

    @Override
    public void streamStart(final DataStreamChannel channel) throws IOException {
        dataStart(charDataStream);
        flushData(channel);
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        if (!endStream) {
            produceData(charDataStream);
        }
        flushData(channel);
    }

}
