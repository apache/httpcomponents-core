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

package org.apache.hc.core5.http.message;

import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Support methods for HTTP message processing.
 *
 * @since 5.0
 */
public class MessageSupport {

    /**
     * An empty immutable {@code String} array.
     */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private MessageSupport() {
        // Do not allow utility class to be instantiated.
    }

    public static void formatTokens(final CharArrayBuffer dst, final String... tokens) {
        Args.notNull(dst, "Destination");
        for (int i = 0; i < tokens.length; i++) {
            final String element = tokens[i];
            if (i > 0) {
                dst.append(", ");
            }
            dst.append(element);
        }
    }

    public static void formatTokens(final CharArrayBuffer dst, final Set<String> tokens) {
        Args.notNull(dst, "Destination");
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        formatTokens(dst, tokens.toArray(EMPTY_STRING_ARRAY));
    }

    public static Header format(final String name, final Set<String> tokens) {
        Args.notBlank(name, "Header name");
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(name);
        buffer.append(": ");
        formatTokens(buffer, tokens);
        return BufferedHeader.create(buffer);
    }

    public static Header format(final String name, final String... tokens) {
        Args.notBlank(name, "Header name");
        if (tokens == null || tokens.length == 0) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(name);
        buffer.append(": ");
        formatTokens(buffer, tokens);
        return BufferedHeader.create(buffer);
    }

    private static final BitSet COMMA = Tokenizer.INIT_BITSET(',');

    public static Set<String> parseTokens(final CharSequence src, final ParserCursor cursor) {
        Args.notNull(src, "Source");
        Args.notNull(cursor, "Cursor");
        final Set<String> tokens = new LinkedHashSet<>();
        while (!cursor.atEnd()) {
            final int pos = cursor.getPos();
            if (src.charAt(pos) == ',') {
                cursor.updatePos(pos + 1);
            }
            final String token = Tokenizer.INSTANCE.parseToken(src, cursor, COMMA);
            if (!TextUtils.isBlank(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static Set<String> parseTokens(final Header header) {
        Args.notNull(header, "Header");
        if (header instanceof FormattedHeader) {
            final CharArrayBuffer buf = ((FormattedHeader) header).getBuffer();
            final ParserCursor cursor = new ParserCursor(0, buf.length());
            cursor.updatePos(((FormattedHeader) header).getValuePos());
            return parseTokens(buf, cursor);
        }
        final String value = header.getValue();
        final ParserCursor cursor = new ParserCursor(0, value.length());
        return parseTokens(value, cursor);
    }

    public static void addContentTypeHeader(final HttpMessage message, final EntityDetails entity) {
        if (entity != null && entity.getContentType() != null && !message.containsHeader(HttpHeaders.CONTENT_TYPE)) {
            message.addHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, entity.getContentType()));
        }
    }

    public static void addContentEncodingHeader(final HttpMessage message, final EntityDetails entity) {
        if (entity != null && entity.getContentEncoding() != null && !message.containsHeader(HttpHeaders.CONTENT_ENCODING)) {
            message.addHeader(new BasicHeader(HttpHeaders.CONTENT_ENCODING, entity.getContentEncoding()));
        }
    }

    public static void addTrailerHeader(final HttpMessage message, final EntityDetails entity) {
        if (entity != null && !message.containsHeader(HttpHeaders.TRAILER)) {
            final Set<String> trailerNames = entity.getTrailerNames();
            if (trailerNames != null && !trailerNames.isEmpty()) {
                message.setHeader(MessageSupport.format(HttpHeaders.TRAILER, trailerNames));
            }
        }
    }

    public static Iterator<HeaderElement> iterate(final MessageHeaders headers, final String name) {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        return new BasicHeaderElementIterator(headers.headerIterator(name));
    }

    public static HeaderElement[] parse(final Header header) {
        Args.notNull(header, "Headers");
        final String value = header.getValue();
        if (value == null) {
            return new HeaderElement[] {};
        }
        final ParserCursor cursor = new ParserCursor(0, value.length());
        return BasicHeaderValueParser.INSTANCE.parseElements(value, cursor);
    }

    /**
     * @since  5.0
     */
    public static boolean canResponseHaveBody(final String method, final HttpResponse response) {
        if (Method.HEAD.isSame(method)) {
            return false;
        }
        final int status = response.getCode();
        if (Method.CONNECT.isSame(method) && status == HttpStatus.SC_OK) {
            return false;
        }
        return status >= HttpStatus.SC_SUCCESS
                && status != HttpStatus.SC_NO_CONTENT
                && status != HttpStatus.SC_NOT_MODIFIED;
    }

}
