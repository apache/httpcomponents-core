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
import java.util.BitSet;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.message.TokenParser;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * A collection of utilities for encoding URLs.
 *
 * @since 4.0
 */
public class URLEncodedUtils {

    /**
     * The default HTML form content type.
     */
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private static final char QP_SEP_A = '&';
    private static final char QP_SEP_S = ';';
    private static final String NAME_VALUE_SEPARATOR = "=";

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as built from the URI's query portion. For example, a URI
     * of {@code http://example.org/path/to/file?a=1&b=2&c=3} would return a list of three NameValuePairs, one for a=1,
     * one for b=2, and one for c=3. By convention, {@code '&'} and {@code ';'} are accepted as parameter separators.
     * <p>
     * This is typically useful while parsing an HTTP PUT.
     *
     * This API is currently only used for testing.
     *
     * @param uri
     *        URI to parse
     * @param charset
     *        Charset to use while parsing the query
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     *
     * @since 4.5
     */
    public static List <NameValuePair> parse(final URI uri, final Charset charset) {
        Args.notNull(uri, "URI");
        final String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            return parse(query, charset);
        }
        return createEmptyList();
    }

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from the given string using the given character
     * encoding. By convention, {@code '&'} and {@code ';'} are accepted as parameter separators.
     *
     * @param s
     *            text to parse.
     * @param charset
     *            Encoding to use when decoding the parameters.
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     *
     * @since 4.2
     */
    public static List<NameValuePair> parse(final String s, final Charset charset) {
        if (s == null) {
            return createEmptyList();
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        return parse(buffer, charset, QP_SEP_A, QP_SEP_S);
    }

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from the given string using the given character
     * encoding.
     *
     * @param s
     *            text to parse.
     * @param charset
     *            Encoding to use when decoding the parameters.
     * @param separators
     *            element separators.
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     *
     * @since 4.3
     */
    public static List<NameValuePair> parse(final String s, final Charset charset, final char... separators) {
        if (s == null) {
            return createEmptyList();
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        return parse(buffer, charset, separators);
    }

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from the given string using
     * the given character encoding.
     *
     * @param buf
     *            text to parse.
     * @param charset
     *            Encoding to use when decoding the parameters.
     * @param separators
     *            element separators.
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     *
     * @since 4.4
     */
    public static List<NameValuePair> parse(
            final CharArrayBuffer buf, final Charset charset, final char... separators) {
        Args.notNull(buf, "Char array buffer");
        final TokenParser tokenParser = TokenParser.INSTANCE;
        final BitSet delimSet = new BitSet();
        for (final char separator: separators) {
            delimSet.set(separator);
        }
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final List<NameValuePair> list = new ArrayList<>();
        while (!cursor.atEnd()) {
            delimSet.set('=');
            final String name = tokenParser.parseToken(buf, cursor, delimSet);
            String value = null;
            if (!cursor.atEnd()) {
                final int delim = buf.charAt(cursor.getPos());
                cursor.updatePos(cursor.getPos() + 1);
                if (delim == '=') {
                    delimSet.clear('=');
                    value = tokenParser.parseValue(buf, cursor, delimSet);
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
        final StringBuilder result = new StringBuilder();
        for (final NameValuePair parameter : parameters) {
            final String encodedName = encodeFormFields(parameter.getName(), charset);
            final String encodedValue = encodeFormFields(parameter.getValue(), charset);
            if (result.length() > 0) {
                result.append(parameterSeparator);
            }
            result.append(encodedName);
            if (encodedValue != null) {
                result.append(NAME_VALUE_SEPARATOR);
                result.append(encodedValue);
            }
        }
        return result.toString();
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
        PATHSAFE.set('/'); // segment separator
        PATHSAFE.set(';'); // param separator
        PATHSAFE.set(':'); // rest as per list in 2396, i.e. : @ & = + $ ,
        PATHSAFE.set('@');
        PATHSAFE.set('&');
        PATHSAFE.set('=');
        PATHSAFE.set('+');
        PATHSAFE.set('$');
        PATHSAFE.set(',');

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

    private static String urlEncode(
            final String content,
            final Charset charset,
            final BitSet safechars,
            final boolean blankAsPlus) {
        if (content == null) {
            return null;
        }
        final StringBuilder buf = new StringBuilder();
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
        return buf.toString();
    }

    /**
     * Decode/unescape a portion of a URL, to use with the query part ensure {@code plusAsBlank} is true.
     *
     * @param content the portion to decode
     * @param charset the charset to use
     * @param plusAsBlank if {@code true}, then convert '+' to space (e.g. for www-url-form-encoded content), otherwise leave as is.
     * @return encoded string
     */
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

    /**
     * Decode/unescape www-url-form-encoded content.
     *
     * @param content the content to decode, will decode '+' as space
     * @param charset the charset to use
     * @return encoded string
     */
    private static String decodeFormFields (final String content, final Charset charset) {
        if (content == null) {
            return null;
        }
        return urlDecode(content, charset != null ? charset : StandardCharsets.UTF_8, true);
    }

    /**
     * Encode/escape www-url-form-encoded content.
     * <p>
     * Uses the {@link #URLENCODER} set of characters, rather than
     * the {@link #UNRESERVED} set; this is for compatibilty with previous
     * releases, URLEncoder.encode() and most browsers.
     *
     * @param content the content to encode, will convert space to '+'
     * @param charset the charset to use
     * @return encoded string
     */
    private static String encodeFormFields (final String content, final Charset charset) {
        if (content == null) {
            return null;
        }
        return urlEncode(content, charset != null ? charset : StandardCharsets.UTF_8, URLENCODER, true);
    }

    /**
     * Encode a String using the {@link #USERINFO} set of characters.
     * <p>
     * Used by URIBuilder to encode the userinfo segment.
     *
     * @param content the string to encode, does not convert space to '+'
     * @param charset the charset to use
     * @return the encoded string
     */
    static String encUserInfo(final String content, final Charset charset) {
        return urlEncode(content, charset, USERINFO, false);
    }

    /**
     * Encode a String using the {@link #URIC} set of characters.
     * <p>
     * Used by URIBuilder to encode the query and fragment segments.
     *
     * @param content the string to encode, does not convert space to '+'
     * @param charset the charset to use
     * @return the encoded string
     */
    static String encUric(final String content, final Charset charset) {
        return urlEncode(content, charset, URIC, false);
    }

    /**
     * Encode a String using the {@link #PATHSAFE} set of characters.
     * <p>
     * Used by URIBuilder to encode path segments.
     *
     * @param content the string to encode, does not convert space to '+'
     * @param charset the charset to use
     * @return the encoded string
     */
    static String encPath(final String content, final Charset charset) {
        return urlEncode(content, charset, PATHSAFE, false);
    }

}
