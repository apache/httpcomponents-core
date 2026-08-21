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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.uri.internal.uris.Rfc3986Equivalence;
import org.apache.hc.core5.net.uri.internal.uris.Rfc3986Normalizer;
import org.apache.hc.core5.net.uri.internal.uris.Rfc3986Parser;
import org.apache.hc.core5.net.uri.internal.uris.Rfc3986Resolver;

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

    final String original;

    final String scheme;    // lower-cased (ASCII) or null
    final String userInfo;  // raw or null
    final String host;      // reg-name lower-cased; IPv6 literal kept with brackets; or null
    final int port;         // -1 if missing
    final String path;      // raw, never null ("" allowed)
    final String query;     // raw (no '?') or null
    final String fragment;  // raw (no '#') or null

    public Rfc3986Uri(
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
        return Rfc3986Parser.parse(s);
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
        return Rfc3986Normalizer.normalizePath(this);
    }

    /**
     * RFC equivalence (case-insensitive scheme/host; decode %XX for unreserved; uppercase hex).
     */
    public boolean equivalentTo(final Rfc3986Uri other) {
        return Rfc3986Equivalence.equivalent(this, other);
    }

    /**
     * Resolve against a base (RFC 3986 §5.2).
     */
    public static Rfc3986Uri resolve(final Rfc3986Uri base, final Rfc3986Uri ref) {
        return Rfc3986Resolver.resolve(base, ref);
    }

    /**
     * Canonicalization used by URIBuilder#optimize().
     * <p>Performs:
     * <ul>
     *   <li>Lower-case of scheme and reg-name host (IPv6 literal preserved).</li>
     *   <li>Dot-segment removal if the path is absolute or an authority is present.</li>
     *   <li>Decoding of percent-escapes only for ASCII unreserved.</li>
     *   <li>Uppercasing of hex digits in remaining percent-escapes.</li>
     *   <li>Strict re-encoding of the path (preserve '/' and valid %HH; encode everything else via UTF-8).</li>
     *   <li>Query and fragment normalized by decode-unreserved + uppercase-hex.</li>
     * </ul>
     * The operation may change the textual form; consumers should treat this as a canonicalization step,
     * not as a guaranteed identity-preserving transformation.
     *
     * @since 5.4
     */
    public Rfc3986Uri optimize() {
        return Rfc3986Normalizer.optimize(this);
    }
}
