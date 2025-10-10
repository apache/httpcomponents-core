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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.TextUtils;

@Internal
public final class ZoneIdSupport {

    private ZoneIdSupport() {
    }

    /**
     * RFC 6874 encoder for ZoneID: emits unreserved characters as-is and percent-encodes
     * everything else using UTF-8 with UPPERCASE hex digits. Existing %HH triplets are
     * passed through unchanged.
     */
    public static String encodeZoneIdRfc6874(final CharSequence raw) {
        if (raw == null || raw.length() == 0) {
            return raw != null ? raw.toString() : null;
        }
        final StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            final char ch = raw.charAt(i);
            if (unreserved(ch)) {
                out.append(ch);
            } else if (ch == '%' && i + 2 < raw.length()
                    && TextUtils.isHex(raw.charAt(i + 1)) && TextUtils.isHex(raw.charAt(i + 2))) {
                // pass through existing %HH
                out.append('%').append(raw.charAt(i + 1)).append(raw.charAt(i + 2));
                i += 2;
            } else {
                final byte[] bytes = String.valueOf(ch).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                final String hex = org.apache.hc.core5.util.TextUtils.toHexString(bytes)
                        .toUpperCase(java.util.Locale.ROOT);
                for (int k = 0; k < hex.length(); k += 2) {
                    out.append('%').append(hex.charAt(k)).append(hex.charAt(k + 1));
                }
            }
        }
        return out.toString();
    }

    /**
     * RFC 6874 decoder for bracket contents of an IPv6 literal.
     * Input: {@code "addr%25<enc-zone>"} → Output internal form: {@code "addr%<decoded-zone>"}.
     * If there is no {@code "%25"} delimiter, returns the input as-is.
     */
    public static String decodeZoneId(final CharSequence host) {
        if (host == null) {
            return null;
        }
        // find "%25"
        int p = -1;
        for (int i = 0; i + 2 < host.length(); i++) {
            if (host.charAt(i) == '%' && host.charAt(i + 1) == '2' && host.charAt(i + 2) == '5') {
                p = i;
                break;
            }
        }
        if (p < 0) {
            return host.toString();
        }
        final CharSequence addrCs = host.subSequence(0, p);
        final CharSequence encZone = host.subSequence(p + 3, host.length());

        final java.io.ByteArrayOutputStream baos =
                new java.io.ByteArrayOutputStream(encZone.length());
        for (int i = 0; i < encZone.length(); i++) {
            final char ch = encZone.charAt(i);
            if (ch == '%' && i + 2 < encZone.length()
                    && TextUtils.isHex(encZone.charAt(i + 1)) && TextUtils.isHex(encZone.charAt(i + 2))) {
                final int hi = Character.digit(encZone.charAt(i + 1), 16);
                final int lo = Character.digit(encZone.charAt(i + 2), 16);
                baos.write((hi << 4) + lo);
                i += 2;
            } else {
                // Allowed unreserved in ZoneID are ASCII; copy as single byte
                baos.write((byte) ch);
            }
        }
        final String zone = new String(baos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        return addrCs.toString() + '%' + zone;
    }

    /**
     * RFC 6874 ZoneID validator:
     * <pre>ZoneID = 1*( unreserved / pct-encoded )</pre>
     * Throws {@link IllegalArgumentException} on invalid input.
     */
    public static void validateZoneIdEncoded(final CharSequence enc) {
        if (enc == null || enc.length() == 0) {
            throw new IllegalArgumentException("ZoneID must not be empty");
        }
        for (int i = 0; i < enc.length(); i++) {
            final char ch = enc.charAt(i);
            if (unreserved(ch)) {
                continue;
            }
            if (ch == '%' && i + 2 < enc.length()
                    && TextUtils.isHex(enc.charAt(i + 1)) && TextUtils.isHex(enc.charAt(i + 2))) {
                i += 2;
                continue;
            }
            throw new IllegalArgumentException("Illegal character in ZoneID");
        }
    }

    /**
     * Heuristic: returns {@code true} if {@code host} looks like an IPv6 address-part
     * (i.e., before any ZoneID) by counting colons. We do not parse/validate IPv6;
     * this keeps our surface minimal while still bracketing correctly.
     * <p>Rule: if the address-part (up to '%', if present) contains &gt;= 2 colons,
     * treat it as IPv6-like.</p>
     */
    public static boolean looksLikeIPv6AddressPart(final CharSequence host) {
        if (host == null) {
            return false;
        }
        int end = host.length();
        for (int i = 0; i < end; i++) {
            if (host.charAt(i) == '%') {
                end = i;
                break;
            }
        }
        int colons = 0;
        for (int i = 0; i < end; i++) {
            if (host.charAt(i) == ':') {
                colons++;
                if (colons >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Appends a bracketed IPv6 literal to {@code buf} if {@code host} looks like IPv6.
     * If a ZoneID is present (after '%'), it is written as {@code "%25"} followed by the
     * RFC 6874-encoded ZoneID. Returns {@code true} iff it wrote the bracketed literal.
     */
    public static boolean appendBracketedIPv6(final StringBuilder buf, final CharSequence host) {
        if (!looksLikeIPv6AddressPart(host)) {
            return false;
        }
        // address part
        int zoneIdx = -1;
        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) == '%') {
                zoneIdx = i;
                break;
            }
        }
        buf.append('[');
        if (zoneIdx >= 0) {
            buf.append(host, 0, zoneIdx);
        } else {
            buf.append(host);
        }
        // zone part
        if (zoneIdx >= 0) {
            final CharSequence zone = host.subSequence(zoneIdx + 1, host.length());
            buf.append("%25").append(encodeZoneIdRfc6874(zone));
        }
        buf.append(']');
        return true;
    }

    /**
     * RFC 3986 unreserved characters.
     */
    private static boolean unreserved(final char ch) {
        return ch >= 'A' && ch <= 'Z'
                || ch >= 'a' && ch <= 'z'
                || ch >= '0' && ch <= '9'
                || ch == '-' || ch == '.' || ch == '_' || ch == '~';
    }
}
