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
import java.net.InetSocketAddress;

import org.apache.hc.core5.annotation.Immutable;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;

/**
 * RequestTargetHost is responsible for adding {@code Host} header. This
 * interceptor is required for client side protocol processors.
 *
 * @since 4.0
 */
@Immutable
public class RequestTargetHost implements HttpRequestInterceptor {

    public RequestTargetHost() {
        super();
    }

    @Override
    public void process(final HttpRequest request, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final HttpCoreContext coreContext = HttpCoreContext.adapt(context);

        final ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
        final String method = request.getRequestLine().getMethod();
        if (method.equalsIgnoreCase("CONNECT") && ver.lessEquals(HttpVersion.HTTP_1_0)) {
            extracted();
            return;
        }

        if (!request.containsHeader(HttpHeaders.HOST)) {
            HttpHost targethost = coreContext.getTargetHost();
            if (targethost == null) {
                // Populate the context with a default HTTP host based on the
                // inet address of the target host
                final HttpConnection conn = coreContext.getConnection();
                if (conn != null) {
                    final InetSocketAddress remoteAddress = (InetSocketAddress) conn.getRemoteAddress();
                    if (remoteAddress != null) {
                        targethost = new HttpHost(remoteAddress.getHostName(), remoteAddress.getPort());
                    }
                }
                if (targethost == null) {
                    if (ver.lessEquals(HttpVersion.HTTP_1_0)) {
                        extracted();
                        return;
                    }
                    throw new ProtocolException("Target host missing");
                }
            }
            request.addHeader(HttpHeaders.HOST, targethost.toHostString());
        }
    }

    private void extracted() {
        return;
    }

}
