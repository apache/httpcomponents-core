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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Support methods for {@link HttpEntity}.
 *
 * @since 4.0
 */
public final class EntityUtils {

    // TODO Consider using a sane value, but what is sane? 1 GB? 100 MB? 10 MB?
    private static final int DEFAULT_ENTITY_RETURN_MAX_LENGTH = Integer.MAX_VALUE;
    private static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;
    private static final int DEFAULT_CHAR_BUFFER_SIZE = 1024;
    private static final int DEFAULT_BYTE_BUFFER_SIZE = 4096;

    private EntityUtils() {
        // NoOp
    }

    /**
     * Ensures that the entity content is fully consumed and the content stream, if exists,
     * is closed. The process is done, <i>quietly</i> , without throwing any IOException.
     *
     * @param entity the entity to consume.
     *
     * @since 4.2
     */
    public static void consumeQuietly(final HttpEntity entity) {
        try {
          consume(entity);
        } catch (final IOException ignore) {
            // Ignore exception
        }
    }

    /**
     * Ensures that the entity content is fully consumed and the content stream, if exists,
     * is closed.
     *
     * @param entity the entity to consume.
     * @throws IOException if an error occurs reading the input stream
     *
     * @since 4.1
     */
    public static void consume(final HttpEntity entity) throws IOException {
        if (entity == null) {
            return;
        }
        if (entity.isStreaming()) {
            Closer.close(entity.getContent());
        }
    }

    /**
     * Gets a usable content length value for the given candidate.
     *
     * @param contentLength an integer.
     * @return The given content length or {@value #DEFAULT_BYTE_BUFFER_SIZE} if it is &lt 0.
     */
    private static int toContentLength(final int contentLength) {
        return contentLength < 0 ? DEFAULT_BYTE_BUFFER_SIZE : contentLength;
    }

    /**
     * Reads the contents of an entity and return it as a byte array.
     *
     * @param entity the entity to read from=
     * @return byte array containing the entity content. May be null if
     *   {@link HttpEntity#getContent()} is null.
     * @throws IOException if an error occurs reading the input stream
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     */
    public static byte[] toByteArray(final HttpEntity entity) throws IOException {
        Args.notNull(entity, "HttpEntity");
        final int contentLength = toContentLength((int) Args.checkContentLength(entity));
        try (final InputStream inStream = entity.getContent()) {
            if (inStream == null) {
                return null;
            }
            final ByteArrayBuffer buffer = new ByteArrayBuffer(contentLength);
            final byte[] tmp = new byte[DEFAULT_BYTE_BUFFER_SIZE];
            int l;
            while ((l = inStream.read(tmp)) != -1) {
                buffer.append(tmp, 0, l);
            }
            return buffer.toByteArray();
        }
    }

    /**
     * Reads the contents of an entity and return it as a byte array.
     *
     * @param entity the entity to read from=
     * @return byte array containing the entity content. May be null if
     *   {@link HttpEntity#getContent()} is null.
     * @param maxResultLength
     *            The maximum size of the String to return; use it to guard against unreasonable or malicious processing.
     * @throws IOException if an error occurs reading the input stream
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     */
    public static byte[] toByteArray(final HttpEntity entity, final int maxResultLength) throws IOException {
        Args.notNull(entity, "HttpEntity");
        final int contentLength = toContentLength((int) Args.checkContentLength(entity));
        try (final InputStream inStream = entity.getContent()) {
            if (inStream == null) {
                return null;
            }
            final ByteArrayBuffer buffer = new ByteArrayBuffer(Math.min(maxResultLength, contentLength));
            final byte[] tmp = new byte[DEFAULT_BYTE_BUFFER_SIZE];
            int l;
            while ((l = inStream.read(tmp, 0, Math.min(DEFAULT_BYTE_BUFFER_SIZE, buffer.capacity() - buffer.length()))) > 0) {
                buffer.append(tmp, 0, l);
            }
            return buffer.toByteArray();
        }
    }

    private static CharArrayBuffer toCharArrayBuffer(final InputStream inStream, final int contentLength,
            final Charset charset, final int maxResultLength) throws IOException {
        Args.notNull(inStream, "InputStream");
        Args.positive(maxResultLength, "maxResultLength");
        final Charset actualCharset = charset == null ? DEFAULT_CHARSET : charset;
        final CharArrayBuffer buf = new CharArrayBuffer(
                Math.min(maxResultLength, contentLength > 0 ? contentLength : DEFAULT_CHAR_BUFFER_SIZE));
        final Reader reader = new InputStreamReader(inStream, actualCharset);
        final char[] tmp = new char[DEFAULT_CHAR_BUFFER_SIZE];
        int chReadCount;
        while ((chReadCount = reader.read(tmp)) != -1 && buf.length() < maxResultLength) {
            buf.append(tmp, 0, chReadCount);
        }
        buf.setLength(Math.min(buf.length(), maxResultLength));
        return buf;
    }

