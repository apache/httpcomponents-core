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

package org.apache.hc.core5.testing.framework;

import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.BODY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.CONTENT_TYPE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.HEADERS;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.METHOD;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PROTOCOL_VERSION;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.QUERY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestTestingFrameworkRequestHandler {
    @Test
    void assertNothingThrown() throws Exception {
        final TestingFrameworkRequestHandler handler = new TestingFrameworkRequestHandler() {

            @Override
            public void handle(final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context)
                throws HttpException, IOException {
            }
        };

        handler.assertNothingThrown();
    }

    @Test
    void assertNothingThrownThrows() throws Exception {
        final String errorMessage = "thrown intentionally";

        final TestingFrameworkRequestHandler handler = new TestingFrameworkRequestHandler() {

            @Override
            public void handle(final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context)
                    throws HttpException, IOException {
                thrown = new TestingFrameworkException(errorMessage);
            }
        };

        handler.handle(null, null, null);
        final TestingFrameworkException exception = Assertions.assertThrows(TestingFrameworkException.class,
                () -> handler.assertNothingThrown());
        Assertions.assertEquals(errorMessage, exception.getMessage(), "Unexpected message");
        // a second call should not throw
        handler.assertNothingThrown();
    }

    @Test
    void handleValidatesRequestAndBuildsResponse() throws Exception {
        final TestingFrameworkRequestHandler handler = new TestingFrameworkRequestHandler();

        final Map<String, Object> requestExpectations = new HashMap<>();
        requestExpectations.put(METHOD, Method.POST.name());
        requestExpectations.put(BODY, "ping");
        requestExpectations.put(CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
        requestExpectations.put(PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        final Map<String, String> query = new HashMap<>();
        query.put("q", "1");
        query.put("r", "2");
        requestExpectations.put(QUERY, query);
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "value");
        requestExpectations.put(HEADERS, headers);
        handler.setRequestExpectations(requestExpectations);

        final Map<String, Object> desiredResponse = new HashMap<>();
        desiredResponse.put(STATUS, 201);
        desiredResponse.put(BODY, "pong");
        desiredResponse.put(CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
        final Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("X-Reply", "ok");
        desiredResponse.put(HEADERS, responseHeaders);
        handler.setDesiredResponse(desiredResponse);

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/echo?q=1&r=2");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.addHeader("X-Test", "value");
        request.setEntity(new StringEntity("ping", ContentType.TEXT_PLAIN));
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200);

        handler.handle(request, response, HttpCoreContext.create());
        handler.assertNothingThrown();

        Assertions.assertEquals(201, response.getCode());
        Assertions.assertEquals("pong", EntityUtils.toString(response.getEntity()));
        Assertions.assertTrue(response.getEntity().getContentType().contains("text/plain"));
        Assertions.assertEquals("ok", response.getFirstHeader("X-Reply").getValue());
    }

    @Test
    void handleStoresThrowableOnMismatch() throws Exception {
        final TestingFrameworkRequestHandler handler = new TestingFrameworkRequestHandler();

        final Map<String, Object> requestExpectations = new HashMap<>();
        requestExpectations.put(METHOD, Method.GET.name());
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-Needed", "true");
        requestExpectations.put(HEADERS, headers);
        handler.setRequestExpectations(requestExpectations);

        final Map<String, Object> desiredResponse = new HashMap<>();
        desiredResponse.put(STATUS, 200);
        handler.setDesiredResponse(desiredResponse);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200);

        handler.handle(request, response, HttpCoreContext.create());
        Assertions.assertThrows(TestingFrameworkException.class, handler::assertNothingThrown);
    }

}
