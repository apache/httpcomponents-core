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
package org.apache.hc.core5.net.uri;

import java.nio.charset.Charset;
import java.util.function.IntPredicate;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Percent-encoding/decoding helpers with component-specific allow-lists.
 *
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class PercentCodec {

    // ---- Allowed predicates bound to RFC 3986 ----

    static boolean isPathAllowed(final int c) {
        return c == '/' || Ascii.isPchar(c);
    }

    static boolean isPcharAllowed(final int c) {
        return Ascii.isPchar(c);
    }

    static boolean isQueryAllowed(final int c) {
        return c == '/' || c == '?' || Ascii.isPchar(c);
    }

    static boolean isUserInfoAllowed(final int c) {
        return Ascii.isUnreserved(c) || Ascii.isSubDelim(c) || c == ':';
    }

    static boolean isFragmentAllowed(final int c) {
        return c == '/' || c == '?' || Ascii.isPchar(c);
    }

    // ---- Encoders ----

    static String encodePath(final String s, final Charset cs) {
        return encodeWithPredicate(s, cs, PercentCodec::isPathAllowed);
    }

    static String encodePathSegment(final String s, final Charset cs) {
        return encodeWithPredicate(s, cs, PercentCodec::isPcharAllowed);
    }

    static String encodeQuery(final String s, final Charset cs) {
        return encodeWithPredicate(s, cs, PercentCodec::isQueryAllowed);
    }

    static String encodeUserInfo(final String s, final Charset cs) {
        return encodeWithPredicate(s, cs, PercentCodec::isUserInfoAllowed);
    }

    static String encodeFragment(final String s, final Charset cs) {
        return encodeWithPredicate(s, cs, PercentCodec::isFragmentAllowed);
    }

    static String encodeWithPredicate(final String s, final Charset cs, final IntPredicate allowed) {
        if (s == null || s.isEmpty()) {
            return s == null ? null : "";
        }
        final StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x80 && allowed.test(cp)) {
                out.append((char) cp);
            } else {
                final byte[] bytes = new String(Character.toChars(cp)).getBytes(cs);
                for (final byte b : bytes) {
                    out.append('%');
                    final int v = b & 0xFF;
                    out.append(HEX_UPPER[v >>> 4 & 0x0F]).append(HEX_UPPER[v & 0x0F]);
                }
            }
        }
        return out.toString();
    }

    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    // ---- Decoders used for equivalence/canonicalization ----

    /**
     * Decode percent-escapes. If {@code decodeUnreservedOnly} is true, only decodes
     * escapes that map to ASCII unreserved (safe for RFC equivalence).
     */
    static String decode(final String s, final boolean decodeUnreservedOnly) {
        if (s == null || s.indexOf('%') < 0) {
            return s;
        }
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            final char ch = s.charAt(i);
            if (ch != '%') {
                out.append(ch);
                i++;
                continue;
            }
            // Need at least two hex digits
            if (i + 2 >= s.length() || !Ascii.isHexDigit(s.charAt(i + 1)) || !Ascii.isHexDigit(s.charAt(i + 2))) {
                out.append('%'); // leave as-is if malformed
                i++;
                continue;
            }
            final int hi = hex(s.charAt(i + 1));
            final int lo = hex(s.charAt(i + 2));
            final int v = hi << 4 | lo;
            if (decodeUnreservedOnly) {
                if (Ascii.isUnreserved(v)) {
                    out.append((char) v);
                } else {
                    // keep original triplet
                    out.append('%').append(s.charAt(i + 1)).append(s.charAt(i + 2));
                }
            } else {
                out.append((char) v); // single-byte decode
            }
            i += 3;
        }
        return out.toString();
    }

    static String uppercaseHexInPercents(final String s) {
        if (s == null) {
            return null;
        }
        if (s.indexOf('%') < 0) {
            return s;
        }
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char ch = s.charAt(i);
            if (ch == '%' && i + 2 < s.length()) {
                out.append('%');
                final char c1 = s.charAt(i + 1), c2 = s.charAt(i + 2);
                out.append(upperHex(c1)).append(upperHex(c2));
                i += 2;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static int hex(final char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        final char u = c >= 'a' && c <= 'f' ? (char) (c - 32) : c;
        return u >= 'A' && u <= 'F' ? u - 'A' + 10 : -1;
    }

    private static char upperHex(final char c) {
        if (c >= 'a' && c <= 'f') {
            return (char) (c - 32);
        }
        return c;
    }

    private PercentCodec() {
    }
}
