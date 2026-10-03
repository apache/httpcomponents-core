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
import org.apache.hc.core5.net.uri.internal.paths.DotSegments;
import org.apache.hc.core5.util.Args;

/**
 * Resolver per RFC 3986 §5.2.
 *
 * @since 5.5
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class Rfc3986Resolver {

    private Rfc3986Resolver() {
    }

    public static Rfc3986Uri resolve(final Rfc3986Uri base, final Rfc3986Uri ref) {
        Args.notNull(base, "base");
        Args.notNull(ref, "ref");

        if (ref.getScheme() != null) {
            final String p = DotSegments.remove(ref.getPath());
            final String raw = format(ref.getScheme(), ref.getUserInfo(), ref.getHost(), ref.getPort(), p, ref.getQuery(), ref.getFragment());
            return new Rfc3986Uri(raw, ref.getScheme(), ref.getUserInfo(), ref.getHost(), ref.getPort(), p, ref.getQuery(), ref.getFragment());
        }
        if (ref.getHost() != null) {
            final String p = DotSegments.remove(ref.getPath());
            final String raw = format(base.getScheme(), ref.getUserInfo(), ref.getHost(), ref.getPort(), p, ref.getQuery(), ref.getFragment());
            return new Rfc3986Uri(raw, base.getScheme(), ref.getUserInfo(), ref.getHost(), ref.getPort(), p, ref.getQuery(), ref.getFragment());
        }

        final String mergedPath;
        if (ref.getPath() == null || ref.getPath().isEmpty()) {
            mergedPath = base.getPath();
        } else if (ref.getPath().startsWith("/")) {
            mergedPath = DotSegments.remove(ref.getPath());
        } else {
            mergedPath = DotSegments.remove(mergePaths(base, ref.getPath()));
        }

        final String q = ref.getPath() == null || ref.getPath().isEmpty()
                ? ref.getQuery() != null ? ref.getQuery() : base.getQuery()
                : ref.getQuery();

        final String raw = format(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), mergedPath, q, ref.getFragment());
        return new Rfc3986Uri(raw, base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), mergedPath, q, ref.getFragment());
    }

    private static String mergePaths(final Rfc3986Uri base, final String relPath) {
        if (base.getHost() != null && (base.getPath() == null || base.getPath().isEmpty())) {
            return "/" + relPath;
        }
        final int slash = base.getPath().lastIndexOf('/');
        if (slash >= 0) {
            return base.getPath().substring(0, slash + 1) + relPath;
        }
        return relPath;
    }

    private static String format(
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
}
