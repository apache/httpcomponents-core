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

package org.apache.http.nio.entity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;

/**
 * A simple, self contained, repeatable non-blocking entity that retrieves
 * its content from a {@link String} object.
 *
 * @since 4.0
 *
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public class NStringEntity extends AbstractHttpEntity
                           implements HttpAsyncContentProducer, ProducingNHttpEntity {

    private final byte[] b;
    private final ByteBuffer buf;
    /**
     * @deprecated (4.2)
     */
    @Deprecated
    protected final byte[] content;
    /**
     * @deprecated (4.2)
     */
    @Deprecated
    protected final ByteBuffer buffer;

    /**
     * Creates a NStringEntity with the specified content and content type.
     *
     * @param s content to be used. Not {@code null}.
     * @param contentType content type to be used. May be {@code null}, in which case
     * {@link ContentType#TEXT_PLAIN} is assumed.
     *
     * @throws IllegalArgumentException if the string parameter is null
     *
     * @since 4.2
     */
    public NStringEntity(final String s, final ContentType contentType) {
        Args.notNull(s, "Source string");
        Charset charset = contentType != null ? contentType.getCharset() : null;
        if (charset == null) {
            charset = HTTP.DEF_CONTENT_CHARSET;
        }
        this.b = s.getBytes(charset);
        this.buf = ByteBuffer.wrap(this.b);
        this.content = b;
        this.buffer = this.buf;
        if (contentType != null) {
            setContentType(contentType.toString());
        }
    }

    /**
     * Creates a NStringEntity with the specified content and charset. The MIME type defaults
     * to "text/plain".
     *
     * @param s content to be used. Not {@code null}.
     * @param charset character set to be used. May be {@code null}, in which case the default
     *   is {@link HTTP#DEF_CONTENT_CHARSET} is assumed
     *
     * @throws IllegalArgumentException if the string parameter is null
     * @throws UnsupportedEncodingException Thrown when the named charset is not available in
     * this instance of the Java virtual machine
     */
    public NStringEntity(final String s, final String charset)
            throws UnsupportedEncodingException {
        this(s, ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), charset));
    }

    /**
     * Creates a NStringEntity with the specified content and charset. The MIME type defaults
     * to "text/plain".
     *
     * @param s content to be used. Not {@code null}.
     * @param charset character set to be used. May be {@code null}, in which case the default
     *   is {@link HTTP#DEF_CONTENT_CHARSET} is assumed
     *
     * @throws IllegalArgumentException if the string parameter is null
     *
     * @since 4.2
     */
    public NStringEntity(final String s, final Charset charset) {
        this(s, ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), charset));
    }

    /**
     * Creates a NStringEntity with the specified content. The content type defaults to
     * {@link ContentType#TEXT_PLAIN}.
     *
     * @param s content to be used. Not {@code null}.
     *
     * @throws IllegalArgumentException if the string parameter is null
     * @throws UnsupportedEncodingException if the default HTTP charset is not supported.
     */
    public NStringEntity(final String s) throws UnsupportedEncodingException {
        this(s, ContentType.DEFAULT_TEXT);
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long getContentLength() {
        return this.b.length;
    }

    /**
     * {@inheritDoc}
     *
     * @since 4.2
     */
    @Override
    public void close() {
        this.buf.rewind();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated (4.2) use {@link #close()}
     */
    @Deprecated
    public void finish() {
        close();
    }

    @Override
    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        encoder.write(this.buf);
        if (!this.buf.hasRemaining()) {
            encoder.complete();
        }
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public InputStream getContent() {
        return new ByteArrayInputStream(this.b);
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        Args.notNull(outstream, "Output stream");
        outstream.write(this.b);
        outstream.flush();
    }

}
