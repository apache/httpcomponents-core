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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * A self contained, repeatable entity that obtains its content from a {@link String}.
 * <p>
 * This class contains {@link ThreadingBehavior#IMMUTABLE immutable attributes} but subclasses may contain
 * additional immutable or mutable attributes.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class StringEntity extends AbstractHttpEntity {

    private final byte[] content;

    /**
     * Constructs a StringEntity with the specified content and content type.
     *
     * @param string          The content to be used. Not {@code null}.
     * @param contentType     The content type to be used. May be {@code null}, in which case the default MIME type {@link ContentType#TEXT_PLAIN} is assumed.
     * @param contentEncoding The content encoding string, may be null.
     * @param chunked         Whether this entity should be chunked.
     * @throws NullPointerException Thrown if string is null.
     * @since 5.0
     */
    public StringEntity(
            final String string, final ContentType contentType, final String contentEncoding, final boolean chunked) {
        super(contentType, contentEncoding, chunked);
        Args.notNull(string, "Source string");
        final Charset charset = ContentType.getCharset(contentType, StandardCharsets.UTF_8);
        this.content = string.getBytes(charset);
    }

    /**
     * Constructs a StringEntity with the specified content and content type.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param string          The content to be used. Not {@code null}.
     * @param contentType     The content type to be used. May be {@code null}, in which case the default MIME type {@link ContentType#TEXT_PLAIN} is assumed.
     * @param chunked         Whether this entity should be chunked.
     * @throws NullPointerException Thrown if string is null.
     */
    public StringEntity(final String string, final ContentType contentType, final boolean chunked) {
        this(string, contentType, null, chunked);
    }

    /**
     * Constructs a StringEntity with the specified content and content type.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param string          The content to be used. Not {@code null}.
     * @param contentType     The content type to be used. May be {@code null}, in which case the default MIME type {@link ContentType#TEXT_PLAIN} is assumed.
     * @throws NullPointerException Thrown if string is null.
     */
    public StringEntity(final String string, final ContentType contentType) {
        this(string, contentType, null, false);
    }

    /**
     * Constructs a StringEntity with the specified content and charset. The MIME type defaults to "text/plain".
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>sets the content type to {@code "text/plain"} and the given Charset.</li>
     * </ul>
     *
     * @param string  The content to be used. Not {@code null}.
     * @param charset The character set to be used. May be {@code null}, in which case the default is {@link StandardCharsets#UTF_8} is assumed.
     * @throws NullPointerException Thrown if string is null.
     * @since 4.2
     */
    public StringEntity(final String string, final Charset charset) {
        this(string, ContentType.TEXT_PLAIN.withCharset(charset));
    }

    /**
     * Constructs a StringEntity with the specified content and content type.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>sets the content type to {@code "text/plain"} and the given Charset.</li>
     * </ul>
     *
     * @param string          The content to be used. Not {@code null}.
     * @param charset The character set to be used. May be {@code null}, in which case the default is {@link StandardCharsets#UTF_8} is assumed.
     * @param chunked         Whether this entity should be chunked.
     * @throws NullPointerException Thrown if string is null.
     */
    public StringEntity(final String string, final Charset charset, final boolean chunked) {
        this(string, ContentType.TEXT_PLAIN.withCharset(charset), chunked);
    }

    /**
     * Constructs a StringEntity with the specified content and content type.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>sets the content type to {@code "text/plain"} and the given Charset.</li>
     * </ul>
     *
     * @param string          The content to be used. Not {@code null}.
     * @throws NullPointerException Thrown if string is null.
     */
    public StringEntity(final String string) {
        this(string, ContentType.DEFAULT_TEXT);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     */
    @Override
    public final boolean isRepeatable() {
        return true;
    }

    @Override
    public final long getContentLength() {
        return this.content.length;
    }

    @Override
    public final InputStream getContent() throws IOException {
        return new ByteArrayInputStream(this.content);
    }

    @Override
    public final void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        outStream.write(this.content);
        outStream.flush();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}.
     * </p>
     */
    @Override
    public final boolean isStreaming() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is a no-op.
     * </p>
     */
    @Override
    public final void close() throws IOException {
        // nothing to do
    }

}
