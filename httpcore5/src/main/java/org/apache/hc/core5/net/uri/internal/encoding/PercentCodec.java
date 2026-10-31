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

package org.apache.hc.core5.net.uri.internal.encoding;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.uri.internal.utils.Ascii;

/**
 * Minimal percent-encoding helpers for RFC 3986.
 * Only what is required by {@code Rfc3986Uri#optimize()} and equivalence.
 *
 * @since 5.4
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class PercentCodec {
    private PercentCodec() {
    }

    /**
     * Decode percent-escapes. If {@code unreservedOnly} is true, only decode escapes that map
     * to ASCII unreserved; otherwise decode all valid %XX sequences.
     */
    public static String decode(final String s, final boolean unreservedOnly) {
        if (s == null) {
            return null;
        }
        final int n = s.length();
        boolean changed = false;
        final StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            final char ch = s.charAt(i);
            if (ch == '%' && i + 2 < n) {
                final int h1 = Ascii.hexVal(s.charAt(i + 1));
                final int h2 = Ascii.hexVal(s.charAt(i + 2));
                if (h1 >= 0 && h2 >= 0) {
                    final int b = h1 << 4 | h2;
                    final char decoded = (char) (b & 0xFF);
                    if (!unreservedOnly || Ascii.isUnreserved(decoded)) {
                        out.append(decoded);
                        i += 2;
                        changed = true;
                        continue;
                    }
                }
            }
            out.append(ch);
        }
        return changed ? out.toString() : s;
    }

    /**
     * Uppercase hex digits in any valid percent-escapes.
     */
    public static String uppercaseHexInPercents(final String s) {
        if (s == null) {
            return null;
        }
        final int n = s.length();
        boolean changed = false;
        final StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            final char ch = s.charAt(i);
            if (ch == '%' && i + 2 < n) {
                final int h1 = Ascii.hexVal(s.charAt(i + 1));
                final int h2 = Ascii.hexVal(s.charAt(i + 2));
                if (h1 >= 0 && h2 >= 0) {
                    out.append('%');
                    out.append(Ascii.toHexUpper(h1));
                    out.append(Ascii.toHexUpper(h2));
                    i += 2;
                    changed = true;
                    continue;
                }
            }
            out.append(ch);
        }
        return changed ? out.toString() : s;
    }

    /**
     * Strict path encoder used by optimize(): preserves '/' and valid %HH sequences,
     * percent-encodes all other characters using UTF-8.
     */
    public static String encodeStrictPath(final String s) {
        if (s == null) {
            return null;
        }
        final int n = s.length();
        final StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            final char ch = s.charAt(i);
            // Preserve existing percent-escapes
            if (ch == '%' && i + 2 < n) {
                final int h1 = Ascii.hexVal(s.charAt(i + 1));
                final int h2 = Ascii.hexVal(s.charAt(i + 2));
                if (h1 >= 0 && h2 >= 0) {
                    out.append('%').append(Ascii.toHexUpper(h1)).append(Ascii.toHexUpper(h2));
                    i += 2;
                    continue;
                }
            }
            if (ch == '/' || Ascii.isUnreserved(ch)) {
                out.append(ch);
            } else {
                // encode as UTF-8
                final byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                for (final byte b : bytes) {
                    out.append('%');
                    out.append(Ascii.toHexUpper(b >> 4 & 0xF));
                    out.append(Ascii.toHexUpper(b & 0xF));
                }
            }
        }
        return out.toString();
    }
}
