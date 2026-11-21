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

package org.apache.hc.core5.testing.classic;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.WWWFormCodec;

/**
 * A handler that generates random data.
 */
public class RandomHandler implements HttpRequestHandler {

    /**
     * Handles a request by generating random data.
     * The length of the response can be specified in the request URI
     * as a number after the last /. For example /random/whatever/20
     * will generate 20 random bytes in the printable ASCII range.
     * If the request URI ends with /, a random number of random bytes
     * is generated, but at least one.
     *
     * @param request   the request
     * @param response  the response
     * @param context   the context
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    @Override
    public void handle(final ClassicHttpRequest request,
                       final ClassicHttpResponse response,
                       final HttpContext context)
        throws HttpException, IOException {

        final String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) &&
                !"HEAD".equalsIgnoreCase(method) &&
                !"POST".equalsIgnoreCase(method) &&
                !"PUT".equalsIgnoreCase(method)) {
            throw new MethodNotSupportedException(method + " not supported by " + getClass().getName());
        }
        final URI uri;
        try {
            uri = request.getUri();
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }
        final String query = uri.getQuery();
        int delayMs = 0;
        boolean drip = false;
        if (query != null) {
            final List<NameValuePair> params = WWWFormCodec.parse(query, UTF_8);
            for (final NameValuePair param : params) {
                final String name = param.getName();
                final String value = param.getValue();
                if ("delay".equals(name)) {
                    delayMs = Integer.parseInt(value);
                } else if ("drip".equals(name)) {
                    drip = "1".equals(value);
                }
            }
        }

        final String path = uri.getPath();
        final int slash = path.lastIndexOf('/');
        if (slash != -1) {
            final String payload = path.substring(slash + 1);
            final long n;
            if (!payload.isEmpty()) {
                try {
                    n = Long.parseLong(payload);
                } catch (final NumberFormatException ex) {
                    throw new ProtocolException("Invalid request path: " + path);
                }
            } else {
                // random length, but make sure at least something is sent
                n = 1 + (int)(Math.random() * 79.0);
            }
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new RandomEntity(n, delayMs, drip));
        } else {
            throw new ProtocolException("Invalid request path: " + path);
        }
    }

}
