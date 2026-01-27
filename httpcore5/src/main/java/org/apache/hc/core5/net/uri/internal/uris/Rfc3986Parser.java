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

package org.apache.hc.core5.net.uri.internal.uris;

import static org.apache.hc.core5.util.TextUtils.isHex;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.uri.Rfc3986Uri;
import org.apache.hc.core5.net.uri.internal.authorities.Ports;
import org.apache.hc.core5.net.uri.internal.utils.Ascii;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Parser for {@link Rfc3986Uri}.
 *
 * @since 5.5
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class Rfc3986Parser {

    private Rfc3986Parser() {
    }

    public static Rfc3986Uri parse(final String s) {
        Args.notNull(s, "URI must not be null");

        final char[] buf = s.toCharArray();
        final Tokenizer.Cursor cur = new Tokenizer.Cursor(0, buf.length);

        String scheme = null, userInfo = null, host = null, path = "", query = null, fragment = null;
        int port = -1;

        // ---- scheme ----  ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) ":"
        final int schemeEnd = scanScheme(buf, cur.getPos(), cur.getUpperBound());
        if (schemeEnd >= 0 && schemeEnd < buf.length && buf[schemeEnd] == ':') {
            scheme = Ascii.lowerAscii(s.substring(cur.getPos(), schemeEnd));
            cur.updatePos(schemeEnd + 1); // skip ':'
        } else {
            cur.updatePos(0); // no scheme
        }

        // ---- authority ----  "//" [userinfo "@"] host [ ":" port ]
        if (cur.getPos() + 1 < buf.length && buf[cur.getPos()] == '/' && buf[cur.getPos() + 1] == '/') {
            cur.updatePos(cur.getPos() + 2); // skip "//"
            final int authStart = cur.getPos();
            final int authEnd = scanUntil(buf, authStart, buf.length, '/', '?', '#');

            final int at = indexOf(buf, '@', authStart, authEnd);
            final int hostStart;
            if (at >= 0) {
                userInfo = s.substring(authStart, at);
                hostStart = at + 1;
            } else {
                hostStart = authStart;
            }

            // RFC 3986 allows empty host in authority (reg-name can be empty), e.g. file:///path
            if (hostStart >= authEnd) {
                host = "";
                cur.updatePos(authEnd);
            } else {
                if (buf[hostStart] == '[') {
                    final int rb = indexOf(buf, ']', hostStart + 1, authEnd);
                    if (rb < 0) {
                        throw new IllegalArgumentException("Unclosed IPv6 literal");
                    }
                    host = Ascii.lowerAscii(s.substring(hostStart, rb + 1)); // normalize consistently
                    if (rb + 1 < authEnd && buf[rb + 1] == ':') {
                        if (rb + 2 == authEnd) {
                            port = -1; // empty port is syntactically allowed
                        } else {
                            port = Ports.parsePort(buf, rb + 2, authEnd);
                        }
                    }
                } else {
                    final int colon = lastIndexOf(buf, ':', hostStart, authEnd);
                    if (colon >= 0) {
                        host = Ascii.lowerAscii(s.substring(hostStart, colon));
                        if (colon + 1 == authEnd) {
                            port = -1; // empty port is syntactically allowed
                        } else {
                            port = Ports.parsePort(buf, colon + 1, authEnd);
                        }
                    } else {
                        host = Ascii.lowerAscii(s.substring(hostStart, authEnd));
                    }
                }
                cur.updatePos(authEnd);
            }
        }

        // ---- path ----
        final int pathStart = cur.getPos();
        final int pathEnd = scanUntil(buf, pathStart, buf.length, '?', '#');
        path = s.substring(pathStart, pathEnd);
        cur.updatePos(pathEnd);

        // ---- query ----
        if (cur.getPos() < buf.length && buf[cur.getPos()] == '?') {
            final int qStart = cur.getPos() + 1;
            final int qEnd = scanUntil(buf, qStart, buf.length, '#');
            query = s.substring(qStart, qEnd);
            cur.updatePos(qEnd);
        }

        // ---- fragment ----
        if (cur.getPos() < buf.length && buf[cur.getPos()] == '#') {
            fragment = s.substring(cur.getPos() + 1);
            cur.updatePos(buf.length);
        }

        final Rfc3986Uri u = new Rfc3986Uri(s, scheme, userInfo, host, port, path, query, fragment);

        // strict-by-default validation
        validateScheme(u.getScheme());
        validateUserInfo(u.getUserInfo());
        validateHost(u.getHost());
        validatePath(u.getPath());
        validateQuery(u.getQuery());
        validateFragment(u.getFragment());

        return u;
    }

    private static void validateScheme(final String scheme) {
        if (scheme == null) {
            return;
        }
        for (int i = 0; i < scheme.length(); i++) {
            final char c = scheme.charAt(i);
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in scheme");
            }
        }
    }

    private static void validateUserInfo(final String userInfo) {
        if (userInfo == null) {
            return;
        }
        validatePctEncoding(userInfo, "userinfo");
        for (int i = 0; i < userInfo.length(); i++) {
            final char c = userInfo.charAt(i);
            if (c == '%') {
                i += 2;
                continue;
            }
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in userinfo");
            }
            if (!isUnreserved(c) && !isSubDelim(c) && c != ':') {
                throw new IllegalArgumentException("Illegal character in userinfo");
            }
        }
    }

    private static void validateHost(final String host) {
        if (host == null) {
            return;
        }
        if (host.isEmpty()) {
            return; // allowed (reg-name is *)
        }
        if (host.charAt(0) == '[') {
            if (host.charAt(host.length() - 1) != ']') {
                throw new IllegalArgumentException("Unclosed IP-literal");
            }
            final String inside = host.substring(1, host.length() - 1);
            validateIpLiteral(inside);
            return;
        }
        validatePctEncoding(host, "host");
        for (int i = 0; i < host.length(); i++) {
            final char c = host.charAt(i);
            if (c == '%') {
                i += 2;
                continue;
            }
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in host");
            }
            if (!isUnreserved(c) && !isSubDelim(c)) {
                throw new IllegalArgumentException("Illegal character in host");
            }
        }
    }

    private static void validatePath(final String path) {
        if (path == null) {
            return;
        }
        validatePctEncoding(path, "path");
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '%') {
                i += 2;
                continue;
            }
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in path");
            }
            if (c == '/') {
                continue;
            }
            if (!isUnreserved(c) && !isSubDelim(c) && c != ':' && c != '@') {
                throw new IllegalArgumentException("Illegal character in path");
            }
        }
    }

    private static void validateQuery(final String query) {
        if (query == null) {
            return;
        }
        validatePctEncoding(query, "query");
        for (int i = 0; i < query.length(); i++) {
            final char c = query.charAt(i);
            if (c == '%') {
                i += 2;
                continue;
            }
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in query");
            }
            if (c == '/' || c == '?') {
                continue;
            }
            if (!isUnreserved(c) && !isSubDelim(c) && c != ':' && c != '@') {
                throw new IllegalArgumentException("Illegal character in query");
            }
        }
    }

    private static void validateFragment(final String fragment) {
        if (fragment == null) {
            return;
        }
        validatePctEncoding(fragment, "fragment");
        for (int i = 0; i < fragment.length(); i++) {
            final char c = fragment.charAt(i);
            if (c == '%') {
                i += 2;
                continue;
            }
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in fragment");
            }
            if (c == '/' || c == '?') {
                continue;
            }
            if (!isUnreserved(c) && !isSubDelim(c) && c != ':' && c != '@') {
                throw new IllegalArgumentException("Illegal character in fragment");
            }
        }
    }

    private static void validatePctEncoding(final String s, final String component) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= s.length()) {
                    throw new IllegalArgumentException("Incomplete pct-encoding in " + component);
                }
                final char h1 = s.charAt(i + 1);
                final char h2 = s.charAt(i + 2);
                if (!isHex(h1) || !isHex(h2)) {
                    throw new IllegalArgumentException("Invalid pct-encoding in " + component);
                }
                i += 2;
            } else if (c <= 0x1F || c == 0x7F) {
                throw new IllegalArgumentException("Control character in " + component);
            }
        }
    }

    private static void validateIpLiteral(final String inside) {
        if (inside.isEmpty()) {
            throw new IllegalArgumentException("Empty IP-literal");
        }
        final char first = inside.charAt(0);
        if (first == 'v' || first == 'V') {
            validateIpvFuture(inside);
        } else {
            validateIpv6Address(inside);
        }
    }

    private static void validateIpvFuture(final String s) {
        int i = 1;
        if (i >= s.length()) {
            throw new IllegalArgumentException("Invalid IPvFuture");
        }
        int hexdigs = 0;
        while (i < s.length() && isHex(s.charAt(i))) {
            hexdigs++;
            i++;
        }
        if (hexdigs == 0 || i >= s.length() || s.charAt(i) != '.') {
            throw new IllegalArgumentException("Invalid IPvFuture");
        }
        i++;
        if (i >= s.length()) {
            throw new IllegalArgumentException("Invalid IPvFuture");
        }
        int tail = 0;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in IPvFuture");
            }
            if (!isUnreserved(c) && !isSubDelim(c) && c != ':') {
                throw new IllegalArgumentException("Illegal character in IPvFuture");
            }
            tail++;
            i++;
        }
        if (tail == 0) {
            throw new IllegalArgumentException("Invalid IPvFuture");
        }
    }

    private static void validateIpv6Address(final String s) {
        if (s.indexOf(":::") >= 0) {
            throw new IllegalArgumentException("Invalid IPv6 literal");
        }
        final int dbl = s.indexOf("::");
        if (dbl >= 0 && s.indexOf("::", dbl + 2) >= 0) {
            throw new IllegalArgumentException("Invalid IPv6 literal");
        }
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c > 0x7F) {
                throw new IllegalArgumentException("Non-ASCII character in IPv6 literal");
            }
            if (!(isHex(c) || c == ':' || c == '.')) {
                throw new IllegalArgumentException("Illegal character in IPv6 literal");
            }
        }
        // This is "strict enough" for RFC 3986 IP-literal validation without pulling in InetAddress.
        // It rejects obvious junk and enforces the '::' compression constraint.
    }

    private static boolean isUnreserved(final char c) {
        return Ascii.isAlpha(c) || Ascii.isDigit(c) || c == '-' || c == '.' || c == '_' || c == '~';
    }

    private static boolean isSubDelim(final char c) {
        switch (c) {
            case '!':
            case '$':
            case '&':
            case '\'':
            case '(':
            case ')':
            case '*':
            case '+':
            case ',':
            case ';':
            case '=':
                return true;
            default:
                return false;
        }
    }

    private static int scanScheme(final char[] a, final int from, final int toExcl) {
        int finalFrom = from;
        if (from >= toExcl) {
            return -1;
        }
        if (!Ascii.isAlpha(a[from])) {
            return -1;
        }
        finalFrom++;
        while (finalFrom < toExcl) {
            final char c = a[finalFrom];
            if (Ascii.isAlpha(c) || Ascii.isDigit(c) || c == '+' || c == '-' || c == '.') {
                finalFrom++;
            } else {
                break;
            }
        }
        return finalFrom; // caller checks for ':'
    }

    private static int scanUntil(final char[] a, final int from, final int toExcl, final char... stops) {
        outer:
        for (int i = from; i < toExcl; i++) {
            final char c = a[i];
            for (final char s : stops) {
                if (c == s) {
                    return i;
                }
            }
        }
        return toExcl;
    }

    private static int indexOf(final char[] a, final char ch, final int from, final int toExcl) {
        return Ascii.indexOf(a, ch, from, toExcl);
    }

    private static int lastIndexOf(final char[] a, final char ch, final int from, final int toExcl) {
        for (int i = toExcl - 1; i >= from; i--) {
            if (a[i] == ch) {
                return i;
            }
        }
        return -1;
    }
}