    private static final Map<String, ContentType> CONTENT_TYPE_MAP;
    static {
        final ContentType[] contentTypes = {
                ContentType.APPLICATION_ATOM_XML,
                ContentType.APPLICATION_FORM_URLENCODED,
                ContentType.APPLICATION_JSON,
                ContentType.APPLICATION_SVG_XML,
                ContentType.APPLICATION_XHTML_XML,
                ContentType.APPLICATION_XML,
                ContentType.MULTIPART_FORM_DATA,
                ContentType.TEXT_HTML,
                ContentType.TEXT_PLAIN,
                ContentType.TEXT_XML };
        final HashMap<String, ContentType> map = new HashMap<>();
        for (final ContentType contentType: contentTypes) {
            map.put(contentType.getMimeType(), contentType);
        }
        CONTENT_TYPE_MAP = Collections.unmodifiableMap(map);
    }

    private static String toString(final HttpEntity entity, final ContentType contentType, final int maxResultLength)
            throws IOException {
        Args.notNull(entity, "HttpEntity");
        final int contentLength = toContentLength((int) Args.checkContentLength(entity));
        try (final InputStream inStream = entity.getContent()) {
            if (inStream == null) {
                return null;
            }
            Charset charset = null;
            if (contentType != null) {
                charset = contentType.getCharset();
                if (charset == null) {
                    final ContentType defaultContentType = CONTENT_TYPE_MAP.get(contentType.getMimeType());
                    charset = defaultContentType != null ? defaultContentType.getCharset() : null;
                }
            }
            return toCharArrayBuffer(inStream, contentLength, charset, maxResultLength).toString();
        }
    }

    /**
     * Gets the entity content as a String, using the provided default character set
     * if none is found in the entity.
     * If defaultCharset is null, the default "ISO-8859-1" is used.
     *
     * @param entity must not be null
     * @param defaultCharset character set to be applied if none found in the entity,
     * or if the entity provided charset is invalid or not available.
     * @return the entity content as a String. May be null if
     *   {@link HttpEntity#getContent()} is null.
     * @throws ParseException if header elements cannot be parsed
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     * @throws IOException if an error occurs reading the input stream
     * @throws java.nio.charset.UnsupportedCharsetException Thrown when the named entity's charset is not available in
     * this instance of the Java virtual machine and no defaultCharset is provided.
     */
    public static String toString(
            final HttpEntity entity, final Charset defaultCharset) throws IOException, ParseException {
        return toString(entity, defaultCharset, DEFAULT_ENTITY_RETURN_MAX_LENGTH);
    }

    /**
     * Gets the entity content as a String, using the provided default character set
     * if none is found in the entity.
     * If defaultCharset is null, the default "ISO-8859-1" is used.
     *
     * @param entity must not be null
     * @param defaultCharset character set to be applied if none found in the entity,
     * or if the entity provided charset is invalid or not available.
     * @param maxResultLength
     *            The maximum size of the String to return; use it to guard against unreasonable or malicious processing.
     * @return the entity content as a String. May be null if
     *   {@link HttpEntity#getContent()} is null.
     * @throws ParseException if header elements cannot be parsed
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     * @throws IOException if an error occurs reading the input stream
     * @throws java.nio.charset.UnsupportedCharsetException Thrown when the named entity's charset is not available in
     * this instance of the Java virtual machine and no defaultCharset is provided.
     */
    public static String toString(
            final HttpEntity entity, final Charset defaultCharset, final int maxResultLength) throws IOException, ParseException {
        Args.notNull(entity, "HttpEntity");
        ContentType contentType = null;
        try {
            contentType = ContentType.parse(entity.getContentType());
        } catch (final UnsupportedCharsetException ex) {
            if (defaultCharset == null) {
                throw new UnsupportedEncodingException(ex.getMessage());
            }
        }
        if (contentType != null) {
            if (contentType.getCharset() == null) {
                contentType = contentType.withCharset(defaultCharset);
            }
        } else {
            contentType = ContentType.DEFAULT_TEXT.withCharset(defaultCharset);
        }
        return toString(entity, contentType, maxResultLength);
    }

    /**
     * Gets the entity content as a String, using the provided default character set
     * if none is found in the entity.
     * If defaultCharset is null, the default "ISO-8859-1" is used.
     *
     * @param entity must not be null
     * @param defaultCharset character set to be applied if none found in the entity
     * @return the entity content as a String. May be null if
     *   {@link HttpEntity#getContent()} is null.
     * @throws ParseException if header elements cannot be parsed
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     * @throws IOException if an error occurs reading the input stream
     * @throws java.nio.charset.UnsupportedCharsetException Thrown when the named charset is not available in
     * this instance of the Java virtual machine
     */
    public static String toString(
            final HttpEntity entity, final String defaultCharset) throws IOException, ParseException {
        return toString(entity, defaultCharset, DEFAULT_ENTITY_RETURN_MAX_LENGTH);
    }

