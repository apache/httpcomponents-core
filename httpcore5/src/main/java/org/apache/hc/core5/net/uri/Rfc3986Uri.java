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

import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Immutable, RFC 3986-compliant URI value object.
 * <ul>
 *   <li>Parsing preserves raw text (including percent-encodings).</li>
 *   <li>Resolution &amp; dot-segment removal per RFC 3986 §5.2.</li>
 *   <li>Scheme and reg-name host are stored in lower case.</li>
 *   <li>No regex, no {@code Character} classes – pure ASCII tables.</li>
 * </ul>
 *
 * <p><strong>Round-trip:</strong> {@link #toRawString()} returns the exact input.
 * {@link #toString()} renders the canonical form held by this object.</p>
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class Rfc3986Uri implements UriReference {

    private final String original;

    private final String scheme;    // lower-cased (ASCII) or null
    private final String userInfo;  // raw or null
    private final String host;      // reg-name lower-cased; IPv6 literal kept with brackets; or null
    private final int port;      // -1 if missing
    private final String path;      // raw, never null ("" allowed)
    private final String query;     // raw (no '?') or null
    private final String fragment;  // raw (no '#') or null

    private Rfc3986Uri(
            final String original,
            final String scheme,
            final String userInfo,
            final String host,
            final int port,
            final String path,
            final String query,
            final String fragment) {
        this.original = original;
        this.scheme = scheme;
        this.userInfo = userInfo;
        this.host = host;
        this.port = port;
        this.path = path;
        this.query = query;
        this.fragment = fragment;
    }

    /**
     * Parse a URI reference per RFC 3986.
     */
    public static Rfc3986Uri parse(final String s) {
        Objects.requireNonNull(s, "URI must not be null");
        final char[] buf = s.toCharArray();
        final UriTokenizer t = new UriTokenizer(buf);

        String scheme = null, userInfo = null, host = null, path = "", query = null, fragment = null;
        int port = -1;

        // scheme
        final int schemeEnd = t.scanScheme();
        if (schemeEnd >= 0 && schemeEnd < buf.length && buf[schemeEnd] == ':') {
            scheme = Ascii.lowerAscii(s.substring(0, schemeEnd));
            t.pos = schemeEnd + 1; // skip ':'
        } else {
            t.pos = 0; // no scheme
        }

        // authority
        if (t.hasRemaining() && t.current() == '/' && t.peekAhead(1) == '/') {
            t.pos += 2; // skip "//"
            final int authStart = t.pos;
            final int authEnd = t.scanUntil("/?#");
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
                host = s.substring(hostStart, rb + 1); // keep literal as-is (case & brackets)
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
            t.pos = authEnd;
        }

        // path
        final int pathStart = t.pos;
        final int pathEnd = t.scanUntil("?#");
        path = s.substring(pathStart, pathEnd);
        t.pos = pathEnd;

        // query
        if (t.hasRemaining() && t.current() == '?') {
            t.pos++;
            final int qEnd = t.scanUntil("#");
            query = s.substring(pathEnd + 1, qEnd);
            t.pos = qEnd;
        }

        // fragment
        if (t.hasRemaining() && t.current() == '#') {
            t.pos++;
            fragment = s.substring(t.pos);
            t.pos = buf.length;
        }

        return new Rfc3986Uri(s, scheme, userInfo, host, port, path, query, fragment);
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public String getUserInfo() {
        return userInfo;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public String getFragment() {
        return fragment;
    }

    @Override
    public String toRawString() {
        return original;
    }

    @Override
    public String toString() {
        // Render canonical internal state (not the raw input).
        int cap = 0;
        if (scheme != null) {
            cap += scheme.length() + 1;
        }
        if (host != null) {
            cap += 2 + host.length();
            if (userInfo != null) {
                cap += userInfo.length() + 1;
            }
            if (port >= 0) {
                cap += 6;
            }
        }
        if (path != null) {
            cap += path.length();
        }
        if (query != null) {
            cap += 1 + query.length();
        }
        if (fragment != null) {
            cap += 1 + fragment.length();
        }

        final StringBuilder sb = new StringBuilder(Math.max(16, cap));
        if (scheme != null) {
            sb.append(scheme).append(':');
        }
        if (host != null) {
            sb.append("//");
            if (userInfo != null) {
                sb.append(userInfo).append('@');
            }
            sb.append(host);
            if (port >= 0) {
                sb.append(':').append(port);
            }
        }
        if (path != null) {
            sb.append(path);
        }
        if (query != null) {
            sb.append('?').append(query);
        }
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    /**
     * Dot-segment removal (RFC 3986 §5.2.4).
     */
    public Rfc3986Uri normalizePath() {
        final String normalized = DotSegments.remove(path);
        if (Objects.equals(normalized, path)) {
            return this;
        }
        return new Rfc3986Uri(
                rebuild(scheme, userInfo, host, port, normalized, query, fragment),
                scheme, userInfo, host, port, normalized, query, fragment);
    }

    /**
     * RFC equivalence (case-insensitive scheme/host; decode %XX for unreserved; uppercase hex).
     */
    public boolean equivalentTo(final Rfc3986Uri other) {
        if (other == null) {
            return false;
        }
        if (!Objects.equals(Ascii.lowerAscii(scheme), Ascii.lowerAscii(other.scheme))) {
            return false;
        }
        if (!Objects.equals(hostLower(host), hostLower(other.host))) {
            return false;
        }
        if (port != other.port) {
            return false;
        }
        if (!Objects.equals(normPctForEquivalence(path), normPctForEquivalence(other.path))) {
            return false;
        }
        if (!Objects.equals(normPctForEquivalence(query), normPctForEquivalence(other.query))) {
            return false;
        }
        if (!Objects.equals(normPctForEquivalence(fragment), normPctForEquivalence(other.fragment))) {
            return false;
        }
        return Objects.equals(userInfo, other.userInfo);
    }

    private static String normPctForEquivalence(final String s) {
        if (s == null) {
            return null;
        }
        final String step1 = PercentCodec.decode(s, /*decodeUnreservedOnly=*/true);
        return PercentCodec.uppercaseHexInPercents(step1);
    }

    /**
     * Resolve against a base (RFC 3986 §5.2).
     */
    public static Rfc3986Uri resolve(final Rfc3986Uri base, final Rfc3986Uri ref) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(ref, "ref");

        if (ref.scheme != null) {
            final String p = DotSegments.remove(ref.path);
            return new Rfc3986Uri(ref.toString(), ref.scheme, ref.userInfo, ref.host, ref.port, p, ref.query, ref.fragment);
        }
        if (ref.host != null) {
            final String p = DotSegments.remove(ref.path);
            return new Rfc3986Uri(ref.toString(), base.scheme, ref.userInfo, ref.host, ref.port, p, ref.query, ref.fragment);
        }

        final String mergedPath;
        if (ref.path == null || ref.path.isEmpty()) {
            mergedPath = base.path;
        } else if (ref.path.startsWith("/")) {
            mergedPath = DotSegments.remove(ref.path);
        } else {
            mergedPath = DotSegments.remove(mergePaths(base, ref.path));
        }

        final String q = ref.path == null || ref.path.isEmpty()
                ? ref.query != null ? ref.query : base.query
                : ref.query;

        return new Rfc3986Uri(base.toString(), base.scheme, base.userInfo, base.host, base.port, mergedPath, q, ref.fragment);
    }

    private static String mergePaths(final Rfc3986Uri base, final String relPath) {
        if (base.host != null && (base.path == null || base.path.isEmpty())) {
            return "/" + relPath;
        }
        final int slash = base.path.lastIndexOf('/');
        if (slash >= 0) {
            return base.path.substring(0, slash + 1) + relPath;
        }
        return relPath;
    }

    private static String hostLower(final String h) {
        if (h == null) {
            return null;
        }
        if (!h.isEmpty() && h.charAt(0) == '[') {
            return h; // IPv6 literal: keep brackets & case
        }
        return Ascii.lowerAscii(h);
    }

    private static String rebuild(
            final String scheme,
            final String userInfo,
            final String host,
            final int port,
            final String path,
            final String query,
            final String fragment) {
        final StringBuilder sb = new StringBuilder();
        if (scheme != null) {
            sb.append(scheme).append(':');
        }
        if (host != null) {
            sb.append("//");
            if (userInfo != null) {
                sb.append(userInfo).append('@');
            }
            sb.append(host);
            if (port >= 0) {
                sb.append(':').append(port);
            }
        }
        if (path != null) {
            sb.append(path);
        }
        if (query != null) {
            sb.append('?').append(query);
        }
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    // Tight local helpers (branch-light)
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
