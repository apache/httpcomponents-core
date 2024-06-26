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

import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestDefaultConnectionReuseStrategy {

    /** HTTP context. */
    private HttpCoreContext context;

    /** The reuse strategy to be tested. */
    private ConnectionReuseStrategy reuseStrategy;

    @BeforeEach
    void setUp() {
        reuseStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        context = HttpCoreContext.create();
    }

    @Test
    void testInvalidResponseArg() {
        Assertions.assertThrows(NullPointerException.class, () ->
                reuseStrategy.keepAlive(null, null, this.context));
    }

    @Test
    void testNoContentLengthResponseHttp1_0() {
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testNoContentLengthResponseHttp1_1() {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testChunkedContent() {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testIgnoreInvalidKeepAlive() {
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testExplicitClose() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "close");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testExplicitKeepAlive() {
        // Use HTTP 1.0
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep-alive");

        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testHTTP10Default() {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.addHeader("Content-Length", "10");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testHTTP11Default() {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testBrokenConnectionDirective1() {
        // Use HTTP 1.0
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep--alive");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testBrokenConnectionDirective2() {
        // Use HTTP 1.0
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", null);
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testConnectionTokens1() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, cLOSe, dumdy");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testConnectionTokens2() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, kEEP-alive, dumdy");
        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testConnectionTokens3() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, keep-alive, close, dumdy");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testConnectionTokens4() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, close, dumdy");
        response.addHeader("Proxy-Connection", "keep-alive");
        // Connection takes precedence over Proxy-Connection
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testConnectionTokens5() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, dumdy");
        response.addHeader("Proxy-Connection", "close");
        // Connection takes precedence over Proxy-Connection,
        // even if it doesn't contain a recognized token.
        // Default for HTTP/1.1 is to keep alive.
        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testConnectionTokens6() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "");
        response.addHeader("Proxy-Connection", "close");
        // Connection takes precedence over Proxy-Connection,
        // even if it is empty. Default for HTTP/1.1 is to keep alive.
        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testMultipleContentLength() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Content-Length", "11");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testNoContentResponse() {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testNoContentResponseHttp10() {
        // Use HTTP 1.0
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        response.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testRequestExplicitClose() {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "close");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertFalse(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    void testRequestNoExplicitClose() {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "blah, blah, blah");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertTrue(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    void testRequestExplicitCloseMultipleTokens() {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "blah, blah, blah");
        request.addHeader("Connection", "keep-alive");
        request.addHeader("Connection", "close");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertFalse(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    void testRequestClose() {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "close");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep-alive");

        Assertions.assertFalse(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    void testHeadRequestWithout() {
        final HttpRequest request = new BasicHttpRequest(Method.HEAD, "/");
        final HttpResponse response = new BasicHttpResponse(200, "OK");

        Assertions.assertTrue(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    void testHttp204ContentLengthGreaterThanZero() {
        final HttpResponse response = new BasicHttpResponse(204, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testHttp204ContentLengthEqualToZero() {
        final HttpResponse response = new BasicHttpResponse(204, "OK");
        response.addHeader("Content-Length", "0");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testHttp204ChunkedContent() {
        final HttpResponse response = new BasicHttpResponse(204, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    void testResponseHTTP10TransferEncodingPresent() {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setVersion(HttpVersion.HTTP_1_0);
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assertions.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

}