    /**
     * Gets the entity content as a String, using the provided default character set
     * if none is found in the entity.
     * If defaultCharset is null, the default "ISO-8859-1" is used.
     *
     * @param entity must not be null
     * @param defaultCharset character set to be applied if none found in the entity
     * @param maxResultLength
     *            The maximum size of the String to return; use it to guard against unreasonable or malicious processing.
     * @return the entity content as a String. May be null if
     *   {@link HttpEntity#getContent()} is null.
     * @throws ParseException if header elements cannot be parsed
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     * @throws IOException if an error occurs reading the input stream
     * @throws java.nio.charset.UnsupportedCharsetException Thrown when the named charset is not available in
     * this instance of the Java virtual machine
     */
    public static String toString(
            final HttpEntity entity, final String defaultCharset, final int maxResultLength) throws IOException, ParseException {
        return toString(entity, defaultCharset != null ? Charset.forName(defaultCharset) : null, maxResultLength);
    }

    /**
     * Reads the contents of an entity and return it as a String.
     * The content is converted using the character set from the entity (if any),
     * failing that, "ISO-8859-1" is used.
     *
     * @param entity the entity to convert to a string; must not be null
     * @return String containing the content.
     * @throws ParseException if header elements cannot be parsed
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     * @throws IOException if an error occurs reading the input stream
     * @throws java.nio.charset.UnsupportedCharsetException Thrown when the named charset is not available in
     * this instance of the Java virtual machine
     */
    public static String toString(final HttpEntity entity) throws IOException, ParseException {
        return toString(entity, DEFAULT_ENTITY_RETURN_MAX_LENGTH);
    }

    /**
     * Reads the contents of an entity and return it as a String.
     * The content is converted using the character set from the entity (if any),
     * failing that, "ISO-8859-1" is used.
     *
     * @param entity the entity to convert to a string; must not be null
     * @param maxResultLength
     *            The maximum size of the String to return; use it to guard against unreasonable or malicious processing.
     * @return String containing the content.
     * @throws ParseException if header elements cannot be parsed
     * @throws IllegalArgumentException if entity is null or if content length &gt; Integer.MAX_VALUE
     * @throws IOException if an error occurs reading the input stream
     * @throws java.nio.charset.UnsupportedCharsetException Thrown when the named charset is not available in
     * this instance of the Java virtual machine
     */
    public static String toString(final HttpEntity entity, final int maxResultLength) throws IOException, ParseException {
        Args.notNull(entity, "HttpEntity");
        return toString(entity, ContentType.parse(entity.getContentType()), maxResultLength);
    }

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from an {@link HttpEntity}.
     * The encoding is taken from the entity's Content-Encoding header.
     * <p>
     * This is typically used while parsing an HTTP POST.
     * </p>
     *
     * @param entity
     *            The entity to parse
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     * @throws IOException
     *             If there was an exception getting the entity's data.
     */
    public static List<NameValuePair> parse(final HttpEntity entity) throws IOException {
        return parse(entity, DEFAULT_ENTITY_RETURN_MAX_LENGTH);
    }

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from an {@link HttpEntity}.
     * The encoding is taken from the entity's Content-Encoding header.
     * <p>
     * This is typically used while parsing an HTTP POST.
     * </p>
     *
     * @param entity
     *            The entity to parse
     * @param maxStreamLength
     *            The maximum size of the stream to read; use it to guard against unreasonable or malicious processing.
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     * @throws IOException
     *             If there was an exception getting the entity's data.
     */
    public static List<NameValuePair> parse(final HttpEntity entity, final int maxStreamLength) throws IOException {
        Args.notNull(entity, "HttpEntity");
        final int contentLength = toContentLength((int) Args.checkContentLength(entity));
        final ContentType contentType = ContentType.parse(entity.getContentType());
        if (!ContentType.APPLICATION_FORM_URLENCODED.isSameMimeType(contentType)) {
            return Collections.emptyList();
        }
        final Charset charset = contentType.getCharset() != null ? contentType.getCharset()
                        : DEFAULT_CHARSET;
        final CharArrayBuffer buf;
        try (final InputStream inStream = entity.getContent()) {
            if (inStream == null) {
                return Collections.emptyList();
            }
            buf = toCharArrayBuffer(inStream, contentLength, charset, maxStreamLength);

        }
        if (buf.isEmpty()) {
            return Collections.emptyList();
        }
        return WWWFormCodec.parse(buf, charset);
    }

}
