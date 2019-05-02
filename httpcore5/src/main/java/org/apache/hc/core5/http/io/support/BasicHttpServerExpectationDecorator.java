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

package org.apache.hc.core5.http.io.support;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * {@link HttpServerRequestHandler} implementation that adds support
 * for the Expect-Continue handshake to an existing
 * {@link HttpServerRequestHandler}.
 *
 * @since 5.0
 */
public class BasicHttpServerExpectationDecorator implements HttpServerRequestHandler {

    private final HttpServerRequestHandler requestHandler;

    public BasicHttpServerExpectationDecorator(final HttpServerRequestHandler requestHandler) {
        this.requestHandler = Args.notNull(requestHandler, "Request handler");
    }

    /**
     * Verifies the HTTP request and decides whether it meets server expectations and the request
     * processing can continue.
     *
     * @param request the incoming HTTP request.
     * @param context the actual execution context.
     * @return {@code null} if the request meets expectations or a final HTTP response
     *  with an error status representing the cause of expectation failure.
     */
    protected ClassicHttpResponse verify(final ClassicHttpRequest request, final HttpContext context) {
        return null;
    }

    @Override
    public final void handle(
            final ClassicHttpRequest request,
            final ResponseTrigger responseTrigger,
            final HttpContext context) throws HttpException, IOException {
        final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
        if (expect != null && HeaderElements.CONTINUE.equalsIgnoreCase(expect.getValue())) {
            final ClassicHttpResponse response = verify(request, context);
            if (response == null) {
                responseTrigger.sendInformation(new BasicClassicHttpResponse(HttpStatus.SC_CONTINUE));
            } else {
                responseTrigger.submitResponse(response);
                return;
            }
        }
        requestHandler.handle(request, responseTrigger, context);
    }

}
