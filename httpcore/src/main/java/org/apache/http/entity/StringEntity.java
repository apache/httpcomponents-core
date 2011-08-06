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

package org.apache.http.entity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.protocol.HTTP;

/**
 * A self contained, repeatable entity that obtains its content from
 * a {@link String}.
 *
 * @since 4.0
 */
@NotThreadSafe
public class StringEntity extends AbstractHttpEntity implements Cloneable {

    protected final byte[] content;

    /**
     * Creates a StringEntity with the specified content and content type.
     *
     * @param string content to be used. Not {@code null}.
     * @param contentType content type to be used. May be {@code null}, in which case the default
     *   MIME type {@link HTTP#PLAIN_TEXT_TYPE} i.e. "text/plain" and the default charset
     *   {@link HTTP#DEFAULT_CONTENT_CHARSET} i.e. "ISO-8859-1" are assumed.
     *
     * @throws IllegalArgumentException if the string parameter is null or if the charset
     *   specified in the CotnentType is not supported.
     *
     * @since 4.2
     */
    public static StringEntity create(final String string, final ContentType contentType) {
        try {
            return new StringEntity(string, contentType);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    /**
     * Creates a StringEntity with the specified content and charset. The mime type defaults
     * to {@link HTTP#PLAIN_TEXT_TYPE} i.e. "text/plain".
     *
     * @param string content to be used. Not {@code null}.
     * @param charset character set to be used. May be {@code null}, in which case the default
     *   is {@link HTTP#DEFAULT_CONTENT_CHARSET} i.e. "ISO-8859-1"
     *
     * @throws IllegalArgumentException if the string parameter is null or if the charset
     *   specified in the CotnentType is not supported.
     *
     * @since 4.2
     */
    public static StringEntity create(
            final String string, final String charset) {
        return create(string, ContentType.create(HTTP.PLAIN_TEXT_TYPE, charset));
    }

    /**
     * Creates a StringEntity with the specified content and charset. The charset defaults to
     * {@link HTTP#DEFAULT_CONTENT_CHARSET} i.e. "ISO-8859-1". The mime type defaults to
     * {@link HTTP#PLAIN_TEXT_TYPE} i.e. "text/plain".
     *
     * @param string content to be used. Not {@code null}.
     *
     * @throws IllegalArgumentException if the string parameter is null
     *
     * @since 4.2
     */
    public static StringEntity create(final String string) {
        return create(string, ContentType.DEFAULT_TEXT);
    }

    /**
     * Creates a StringEntity with the specified content and content type.
     *
     * @param string content to be used. Not {@code null}.
     * @param contentType content type to be used. May be {@code null}, in which case the default
     *   MIME type {@link HTTP#PLAIN_TEXT_TYPE} i.e. "text/plain" and the default charset
     *   {@link HTTP#DEFAULT_CONTENT_CHARSET} i.e. "ISO-8859-1" are assumed.
     *
     * @throws IllegalArgumentException if the string parameter is null
     * @throws UnsupportedEncodingException if the charset specified in the CotnentType
     *   is not supported.
     *
     * @since 4.2
     */
    public StringEntity(final String string, final ContentType contentType)
            throws UnsupportedEncodingException {
        super();
        if (string == null) {
            throw new IllegalArgumentException("Source string may not be null");
        }
        String charset = contentType != null ? contentType.getCharset() : null;
        if (charset == null) {
            charset = HTTP.DEFAULT_CONTENT_CHARSET;
        }
        this.content = string.getBytes(charset);
        if (contentType != null) {
            setContentType(contentType.toString());
        }
    }

    /**
     * Creates a StringEntity with the specified content, mimetype and charset
     *
     * @param string content to be used. Not {@code null}.
     * @param mimeType mime type to be used. May be {@code null}, in which case the default
     *   is {@link HTTP#PLAIN_TEXT_TYPE} i.e. "text/plain"
     * @param charset character set to be used. May be {@code null}, in which case the default
     *   is {@link HTTP#DEFAULT_CONTENT_CHARSET} i.e. "ISO-8859-1"
     *
     * @since 4.1
     * @throws IllegalArgumentException if the string parameter is null
     *
     * @deprecated use {@link #StringEntity(String, ContentType)}
     */
    @Deprecated
    public StringEntity(final String string, String mimeType, String charset)
            throws UnsupportedEncodingException {
        super();
        if (string == null) {
            throw new IllegalArgumentException("Source string may not be null");
        }
        if (mimeType == null) {
            mimeType = HTTP.PLAIN_TEXT_TYPE;
        }
        if (charset == null) {
            charset = HTTP.DEFAULT_CONTENT_CHARSET;
        }
        this.content = string.getBytes(charset);
        setContentType(mimeType + HTTP.CHARSET_PARAM + charset);
    }

    /**
     * Creates a StringEntity with the specified content and charset. The mime type defaults
     * to {@link HTTP#PLAIN_TEXT_TYPE} i.e. "text/plain".
     *
     * @param string content to be used. Not {@code null}.
     * @param charset character set to be used. May be {@code null}, in which case the default
     *   is {@link HTTP#DEFAULT_CONTENT_CHARSET} i.e. "ISO-8859-1"
     *
     * @throws IllegalArgumentException if the string parameter is null
     * @throws UnsupportedEncodingException if the charset is not supported.
     */
    public StringEntity(final String string, final String charset)
            throws UnsupportedEncodingException {
        this(string, ContentType.create(HTTP.PLAIN_TEXT_TYPE, charset));
    }

    /**
     * Creates a StringEntity with the specified content and charset. The charset defaults to
     * {@link HTTP#DEFAULT_CONTENT_CHARSET} i.e. "ISO-8859-1". The mime type defaults to
     * {@link HTTP#PLAIN_TEXT_TYPE} i.e. "text/plain".
     *
     * @param string content to be used. Not {@code null}.
     *
     * @throws IllegalArgumentException if the string parameter is null
     * @throws UnsupportedEncodingException if the default HTTP charset is not supported.
     */
    public StringEntity(final String string)
            throws UnsupportedEncodingException {
        this(string, ContentType.DEFAULT_TEXT);
    }

    public boolean isRepeatable() {
        return true;
    }

    public long getContentLength() {
        return this.content.length;
    }

    public InputStream getContent() throws IOException {
        return new ByteArrayInputStream(this.content);
    }

    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        outstream.write(this.content);
        outstream.flush();
    }

    /**
     * Tells that this entity is not streaming.
     *
     * @return <code>false</code>
     */
    public boolean isStreaming() {
        return false;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

} // class StringEntity
