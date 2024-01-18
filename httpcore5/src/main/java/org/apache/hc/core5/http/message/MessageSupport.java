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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Support methods for HTTP message processing.
 *
 * @since 5.0
 */
public class MessageSupport {

    private MessageSupport() {
        // Do not allow utility class to be instantiated.
    }

    /**
     * @since 5.3
     */
    public static void formatTokens(final CharArrayBuffer dst, final List<String> tokens) {
        Args.notNull(dst, "Destination");
        if (tokens == null) {
            return;
        }
        for (int i = 0; i < tokens.size(); i++) {
            final String element = tokens.get(i);
            if (i > 0) {
                dst.append(", ");
            }
            dst.append(element);
        }
    }

    public static void formatTokens(final CharArrayBuffer dst, final String... tokens) {
        Args.notNull(dst, "Destination");
        boolean first = true;
        for (final String token : tokens) {
            if (!first) {
                dst.append(", ");
            }
            dst.append(token);
            first = false;
        }
    }

    public static void formatTokens(final CharArrayBuffer dst, final Set<String> tokens) {
        Args.notNull(dst, "Destination");
        if (tokens == null) {
            return;
        }
        boolean first = true;
        for (final String token : tokens) {
            if (!first) {
                dst.append(", ");
            }
            dst.append(token);
            first = false;
        }
    }

    /**
     * @deprecated Use {@link #header(String, Set)}
     */
    @Deprecated
    public static Header format(final String name, final Set<String> tokens) {
        return header(name, tokens);
    }

    /**
     * @since 5.3
     */
    public static Header headerOfTokens(final String name, final List<String> tokens) {
        Args.notBlank(name, "Header name");
        if (tokens == null) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(name);
        buffer.append(": ");
        formatTokens(buffer, tokens);
        return BufferedHeader.create(buffer);
    }

    /**
     * @since 5.3
     */
    public static Header header(final String name, final Set<String> tokens) {
        Args.notBlank(name, "Header name");
        if (tokens == null) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(name);
        buffer.append(": ");
        formatTokens(buffer, tokens);
        return BufferedHeader.create(buffer);
    }

    private static final Tokenizer.Delimiter COMMA = Tokenizer.delimiters(',');
    /**
     * @since 5.3
     */
    public static Header header(final String name, final String... tokens) {
        Args.notBlank(name, "Header name");
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(name);
        buffer.append(": ");
        formatTokens(buffer, tokens);
        return BufferedHeader.create(buffer);
    }

    /**
     * @deprecated use {@link #header(String, String...)}
     */
    @Deprecated
    public static Header format(final String name, final String... tokens) {
        return headerOfTokens(name, Arrays.asList(tokens));
    }

    /**
     * @since 5.3
     */
    public static void parseTokens(final CharSequence src, final ParserCursor cursor, final Consumer<String> consumer) {
        Args.notNull(src, "Source");
        Args.notNull(cursor, "Cursor");
        Args.notNull(consumer, "Consumer");
        while (!cursor.atEnd()) {
            final int pos = cursor.getPos();
            if (src.charAt(pos) == ',') {
                cursor.updatePos(pos + 1);
            }
            final String token = Tokenizer.INSTANCE.parseToken(src, cursor, COMMA);
            consumer.accept(token);
        }
    }

    /**
     * @since 5.3
     */
    public static void parseTokens(final Header header, final Consumer<String> consumer) {
        Args.notNull(header, "Header");
        if (header instanceof FormattedHeader) {
            final CharArrayBuffer buf = ((FormattedHeader) header).getBuffer();
            final ParserCursor cursor = new ParserCursor(0, buf.length());
            cursor.updatePos(((FormattedHeader) header).getValuePos());
            parseTokens(buf, cursor, consumer);
        } else {
            final String value = header.getValue();
            final ParserCursor cursor = new ParserCursor(0, value.length());
            parseTokens(value, cursor, consumer);
        }
    }

