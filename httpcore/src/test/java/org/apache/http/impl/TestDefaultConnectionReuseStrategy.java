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

package org.apache.http.impl;

import junit.framework.TestCase;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class TestDefaultConnectionReuseStrategy extends TestCase {

    /** HTTP context. */
    private HttpContext context;

    /** The reuse strategy to be tested. */
    private ConnectionReuseStrategy reuseStrategy;



    public TestDefaultConnectionReuseStrategy(String testName) {
        super(testName);
    }

    public void setUp() {
        reuseStrategy = new DefaultConnectionReuseStrategy();
        context = new BasicHttpContext(null);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testIllegalResponseArg() throws Exception {

        HttpContext context = new BasicHttpContext(null);

        try {
            reuseStrategy.keepAlive(null, context);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testIllegalContextArg() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        try {
            reuseStrategy.keepAlive(response, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }

    }

    public void testNoContentLengthResponseHttp1_0() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testNoContentLengthResponseHttp1_1() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testChunkedContent() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        assertTrue(reuseStrategy.keepAlive(response, context));
    }

    public void testIgnoreInvalidKeepAlive() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Connection", "keep-alive");

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testExplicitClose() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "close");

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testExplicitKeepAlive() throws Exception {
        // Use HTTP 1.0
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep-alive");

        assertTrue(reuseStrategy.keepAlive(response, context));
    }

    public void testHTTP10Default() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Content-Length", "10");
        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testHTTP11Default() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Content-Length", "10");
        assertTrue(reuseStrategy.keepAlive(response, context));
    }

    public void testFutureHTTP() throws Exception {
        HttpResponse response = new BasicHttpResponse(new HttpVersion(3, 45), 200, "OK");
        response.addHeader("Content-Length", "10");

        assertTrue(reuseStrategy.keepAlive(response, context));
    }

    public void testBrokenConnectionDirective1() throws Exception {
        // Use HTTP 1.0
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", "keep--alive");

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testBrokenConnectionDirective2() throws Exception {
        // Use HTTP 1.0
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Connection", null);

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testConnectionTokens1() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, cLOSe, dumdy");

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testConnectionTokens2() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, kEEP-alive, dumdy");

        assertTrue(reuseStrategy.keepAlive(response, context));
    }

    public void testConnectionTokens3() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, keep-alive, close, dumdy");

        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testConnectionTokens4() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, close, dumdy");
        response.addHeader("Proxy-Connection", "keep-alive");
        // Connection takes precedence over Proxy-Connection
        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testConnectionTokens5() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "yadda, dumdy");
        response.addHeader("Proxy-Connection", "close");
        // Connection takes precedence over Proxy-Connection,
        // even if it doesn't contain a recognized token.
        // Default for HTTP/1.1 is to keep alive.
        assertTrue(reuseStrategy.keepAlive(response, context));
    }

    public void testConnectionTokens6() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "");
        response.addHeader("Proxy-Connection", "close");
        // Connection takes precedence over Proxy-Connection,
        // even if it is empty. Default for HTTP/1.1 is to keep alive.
        assertTrue(reuseStrategy.keepAlive(response, context));
    }

    public void testConnectionTokensInvalid() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.addHeader("Connection", "keep-alive=true");
        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testMultipleContentLength() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Content-Length", "10");
        response.addHeader("Content-Length", "11");
        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testInvalidContentLength() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Content-Length", "crap");
        assertFalse(reuseStrategy.keepAlive(response, context));
    }

    public void testInvalidNegativeContentLength() throws Exception {
        // Use HTTP 1.1
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader("Content-Length", "-10");
        assertFalse(reuseStrategy.keepAlive(response, context));
    }

}

