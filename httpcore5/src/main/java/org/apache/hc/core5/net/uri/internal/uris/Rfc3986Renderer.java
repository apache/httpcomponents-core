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

/**
 * Pre-sized StringBuilder renderer of URI components.
 *
 * @since 5.5
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class Rfc3986Renderer {
    private Rfc3986Renderer() {
    }

    static String build(final String scheme, final String userInfo, final String host, final int port,
                        final String path, final String query, final String fragment) {
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
}