    /**
     * @since 5.3
     */
    public static void parseTokens(final MessageHeaders headers, final String headerName, final Consumer<String> consumer) {
        Args.notNull(headers, "Headers");
        final Iterator<Header> it = headers.headerIterator(headerName);
        while (it.hasNext()) {
            parseTokens(it.next(), consumer);
        }
    }

    public static Set<String> parseTokens(final CharSequence src, final ParserCursor cursor) {
        Args.notNull(src, "Source");
        Args.notNull(cursor, "Cursor");
        final Set<String> tokens = new LinkedHashSet<>();
        parseTokens(src, cursor, tokens::add);
        return tokens;
    }

    public static Set<String> parseTokens(final Header header) {
        Args.notNull(header, "Header");
        final Set<String> tokens = new LinkedHashSet<>();
        parseTokens(header, tokens::add);
        return tokens;
    }

    /**
     * @since 5.3
     */
    public static Iterator<String> iterateTokens(final MessageHeaders headers, final String name) {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        return new BasicTokenIterator(headers.headerIterator(name));
    }

    /**
     * @since 5.3
     */
    public static void formatElements(final CharArrayBuffer dst, final List<HeaderElement> elements) {
        Args.notNull(dst, "Destination");
        if (elements == null) {
            return;
        }
        for (int i = 0; i < elements.size(); i++) {
            final HeaderElement element = elements.get(i);
            if (i > 0) {
                dst.append(", ");
            }
            BasicHeaderValueFormatter.INSTANCE.formatHeaderElement(dst, element, false);
        }
    }

    /**
     * @since 5.3
     */
    public static void formatElements(final CharArrayBuffer dst, final HeaderElement... elements) {
        formatElements(dst, Arrays.asList(elements));
    }

    /**
     * @since 5.3
     */
    public static Header headerOfElements(final String name, final List<HeaderElement> elements) {
        Args.notBlank(name, "Header name");
        if (elements == null) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(name);
        buffer.append(": ");
        formatElements(buffer, elements);
        return BufferedHeader.create(buffer);
    }

    /**
     * @since 5.3
     */
    public static Header header(final String name, final HeaderElement... elements) {
        Args.notBlank(name, "Header name");
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(name);
        buffer.append(": ");
        formatElements(buffer, elements);
        return BufferedHeader.create(buffer);
    }

    /**
     * @since 5.3
     */
    public static void parseElements(final CharSequence buffer, final ParserCursor cursor, final Consumer<HeaderElement> consumer) {
        Args.notNull(buffer, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        Args.notNull(consumer, "Consumer");
        while (!cursor.atEnd()) {
            final HeaderElement element = BasicHeaderValueParser.INSTANCE.parseHeaderElement(buffer, cursor);
            consumer.accept(element);
            if (!cursor.atEnd()) {
                final char ch = buffer.charAt(cursor.getPos());
                if (ch == ',') {
                    cursor.updatePos(cursor.getPos() + 1);
                }
            }
        }
    }

    /**
     * @since 5.3
     */
    public static void parseElements(final Header header, final Consumer<HeaderElement> consumer) {
        Args.notNull(header, "Header");
        if (header instanceof FormattedHeader) {
            final CharArrayBuffer buf = ((FormattedHeader) header).getBuffer();
            final ParserCursor cursor = new ParserCursor(0, buf.length());
            cursor.updatePos(((FormattedHeader) header).getValuePos());
            parseElements(buf, cursor, consumer);
        } else {
            final String value = header.getValue();
            final ParserCursor cursor = new ParserCursor(0, value.length());
            parseElements(value, cursor, consumer);
        }
    }

    /**
     * @since 5.3
     */
    public static void parseElements(final MessageHeaders headers, final String headerName, final Consumer<HeaderElement> consumer) {
        Args.notNull(headers, "Headers");
        final Iterator<Header> it = headers.headerIterator(headerName);
        while (it.hasNext()) {
            parseElements(it.next(), consumer);
        }
    }

    /**
     * @deprecated Use {@link #parseElements(Header, Consumer)}
     */
    @Deprecated
    public static HeaderElement[] parse(final Header header) {
        final List<HeaderElement> elements = new ArrayList<>();
        parseElements(header, elements::add);
        return elements.toArray(new HeaderElement[]{});
    }

    /**
     * @since 5.3
     */
    public static List<HeaderElement> parseElements(final Header header) {
        final List<HeaderElement> elements = new ArrayList<>();
        parseElements(header, elements::add);
        return elements;
    }

    public static Iterator<HeaderElement> iterate(final MessageHeaders headers, final String name) {
        Args.notNull(headers, "Message headers");
        Args.notBlank(name, "Header name");
        return new BasicHeaderElementIterator(headers.headerIterator(name));
    }

    /**
     * @since 5.3
     */
    public static void formatParameters(final CharArrayBuffer dst, final List<NameValuePair> params) {
        Args.notNull(dst, "Destination");
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            final NameValuePair param = params.get(i);
            if (i > 0) {
                dst.append("; ");
            }
            BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(dst, param, false);
        }
    }

