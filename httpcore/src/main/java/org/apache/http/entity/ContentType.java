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

import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.annotation.Immutable;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.protocol.HTTP;

/**
 * Content type information consisting of a MIME type and an optional charset.
 * <p/>
 * This class makes no attempts to verify validity of mime-type and charset attributes.
 * The input parameters of the {@link #create(String, String)} method, however, may not
 * contain characters <">, <;>, <,> reserved by the HTTP specification.
 *
 * @since 4.2
 */
@Immutable
public final class ContentType {

    // constants
    public static final ContentType APPLICATION_ATOM_XML = new ContentType(
    		"application/atom+xml", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType APPLICATION_FORM_URLENCODED = new ContentType(
    		"application/x-www-form-urlencoded", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType APPLICATION_JSON = new ContentType(
    		"application/json", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType APPLICATION_OCTET_STREAM = new ContentType(
    		"application/octet-stream", null);
    public static final ContentType APPLICATION_SVG_XML = new ContentType(
    		"application/svg+xml", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType APPLICATION_XHTML_XML = new ContentType(
    		"application/xhtml+xml", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType APPLICATION_XML = new ContentType(
    		"application/xml", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType MULTIPART_FORM_DATA = new ContentType(
    		"multipart/form-data", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType TEXT_HTML = new ContentType(
    		"text/html", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType TEXT_PLAIN = new ContentType(
    		"text/plain", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType TEXT_XML = new ContentType(
    		"text/xml", HTTP.DEFAULT_CONTENT_CHARSET);
    public static final ContentType WILDCARD = new ContentType(
    		"*/*", null);

    // defaults
    public static final ContentType DEFAULT_TEXT = TEXT_PLAIN;
    public static final ContentType DEFAULT_BINARY = APPLICATION_OCTET_STREAM;

    private final String mimeType;
    private final String charset;

    /**
     * Given a mime type and a character set, constructs a ContentType.
     * @param mimeType The mime type to use for the ContentType header.
     * @param charset The optional character set to use with the ContentType header.
     */
    ContentType(final String mimeType, final String charset) {
        this.mimeType = mimeType;
        this.charset = charset;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public String getCharset() {
        return this.charset;
    }

    /**
     * Converts a ContentType to a string which can be used as a ContentType header.
     * If a charset is provided by the ContentType, it will be included in the string.
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(this.mimeType);
        if (this.charset != null) {
            buf.append("; charset=");
            buf.append(this.charset);
        }
        return buf.toString();
    }

    private static boolean valid(final String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == ',' || ch == ';') {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new instance of {@link ContentType}.
     *
     * @param mimeType MIME type. It may not be <code>null</code> or empty. It may not contain
     *        characters <">, <;>, <,> reserved by the HTTP specification.
     * @param charset charset. It may not contain characters <">, <;>, <,> reserved by the HTTP
     *        specification. This parameter is optional.
     * @return content type
     */
    public static ContentType create(final String mimeType, final String charset) {
        if (mimeType == null) {
            throw new IllegalArgumentException("MIME type may not be null");
        }
        String type = mimeType.trim().toLowerCase(Locale.US);
        if (type.length() == 0) {
            throw new IllegalArgumentException("MIME type may not be empty");
        }
        if (!valid(type)) {
            throw new IllegalArgumentException("MIME type may not contain reserved characters");
        }
        String cs = null;
        if (charset != null) {
            cs = charset.trim().toLowerCase(Locale.US);
            if (!valid(cs)) {
                throw new IllegalArgumentException("Charset may not contain reserved characters");
            }
        }
        return new ContentType(type, cs);
    }

    private static ContentType create(final HeaderElement helem) {
        String mimeType = helem.getName();
        String charset = null;
        NameValuePair param = helem.getParameterByName("charset");
        if (param != null) {
            charset = param.getValue();
        }
        return create(mimeType, charset);
    }

    /**
     * Parses textual representation of <code>Content-Type</code> value.
     *
     * @param s text
     * @return content type
     * @throws ParseException if the given text does not represent a valid
     * <code>Content-Type</code> value.
     */
    public static ContentType parse(final String s) throws ParseException {
        if (s == null) {
            throw new IllegalArgumentException("Content type may not be null");
        }
        HeaderElement[] elements = BasicHeaderValueParser.parseElements(s, null);
        if (elements.length > 0) {
            return create(elements[0]);
        } else {
            throw new ParseException("Invalid content type: " + s);
        }
    }

    /**
     * Extracts <code>Content-Type</code> value from {@link HttpEntity} exactly as
     * specified by the <code>Content-Type</code> header of the entity. Returns <code>null</code>
     * if not specified.
     *
     * @param entity HTTP entity
     * @return content type
     * @throws ParseException if the given text does not represent a valid
     * <code>Content-Type</code> value.
     */
    public static ContentType get(final HttpEntity entity) throws ParseException {
        if (entity == null) {
            return null;
        }
        Header header = entity.getContentType();
        if (header != null) {
            HeaderElement[] elements = header.getElements();
            if (elements.length > 0) {
                return create(elements[0]);
            }
        }
        return null;
    }

    /**
     * Extracts <code>Content-Type</code> value from {@link HttpEntity} or returns default value
     * if not explicitly specified.
     *
     * @param entity HTTP entity
     * @return content type
     * @throws ParseException if the given text does not represent a valid
     * <code>Content-Type</code> value.
     */
    public static ContentType getOrDefault(final HttpEntity entity) throws ParseException {
        ContentType contentType = get(entity);
        return contentType != null ? contentType : DEFAULT_TEXT;
    }

}
