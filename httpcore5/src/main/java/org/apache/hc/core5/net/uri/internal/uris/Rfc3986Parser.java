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
 * @since 5.4
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
            if (hostStart >= authEnd) {
                throw new IllegalArgumentException("Empty host in authority");
            }

            if (buf[hostStart] == '[') {
                final int rb = indexOf(buf, ']', hostStart + 1, authEnd);
                if (rb < 0) {
                    throw new IllegalArgumentException("Unclosed IPv6 literal");
                }
                host = s.substring(hostStart, rb + 1); // keep literal verbatim
                if (rb + 1 < authEnd && buf[rb + 1] == ':') {
                    port = Ports.parsePort(buf, rb + 2, authEnd);
                }
            } else {
                final int colon = lastIndexOf(buf, ':', hostStart, authEnd);
                if (colon >= 0) {
                    host = Ascii.lowerAscii(s.substring(hostStart, colon));
                    port = Ports.parsePort(buf, colon + 1, authEnd);
                } else {
                    host = Ascii.lowerAscii(s.substring(hostStart, authEnd));
                }
            }
            cur.updatePos(authEnd);
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

        return new Rfc3986Uri(s, scheme, userInfo, host, port, path, query, fragment);
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
        for (int i = from; i < toExcl; i++) {
            if (a[i] == ch) {
                return i;
            }
        }
        return -1;
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
