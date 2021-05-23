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
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultConnectionReuseStrategy {

    /** HTTP context. */
    private HttpContext context;

    /** The reuse strategy to be tested. */
    private ConnectionReuseStrategy reuseStrategy;

    @Before
    public void setUp() {
        reuseStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        context = new BasicHttpContext(null);
    }

    @Test
    public void testInvalidResponseArg() throws Exception {
        Assert.assertThrows(NullPointerException.class, () ->
                reuseStrategy.keepAlive(null, null, this.context));
    }

    @Test
    public void testNoContentLengthResponseHttp1_0() throws Exception {
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testNoContentLengthResponseHttp1_1() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testChunkedContent() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testIgnoreInvalidKeepAlive() throws Exception {
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Connection", "keep-alive");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testExplicitClose() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "close");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testExplicitKeepAlive() throws Exception {
        // Use HTTP 1.0
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep-alive");

        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testHTTP10Default() throws Exception {
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testHTTP11Default() throws Exception {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testBrokenConnectionDirective1() throws Exception {
        // Use HTTP 1.0
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep--alive");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testBrokenConnectionDirective2() throws Exception {
        // Use HTTP 1.0
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", null);
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testConnectionTokens1() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, cLOSe, dumdy");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testConnectionTokens2() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, kEEP-alive, dumdy");
        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testConnectionTokens3() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, keep-alive, close, dumdy");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testConnectionTokens4() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, close, dumdy");
        response.addHeader("Proxy-Connection", "keep-alive");
        // Connection takes precedence over Proxy-Connection
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testConnectionTokens5() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, dumdy");
        response.addHeader("Proxy-Connection", "close");
        // Connection takes precedence over Proxy-Connection,
        // even if it doesn't contain a recognized token.
        // Default for HTTP/1.1 is to keep alive.
        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testConnectionTokens6() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "");
        response.addHeader("Proxy-Connection", "close");
        // Connection takes precedence over Proxy-Connection,
        // even if it is empty. Default for HTTP/1.1 is to keep alive.
        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testMultipleContentLength() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Content-Length", "11");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testNoContentResponse() throws Exception {
        // Use HTTP 1.1
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testNoContentResponseHttp10() throws Exception {
        // Use HTTP 1.0
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testRequestExplicitClose() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "close");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assert.assertFalse(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    public void testRequestNoExplicitClose() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "blah, blah, blah");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assert.assertTrue(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    public void testRequestExplicitCloseMultipleTokens() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "blah, blah, blah");
        request.addHeader("Connection", "keep-alive");
        request.addHeader("Connection", "close");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assert.assertFalse(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    public void testRequestClose() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        request.addHeader("Connection", "close");

        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep-alive");

        Assert.assertFalse(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    public void testHeadRequestWithout() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.HEAD, "/");
        final HttpResponse response = new BasicHttpResponse(200, "OK");

        Assert.assertTrue(reuseStrategy.keepAlive(request, response, context));
    }

    @Test
    public void testHttp204ContentLengthGreaterThanZero() throws Exception {
        final HttpResponse response = new BasicHttpResponse(204, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep-alive");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testHttp204ContentLengthEqualToZero() throws Exception {
        final HttpResponse response = new BasicHttpResponse(204, "OK");
        response.addHeader("Content-Length", "0");
        response.addHeader("Connection", "keep-alive");
        Assert.assertTrue(reuseStrategy.keepAlive(null, response, context));
    }

    @Test
    public void testHttp204ChunkedContent() throws Exception {
        final HttpResponse response = new BasicHttpResponse(204, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive");
        Assert.assertFalse(reuseStrategy.keepAlive(null, response, context));
    }
}

