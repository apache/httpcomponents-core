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

package org.apache.hc.core5.http.io.entity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.io.IOCallback;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Args;

/**
 * {HttpEntity} factory methods.
 *
 * @since 5.0
 */
public final class HttpEntities {

    private HttpEntities() {
    }

    public static HttpEntity create(final String content, final ContentType contentType) {
        return new StringEntity(content, contentType);
    }

    public static HttpEntity create(final String content, final Charset charset) {
        return new StringEntity(content, ContentType.TEXT_PLAIN.withCharset(charset));
    }

    public static HttpEntity create(final String content) {
        return new StringEntity(content, ContentType.TEXT_PLAIN);
    }

    public static HttpEntity create(final byte[] content, final ContentType contentType) {
        return new ByteArrayEntity(content, contentType);
    }

    public static HttpEntity create(final File content, final ContentType contentType) {
        return new FileEntity(content, contentType);
    }

    public static HttpEntity create(final Serializable serializable, final ContentType contentType) {
        return new SerializableEntity(serializable, contentType);
    }

    public static HttpEntity createUrlEncoded(
            final Iterable <? extends NameValuePair> parameters, final Charset charset) {
        final ContentType contentType = charset != null ?
                ContentType.APPLICATION_FORM_URLENCODED.withCharset(charset) :
                ContentType.APPLICATION_FORM_URLENCODED;
        return create(WWWFormCodec.format(parameters, contentType.getCharset()), contentType);
    }

    public static HttpEntity create(final IOCallback<OutputStream> callback, final ContentType contentType) {
        return new EntityTemplate(-1, contentType, null, callback);
    }

    public static HttpEntity gzip(final HttpEntity entity) {
        return new HttpEntityWrapper(entity) {

            @Override
            public String getContentEncoding() {
                return "gzip";
            }

            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public InputStream getContent() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void writeTo(final OutputStream outStream) throws IOException {
                Args.notNull(outStream, "Output stream");
                final GZIPOutputStream gzip = new GZIPOutputStream(outStream);
                super.writeTo(gzip);
                // Only close output stream if the wrapped entity has been
                // successfully written out
                gzip.close();
            }

        };
    }

    public static HttpEntity createGzipped(final String content, final ContentType contentType) {
        return gzip(create(content, contentType));
    }

    public static HttpEntity createGzipped(final String content, final Charset charset) {
        return gzip(create(content, charset));
    }

    public static HttpEntity createGzipped(final String content) {
        return gzip(create(content));
    }

    public static HttpEntity createGzipped(final byte[] content, final ContentType contentType) {
        return gzip(create(content, contentType));
    }

    public static HttpEntity createGzipped(final File content, final ContentType contentType) {
        return gzip(create(content, contentType));
    }

    public static HttpEntity createGzipped(final Serializable serializable, final ContentType contentType) {
        return gzip(create(serializable, contentType));
    }

    public static HttpEntity createGzipped(final IOCallback<OutputStream> callback, final ContentType contentType) {
        return gzip(create(callback, contentType));
    }

    public static HttpEntity createGzipped(final Path content, final ContentType contentType) {
        return gzip(create(content, contentType));
    }

    public static HttpEntity withTrailers(final HttpEntity entity, final Header... trailers) {
        return new HttpEntityWrapper(entity) {

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
            public Supplier<List<? extends Header>> getTrailers() {
                return () -> Arrays.asList(trailers);
            }

            @Override
            public Set<String> getTrailerNames() {
                final Set<String> names = new LinkedHashSet<>();
                for (final Header trailer: trailers) {
                    names.add(trailer.getName());
                }
                return names;
            }

        };
    }

    public static HttpEntity create(final String content, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(content, contentType), trailers);
    }

    public static HttpEntity create(final String content, final Charset charset, final Header... trailers) {
        return withTrailers(create(content, charset), trailers);
    }

    public static HttpEntity create(final String content, final Header... trailers) {
        return withTrailers(create(content), trailers);
    }

    public static HttpEntity create(final byte[] content, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(content, contentType), trailers);
    }

    public static HttpEntity create(final File content, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(content, contentType), trailers);
    }

    public static HttpEntity create(
            final Serializable serializable, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(serializable, contentType), trailers);
    }

    public static HttpEntity create(
            final IOCallback<OutputStream> callback, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(callback, contentType), trailers);
    }

    public static HttpEntity create(final Path content, final ContentType contentType) {
        return new PathEntity(content, contentType);
    }

    public static HttpEntity create(final Path content, final ContentType contentType, final Header... trailers) {
        return withTrailers(create(content, contentType), trailers);
    }

}
