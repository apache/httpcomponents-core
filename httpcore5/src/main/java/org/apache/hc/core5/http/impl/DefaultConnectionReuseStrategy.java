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

package org.apache.hc.core5.http.impl;

import java.util.Iterator;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeaderIterator;
import org.apache.hc.core5.http.message.BasicTokenIterator;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Default implementation of a strategy deciding about connection re-use. The strategy
 * determines whether a connection is persistent or not based on the message’s protocol
 * version and {@code Connection} header field if present. Connections will not be
 * re-used and will close if any of these conditions is met
 * <ul>
 *     <li>the {@code close} connection option is present in the request message</li>
 *     <li>the response message content body is incorrectly or ambiguously delineated</li>
 *     <li>the {@code close} connection option is present in the response message</li>
 *     <li>If the received protocol is {@code HTTP/1.0} (or earlier) and {@code keep-alive}
 *     connection option is not present</li>
 * </ul>
 * In the absence of a {@code Connection} header field, the non-standard but commonly used
 * {@code Proxy-Connection} header field will be used instead. If no connection options are
 * explicitly given the default policy for the HTTP version is applied. {@code HTTP/1.1}
 * (or later) connections are re-used by default. {@code HTTP/1.0} (or earlier) connections
 * are not re-used by default.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DefaultConnectionReuseStrategy implements ConnectionReuseStrategy {

    public static final DefaultConnectionReuseStrategy INSTANCE = new DefaultConnectionReuseStrategy();

    public DefaultConnectionReuseStrategy() {
        super();
    }

    // see interface ConnectionReuseStrategy
    @Override
    public boolean keepAlive(
            final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context) {
        Args.notNull(response, "HTTP response");

        if (request != null) {
            final Header[] connHeaders = request.getHeaders(HttpHeaders.CONNECTION);
            if (connHeaders.length != 0) {
                final Iterator<String> ti = new BasicTokenIterator(new BasicHeaderIterator(connHeaders, null));
                while (ti.hasNext()) {
                    final String token = ti.next();
                    if (HeaderElements.CLOSE.equalsIgnoreCase(token)) {
                        return false;
                    }
                }
            }
        }

        // Check for a self-terminating entity. If the end of the entity will
        // be indicated by closing the connection, there is no keep-alive.
        final Header teh = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        if (teh != null) {
            if (!HeaderElements.CHUNKED_ENCODING.equalsIgnoreCase(teh.getValue())) {
                return false;
            }
        } else {
            if (canResponseHaveBody(response) && response.containsHeaders(HttpHeaders.CONTENT_LENGTH) != 1) {
                return false;
            }
        }

        // Check for the "Connection" header. If that is absent, check for
        // the "Proxy-Connection" header. The latter is an unspecified and
        // broken but unfortunately common extension of HTTP.
        Header[] connHeaders = response.getHeaders(HttpHeaders.CONNECTION);
        if (connHeaders.length == 0) {
            connHeaders = response.getHeaders("Proxy-Connection");
        }

        final ProtocolVersion ver = context.getProtocolVersion();
        if (connHeaders.length != 0) {
            if (ver.greaterEquals(HttpVersion.HTTP_1_1)) {
                final Iterator<String> it = new BasicTokenIterator(new BasicHeaderIterator(connHeaders, null));
                while (it.hasNext()) {
                    final String token = it.next();
                    if (HeaderElements.CLOSE.equalsIgnoreCase(token)) {
                        return false;
                    }
                }
                return true;
            }
            final Iterator<String> it = new BasicTokenIterator(new BasicHeaderIterator(connHeaders, null));
            while (it.hasNext()) {
                final String token = it.next();
                if (HeaderElements.KEEP_ALIVE.equalsIgnoreCase(token)) {
                    return true;
                }
            }
            return false;
        }
        return ver.greaterEquals(HttpVersion.HTTP_1_1);
    }

    private boolean canResponseHaveBody(final ClassicHttpResponse response) {
        final int status = response.getCode();
        return status >= HttpStatus.SC_SUCCESS
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

}