    /**
     * @since 5.3
     */
    public static void formatParameters(final CharArrayBuffer dst, final NameValuePair... params) {
        Args.notNull(dst, "Destination");
        if (params == null) {
            return;
        }
        boolean first = true;
        for (final NameValuePair param : params) {
            if (!first) {
                dst.append("; ");
            }
            BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(dst, param, false);
            first = false;
        }
    }

    /**
     * @since 5.3
     */
    public static void parseParameters(final CharSequence src, final ParserCursor cursor, final Consumer<NameValuePair> consumer) {
        Args.notNull(src, "Source");
        Args.notNull(cursor, "Cursor");
        Args.notNull(consumer, "Consumer");

        while (!cursor.atEnd()) {
            final NameValuePair param = BasicHeaderValueParser.INSTANCE.parseNameValuePair(src, cursor);
            consumer.accept(param);
            if (!cursor.atEnd()) {
                final char ch = src.charAt(cursor.getPos());
                if (ch == ';') {
                    cursor.updatePos(cursor.getPos() + 1);
                }
                if (ch == ',') {
                    break;
                }
            }
        }
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
                message.setHeader(MessageSupport.header(HttpHeaders.TRAILER, trailerNames));
            }
        }
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

    private final static Set<String> HOP_BY_HOP;

    static {
        final TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.add(HttpHeaders.CONNECTION);
        set.add(HttpHeaders.CONTENT_LENGTH);
        set.add(HttpHeaders.TRANSFER_ENCODING);
        set.add(HttpHeaders.HOST);
        set.add(HttpHeaders.KEEP_ALIVE);
        set.add(HttpHeaders.TE);
        set.add(HttpHeaders.UPGRADE);
        set.add(HttpHeaders.PROXY_AUTHORIZATION);
        set.add("Proxy-Authentication-Info");
        set.add(HttpHeaders.PROXY_AUTHENTICATE);
        HOP_BY_HOP = Collections.unmodifiableSet(set);
    }

    /**
     * @since 5.3
     */
    public static boolean isHopByHop(final String headerName) {
        if (headerName == null) {
            return false;
        }
        return HOP_BY_HOP.contains(headerName);
    }

    /**
     * @since 5.3
     */
    public static Set<String> hopByHopConnectionSpecific(final MessageHeaders headers) {
        final Header connectionHeader = headers.getFirstHeader(HttpHeaders.CONNECTION);
        final String connDirective = connectionHeader != null ? connectionHeader.getValue() : null;
        // Disregard most common 'Close' and 'Keep-Alive' tokens
        if (connDirective != null &&
                !connDirective.equalsIgnoreCase(HeaderElements.CLOSE) &&
                !connDirective.equalsIgnoreCase(HeaderElements.KEEP_ALIVE)) {
            final TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            result.addAll(HOP_BY_HOP);
            result.addAll(parseTokens(connectionHeader));
            return result;
        } else {
            return HOP_BY_HOP;
        }
    }

}
