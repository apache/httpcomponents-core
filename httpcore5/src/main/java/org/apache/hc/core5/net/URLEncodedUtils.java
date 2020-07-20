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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Tokenizer;

/**
 * A collection of utilities for encoding URLs.
 *
 * @since 4.0
 *
 * @deprecated Use {@link URIBuilder} to parse and format {@link URI}s and
 * {@link WWWFormCodec} to parse and format {@code application/x-www-form-urlencoded} forms.
 */
@Deprecated
public class URLEncodedUtils {

    private static final char QP_SEP_A = '&';
    private static final char QP_SEP_S = ';';

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
        return new ArrayList<>(0);
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
            return new ArrayList<>(0);
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
        final Tokenizer tokenParser = Tokenizer.INSTANCE;
        final BitSet delimSet = new BitSet();
        for (final char separator: separators) {
            delimSet.set(separator);
        }
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
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
                        PercentCodec.decode(name, charset, true),
                        PercentCodec.decode(value, charset, true)));
            }
        }
        return list;
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
        return URIBuilder.parsePath(s, charset);
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
        URIBuilder.formatPath(buf, segments, false, charset);
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
        int i = 0;
        for (final NameValuePair parameter : parameters) {
            if (i > 0) {
                buf.append(parameterSeparator);
            }
            PercentCodec.encode(buf, parameter.getName(), charset, URLENCODER, true);
            if (parameter.getValue() != null) {
                buf.append('=');
                PercentCodec.encode(buf, parameter.getValue(), charset, URLENCODER, true);
            }
            i++;
        }
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

    private static final BitSet URLENCODER   = new BitSet(256);

    static {
        // unreserved chars
        // alpha characters
        for (int i = 'a'; i <= 'z'; i++) {
            URLENCODER.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            URLENCODER.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            URLENCODER.set(i);
        }
        URLENCODER.set('_'); // these are the characters of the "mark" list
        URLENCODER.set('-');
        URLENCODER.set('.');
        URLENCODER.set('*');
    }

}
