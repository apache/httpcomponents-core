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

package org.apache.hc.core5.http.protocol;

import java.io.IOException;

import org.apache.hc.core5.annotation.Immutable;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * RequestTargetHost is responsible for copying {@code Host} header value to
 * {@link HttpRequest#setHost(HttpHost)} of the incoming message.
 * This interceptor is required for server side protocol processors.
 *
 * @since 5.0
 */
@Immutable
public class RequestValidateHost implements HttpRequestInterceptor {

    public RequestValidateHost() {
        super();
    }

    @Override
    public void process(final HttpRequest request, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final ProtocolVersion version = request.getVersion() != null ? request.getVersion() : HttpVersion.HTTP_1_1;
        if (version.greaterEquals(HttpVersion.HTTP_1_1)) {
            final int n = request.containsHeaders(HttpHeaders.HOST);
            if (n == 0) {
                throw new ProtocolException("Host header is absent");
            } else if (n > 1) {
                throw new ProtocolException("Multiple Host headers found");
            }
        }
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        if (header != null) {
            String text = header.getValue();
            if (TextUtils.isBlank(text)) {
                throw new ProtocolException("Empty host header");
            }
            int port = -1;
            final int portIdx = text.lastIndexOf(":");
            if (portIdx > 0) {
                try {
                    port = Integer.parseInt(text.substring(portIdx + 1));
                } catch (final NumberFormatException ex) {
                    throw new ProtocolException("Invalid HTTP host: " + text);
                }
                text = text.substring(0, portIdx);
            }
            request.setHost(new HttpHost(text, port, null));
        }
    }

}
