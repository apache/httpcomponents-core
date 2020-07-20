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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.net.WWWFormCodec;

/**
 * {AsyncEntityProducer} factory methods.
 *
 * @since 5.0
 */
public final class AsyncEntityProducers {

    private AsyncEntityProducers() {
    }

    public static AsyncEntityProducer create(final String content, final ContentType contentType) {
        return new BasicAsyncEntityProducer(content, contentType);
    }

    public static AsyncEntityProducer create(final String content, final Charset charset) {
        return new BasicAsyncEntityProducer(content, ContentType.TEXT_PLAIN.withCharset(charset));
    }

    public static AsyncEntityProducer create(final String content) {
        return new BasicAsyncEntityProducer(content, ContentType.TEXT_PLAIN);
    }

    public static AsyncEntityProducer create(final byte[] content, final ContentType contentType) {
        return new BasicAsyncEntityProducer(content, contentType);
    }

    public static AsyncEntityProducer create(final File content, final ContentType contentType) {
        return new FileEntityProducer(content, contentType);
    }

    public static AsyncEntityProducer createUrlEncoded(
            final Iterable <? extends NameValuePair> parameters, final Charset charset) {
        final ContentType contentType = charset != null ?
                ContentType.APPLICATION_FORM_URLENCODED.withCharset(charset) :
                ContentType.APPLICATION_FORM_URLENCODED;
        return create(WWWFormCodec.format(parameters, contentType.getCharset()), contentType);
    }

    public static AsyncEntityProducer createBinary(
            final Callback<StreamChannel<ByteBuffer>> callback,
            final ContentType contentType) {
        return new AbstractBinAsyncEntityProducer(0, contentType) {

            @Override
            protected int availableData() {
                return Integer.MAX_VALUE;
            }

            @Override
            protected void produceData(final StreamChannel<ByteBuffer> channel) throws IOException {
                callback.execute(channel);
            }

            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public void failed(final Exception cause) {
            }

        };
    }

    public static AsyncEntityProducer createText(
            final Callback<StreamChannel<CharBuffer>> callback,
            final ContentType contentType) {
        return new AbstractCharAsyncEntityProducer(4096, 2048, contentType) {

            @Override
            protected int availableData() {
                return Integer.MAX_VALUE;
            }

            @Override
            protected void produceData(final StreamChannel<CharBuffer> channel) throws IOException {
                callback.execute(channel);
            }

            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public void failed(final Exception cause) {
            }

        };
    }

    public static AsyncEntityProducer withTrailers(final AsyncEntityProducer entity, final Header... trailers) {
        return new AsyncEntityProducerWrapper(entity) {

            @Override
            public boolean isChunked() {
                // Must be chunk coded
                return true;
            }

            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public Set<String> getTrailerNames() {
                final Set<String> names = new LinkedHashSet<>();
                for (final Header trailer: trailers) {
                    names.add(trailer.getName());
                }
                return names;
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                super.produce(new DataStreamChannel() {

                    @Override
                    public void requestOutput() {
                        channel.requestOutput();
                    }

                    @Override
                    public int write(final ByteBuffer src) throws IOException {
                        return channel.write(src);
                    }

                    @Override
                    public void endStream(final List<? extends Header> p) throws IOException {
                        final List<Header> allTrailers;
                        if (p != null && !p.isEmpty()) {
                            allTrailers = new ArrayList<>(p);
                            allTrailers.addAll(Arrays.asList(trailers));
                        } else {
                            allTrailers = Arrays.asList(trailers);
                        }
                        channel.endStream(allTrailers);
                    }

                    @Override
                    public void endStream() throws IOException {
                        channel.endStream();
                    }

                });
            }
        };
    }

    public static AsyncEntityProducer create(final String content, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(content, contentType), trailers);
    }

    public static AsyncEntityProducer create(final String content, final Charset charset, final Header... trailers) {
        return withTrailers(create(content, charset), trailers);
    }

    public static AsyncEntityProducer create(final String content, final Header... trailers) {
        return withTrailers(create(content), trailers);
    }

    public static AsyncEntityProducer create(final byte[] content, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(content, contentType), trailers);
    }

    public static AsyncEntityProducer create(final File content, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(content, contentType), trailers);
    }

    public static AsyncEntityProducer createBinary(
            final Callback<StreamChannel<ByteBuffer>> callback,
            final ContentType contentType,
            final Header... trailers) {
        return withTrailers(createBinary(callback, contentType), trailers);
    }

    public static AsyncEntityProducer createText(
            final Callback<StreamChannel<CharBuffer>> callback,
            final ContentType contentType,
            final Header... trailers) {
        return withTrailers(createText(callback, contentType), trailers);
    }

}
