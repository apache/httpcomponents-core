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

package org.apache.hc.core5.net;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.message.TokenParser;
import org.apache.hc.core5.util.Args;

/**
 * A collection of utilities for encoding URLs.
 *
 * @since 4.0
 */
public class URLEncodedUtils {

    private static final char QP_SEP_A = '&';
    private static final char QP_SEP_S = ';';
    private static final String NAME_VALUE_SEPARATOR = "=";
    private static final char PATH_SEPARATOR = '/';

    private static final BitSet PATH_SEPARATORS     = new BitSet(256);
    static {
        PATH_SEPARATORS.set(PATH_SEPARATOR);
    }

    /**
     * Returns a list of {@link NameValuePair}s URI query parameters.
     * By convention, {@code '&'} and {@code ';'} are accepted as parameter separators.
     *
     * @param uri input URI.
     * @param charset parameter charset.
     * @return list of query parameters.
     *
     * @since 4.5
     */
    public static List<NameValuePair> parse(final URI uri, final Charset charset) {
        Args.notNull(uri, "URI");
        final String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            return parse(query, charset);
        }
        return createEmptyList();
    }

    /**
     * Returns a list of {@link NameValuePair}s URI query parameters.
     * By convention, {@code '&'} and {@code ';'} are accepted as parameter separators.
     *
     * @param s URI query component.
     * @param charset charset to use when decoding the parameters.
     * @return list of query parameters.
     *
     * @since 4.2
     */
    public static List<NameValuePair> parse(final CharSequence s, final Charset charset) {
        if (s == null) {
            return createEmptyList();
        }
        return parse(s, charset, QP_SEP_A, QP_SEP_S);
    }

    /**
     * Returns a list of {@link NameValuePair}s parameters.
     *
     * @param s input text.
     * @param charset parameter charset.
     * @param separators parameter separators.
     * @return list of query parameters.
     *
     * @since 4.4
     */
    public static List<NameValuePair> parse(
            final CharSequence s, final Charset charset, final char... separators) {
        Args.notNull(s, "Char sequence");
        final TokenParser tokenParser = TokenParser.INSTANCE;
        final BitSet delimSet = new BitSet();
        for (final char separator: separators) {
            delimSet.set(separator);
        }
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final List<NameValuePair> list = new ArrayList<>();
        while (!cursor.atEnd()) {
            delimSet.set('=');
            final String name = tokenParser.parseToken(s, cursor, delimSet);
            String value = null;
            if (!cursor.atEnd()) {
                final int delim = s.charAt(cursor.getPos());
                cursor.updatePos(cursor.getPos() + 1);
                if (delim == '=') {
                    delimSet.clear('=');
                    value = tokenParser.parseToken(s, cursor, delimSet);
                    if (!cursor.atEnd()) {
                        cursor.updatePos(cursor.getPos() + 1);
                    }
                }
            }
            if (!name.isEmpty()) {
                list.add(new BasicNameValuePair(
                        decodeFormFields(name, charset),
                        decodeFormFields(value, charset)));
            }
        }
        return list;
    }

    static List<String> splitSegments(final CharSequence s, final BitSet separators) {
        final ParserCursor cursor = new ParserCursor(0, s.length());
        // Skip leading separator
        if (cursor.atEnd()) {
            return Collections.emptyList();
        }
        if (separators.get(s.charAt(cursor.getPos()))) {
            cursor.updatePos(cursor.getPos() + 1);
        }
        final List<String> list = new ArrayList<>();
        final StringBuilder buf = new StringBuilder();
        for (;;) {
            if (cursor.atEnd()) {
                list.add(buf.toString());
                break;
            }
            final char current = s.charAt(cursor.getPos());
            if (separators.get(current)) {
                list.add(buf.toString());
                buf.setLength(0);
            } else {
                buf.append(current);
            }
            cursor.updatePos(cursor.getPos() + 1);
        }
        return list;
    }

    static List<String> splitPathSegments(final CharSequence s) {
        return splitSegments(s, PATH_SEPARATORS);
    }

    /**
     * Returns a list of URI path segments.
     *
     * @param s URI path component.
     * @param charset parameter charset.
     * @return list of segments.
     *
     * @since 4.5
     */
    public static List<String> parsePathSegments(final CharSequence s, final Charset charset) {
        Args.notNull(s, "Char sequence");
        final List<String> list = splitPathSegments(s);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, urlDecode(list.get(i), charset != null ? charset : StandardCharsets.UTF_8, false));
        }
        return list;
    }

    /**
     * Returns a list of URI path segments.
     *
     * @param s URI path component.
     * @return list of segments.
     *
     * @since 4.5
     */
    public static List<String> parsePathSegments(final CharSequence s) {
        return parsePathSegments(s, StandardCharsets.UTF_8);
    }

    static void formatSegments(final StringBuilder buf, final Iterable<String> segments, final Charset charset) {
        for (final String segment : segments) {
            buf.append(PATH_SEPARATOR);
            urlEncode(buf, segment, charset, PATHSAFE, false);
        }
    }

    /**
     * Returns a string consisting of joint encoded path segments.
     *
     * @param segments the segments.
     * @param charset parameter charset.
     * @return URI path component
     *
     * @since 4.5
     */
    public static String formatSegments(final Iterable<String> segments, final Charset charset) {
        Args.notNull(segments, "Segments");
        final StringBuilder buf = new StringBuilder();
        formatSegments(buf, segments, charset);
        return buf.toString();
    }

    /**
     * Returns a string consisting of joint encoded path segments.
     *
     * @param segments the segments.
     * @return URI path component
     *
     * @since 4.5
     */
    public static String formatSegments(final String... segments) {
        return formatSegments(Arrays.asList(segments), StandardCharsets.UTF_8);
    }

    static void formatNameValuePairs(
            final StringBuilder buf,
            final Iterable<? extends NameValuePair> parameters,
            final char parameterSeparator,
            final Charset charset) {
        int i = 0;
        for (final NameValuePair parameter : parameters) {
            if (i > 0) {
                buf.append(parameterSeparator);
            }
            encodeFormFields(buf, parameter.getName(), charset);
            if (parameter.getValue() != null) {
                buf.append(NAME_VALUE_SEPARATOR);
                encodeFormFields(buf, parameter.getValue(), charset);
            }
            i++;
        }
    }

    static void formatParameters(
            final StringBuilder buf,
            final Iterable<? extends NameValuePair> parameters,
            final Charset charset) {
        formatNameValuePairs(buf, parameters, QP_SEP_A, charset);
    }

    /**
     * Returns a String that is suitable for use as an {@code application/x-www-form-urlencoded}
     * list of parameters in an HTTP PUT or HTTP POST.
     *
     * @param parameters  The parameters to include.
     * @param parameterSeparator The parameter separator, by convention, {@code '&'} or {@code ';'}.
     * @param charset The encoding to use.
     * @return An {@code application/x-www-form-urlencoded} string
     *
     * @since 4.3
     */
    public static String format(
            final Iterable<? extends NameValuePair> parameters,
            final char parameterSeparator,
            final Charset charset) {
        Args.notNull(parameters, "Parameters");
        final StringBuilder buf = new StringBuilder();
        formatNameValuePairs(buf, parameters, parameterSeparator, charset);
        return buf.toString();
    }

    /**
     * Returns a String that is suitable for use as an {@code application/x-www-form-urlencoded}
     * list of parameters in an HTTP PUT or HTTP POST.
     *
     * @param parameters  The parameters to include.
     * @param charset The encoding to use.
     * @return An {@code application/x-www-form-urlencoded} string
     *
     * @since 4.2
     */
    public static String format(
            final Iterable<? extends NameValuePair> parameters,
            final Charset charset) {
        return format(parameters, QP_SEP_A, charset);
    }

    /**
     * Unreserved characters, i.e. alphanumeric, plus: {@code _ - ! . ~ ' ( ) *}
     * <p>
     *  This list is the same as the {@code unreserved} list in
     *  <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC 2396</a>
     */
    private static final BitSet UNRESERVED   = new BitSet(256);
    /**
     * Punctuation characters: , ; : $ & + =
     * <p>
     * These are the additional characters allowed by userinfo.
     */
    private static final BitSet PUNCT        = new BitSet(256);
    /** Characters which are safe to use in userinfo,
     * i.e. {@link #UNRESERVED} plus {@link #PUNCT}uation */
    private static final BitSet USERINFO     = new BitSet(256);
    /** Characters which are safe to use in a path,
     * i.e. {@link #UNRESERVED} plus {@link #PUNCT}uation plus / @ */
    private static final BitSet PATHSAFE     = new BitSet(256);
    /** Characters which are safe to use in a query or a fragment,
     * i.e. {@link #RESERVED} plus {@link #UNRESERVED} */
    private static final BitSet URIC     = new BitSet(256);

    /**
     * Reserved characters, i.e. {@code ;/?:@&=+$,[]}
     * <p>
     *  This list is the same as the {@code reserved} list in
     *  <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC 2396</a>
     *  as augmented by
     *  <a href="http://www.ietf.org/rfc/rfc2732.txt">RFC 2732</a>
     */
    private static final BitSet RESERVED     = new BitSet(256);


    /**
     * Safe characters for x-www-form-urlencoded data, as per java.net.URLEncoder and browser behaviour,
     * i.e. alphanumeric plus {@code "-", "_", ".", "*"}
     */
    private static final BitSet URLENCODER   = new BitSet(256);

    private static final BitSet PATH_SPECIAL = new BitSet(256);

    static {
        // unreserved chars
        // alpha characters
        for (int i = 'a'; i <= 'z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            UNRESERVED.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            UNRESERVED.set(i);
        }
        UNRESERVED.set('_'); // these are the charactes of the "mark" list
        UNRESERVED.set('-');
        UNRESERVED.set('.');
        UNRESERVED.set('*');
        URLENCODER.or(UNRESERVED); // skip remaining unreserved characters
        UNRESERVED.set('!');
        UNRESERVED.set('~');
        UNRESERVED.set('\'');
        UNRESERVED.set('(');
        UNRESERVED.set(')');
        // punct chars
        PUNCT.set(',');
        PUNCT.set(';');
        PUNCT.set(':');
        PUNCT.set('$');
        PUNCT.set('&');
        PUNCT.set('+');
        PUNCT.set('=');
        // Safe for userinfo
        USERINFO.or(UNRESERVED);
        USERINFO.or(PUNCT);

        // URL path safe
        PATHSAFE.or(UNRESERVED);
        PATHSAFE.set(';'); // param separator
        PATHSAFE.set(':'); // RFC 2396
        PATHSAFE.set('@');
        PATHSAFE.set('&');
        PATHSAFE.set('=');
        PATHSAFE.set('+');
        PATHSAFE.set('$');
        PATHSAFE.set(',');

        PATH_SPECIAL.or(PATHSAFE);
        PATH_SPECIAL.set('/');

        RESERVED.set(';');
        RESERVED.set('/');
        RESERVED.set('?');
        RESERVED.set(':');
        RESERVED.set('@');
        RESERVED.set('&');
        RESERVED.set('=');
        RESERVED.set('+');
        RESERVED.set('$');
        RESERVED.set(',');
        RESERVED.set('['); // added by RFC 2732
        RESERVED.set(']'); // added by RFC 2732

        URIC.or(RESERVED);
        URIC.or(UNRESERVED);
    }

    private static final int RADIX = 16;

    private static List<NameValuePair> createEmptyList() {
        return new ArrayList<>(0);
    }

    private static void urlEncode(
            final StringBuilder buf,
            final String content,
            final Charset charset,
            final BitSet safechars,
            final boolean blankAsPlus) {
        if (content == null) {
            return;
        }
        final ByteBuffer bb = charset.encode(content);
        while (bb.hasRemaining()) {
            final int b = bb.get() & 0xff;
            if (safechars.get(b)) {
                buf.append((char) b);
            } else if (blankAsPlus && b == ' ') {
                buf.append('+');
            } else {
                buf.append("%");
                final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX));
                final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
                buf.append(hex1);
                buf.append(hex2);
            }
        }
    }

    private static String urlDecode(
            final String content,
            final Charset charset,
            final boolean plusAsBlank) {
        if (content == null) {
            return null;
        }
        final ByteBuffer bb = ByteBuffer.allocate(content.length());
        final CharBuffer cb = CharBuffer.wrap(content);
        while (cb.hasRemaining()) {
            final char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
                final char uc = cb.get();
                final char lc = cb.get();
                final int u = Character.digit(uc, 16);
                final int l = Character.digit(lc, 16);
                if (u != -1 && l != -1) {
                    bb.put((byte) ((u << 4) + l));
                } else {
                    bb.put((byte) '%');
                    bb.put((byte) uc);
                    bb.put((byte) lc);
                }
            } else if (plusAsBlank && c == '+') {
                bb.put((byte) ' ');
            } else {
                bb.put((byte) c);
            }
        }
        bb.flip();
        return charset.decode(bb).toString();
    }

    static String decodeFormFields(final String content, final Charset charset) {
        if (content == null) {
            return null;
        }
        return urlDecode(content, charset != null ? charset : StandardCharsets.UTF_8, true);
    }

    static void encodeFormFields(final StringBuilder buf, final String content, final Charset charset) {
        if (content == null) {
            return;
        }
        urlEncode(buf, content, charset != null ? charset : StandardCharsets.UTF_8, URLENCODER, true);
    }

    static void encUserInfo(final StringBuilder buf, final String content, final Charset charset) {
        urlEncode(buf, content, charset != null ? charset : StandardCharsets.UTF_8, USERINFO, false);
    }

    static void encUric(final StringBuilder buf, final String content, final Charset charset) {
        urlEncode(buf, content, charset != null ? charset : StandardCharsets.UTF_8, URIC, false);
    }

}
