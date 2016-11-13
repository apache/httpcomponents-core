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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public abstract class AbstractCharAsyncEntityConsumer<T> implements AsyncEntityConsumer<T> {

    private static final ByteBuffer EMPTY = ByteBuffer.wrap(new byte[0]);

    private volatile ContentType contentType;
    private volatile CharsetDecoder charsetDecoder;
    private volatile CharBuffer charbuf;

    protected abstract void dataStart(ContentType contentType, FutureCallback<T> resultCallback) throws HttpException, IOException;

    protected abstract void consumeData(CharBuffer src) throws IOException;

    protected abstract void dataEnd() throws IOException;

    @Override
    public final void streamStart(
            final EntityDetails entityDetails,
            final FutureCallback<T> resultCallback) throws IOException, HttpException {
        Args.notNull(resultCallback, "Result callback");
        try {
            this.contentType = entityDetails != null ? ContentType.parse(entityDetails.getContentType()) : null;
            dataStart(this.contentType, resultCallback);
        } catch (UnsupportedCharsetException ex) {
            throw new UnsupportedEncodingException(ex.getMessage());
        }
    }

    private void checkResult(final CoderResult result) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
    }

    private void doDecode() throws IOException {
        charbuf.flip();
        final int chunk = charbuf.remaining();
        if (chunk > 0) {
            consumeData(charbuf);
        }
        charbuf.clear();
    }

    @Override
    public final int consume(final ByteBuffer src) throws IOException {
        Args.notNull(src, "ByteBuffer");
        if (charsetDecoder == null) {
            Charset charset = contentType != null ? contentType.getCharset() : null;
            if (charset == null) {
                charset = StandardCharsets.US_ASCII;
            }
            charsetDecoder = charset.newDecoder();
        }
        if (charbuf == null) {
            charbuf = CharBuffer.allocate(2048);
        }
        while (src.hasRemaining()) {
            checkResult(charsetDecoder.decode(src, charbuf, false));
            doDecode();
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws IOException {
        if (charsetDecoder != null) {
            if (charbuf == null) {
                charbuf = CharBuffer.allocate(512);
            }
            checkResult(charsetDecoder.decode(EMPTY, charbuf, true));
            doDecode();
            checkResult(charsetDecoder.flush(charbuf));
            doDecode();
        }
        dataEnd();
    }

}
