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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * HttpServerExpectationFilter add support for the Expect-Continue handshake
 * to the request processing pipeline.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class HttpServerExpectationFilter implements HttpFilterHandler {

    /**
     * Verifies the HTTP request and decides whether it meets server expectations and the request
     * processing can continue.
     *
     * @param request the incoming HTTP request.
     * @param context the actual execution context.
     * @return {@code true} if the request meets expectations or {@code false} otherwise.
     */
    protected boolean verify(final ClassicHttpRequest request, final HttpContext context) throws HttpException {
        return true;
    }

    /**
     * Generates response content entity for the final HTTP response with an error status
     * representing the cause of expectation failure.
     *
     * @param expectationFailed the final HTTP response.
     * @return the content entity for the final HTTP response with an error status
     *  representing the cause of expectation failure.
     */
    protected HttpEntity generateResponseContent(final HttpResponse expectationFailed) throws HttpException {
        return null;
    }

    @Override
    public final void handle(
            final ClassicHttpRequest request,
            final HttpFilterChain.ResponseTrigger responseTrigger,
            final HttpContext context,
            final HttpFilterChain chain) throws HttpException, IOException {
        final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
        final boolean expectContinue = expect != null && HeaderElements.CONTINUE.equalsIgnoreCase(expect.getValue());
        if (expectContinue) {
            final boolean verified = verify(request, context);
            if (verified) {
                responseTrigger.sendInformation(new BasicClassicHttpResponse(HttpStatus.SC_CONTINUE));
            } else {
                final ClassicHttpResponse expectationFailed = new BasicClassicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED);
                final HttpEntity responseContent = generateResponseContent(expectationFailed);
                expectationFailed.setEntity(responseContent);
                responseTrigger.submitResponse(expectationFailed);
                return;
            }
        }
        chain.proceed(request, responseTrigger, context);
    }
}
