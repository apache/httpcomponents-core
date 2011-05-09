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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultConnectionReuseStrategy {

    /** A mock connection that is open and not stale. */
    private HttpConnection mockConnection;

    /** HTTP context. */
    private HttpContext context;

    /** The reuse strategy to be tested. */
    private ConnectionReuseStrategy reuseStrategy;

    @Before
    public void setUp() {
        // open and not stale is required for most of the tests here
        mockConnection = new MockConnection(true, false);
        reuseStrategy = new DefaultConnectionReuseStrategy();
        context = new BasicHttpContext(null);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, mockConnection);
    }

    @After
    public void tearDown() {
        mockConnection = null;
    }

    // ------------------------------------------------------- TestCase Methods

    @Test
    public void testIllegalResponseArg() throws Exception {

        HttpContext context = new BasicHttpContext(null);

        try {
            reuseStrategy.keepAlive(null, context);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testIllegalContextArg() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", false, -1);
        try {
            reuseStrategy.keepAlive(response, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testNoContentLengthResponseHttp1_0() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK", false, -1);

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testNoContentLengthResponseHttp1_1() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", false, -1);

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testChunkedContent() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);

        Assert.assertTrue(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testClosedConnection() throws Exception {

        // based on testChunkedContent which is known to return true
        // the difference is in the mock connection passed here
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);

        HttpConnection mockonn = new MockConnection(false, false);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, mockonn);
        Assert.assertFalse("closed connection should not be kept alive",
                    reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testStaleConnection() throws Exception {

        // based on testChunkedContent which is known to return true
        // the difference is in the mock connection passed here
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);

        HttpConnection mockonn = new MockConnection(true, true);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, mockonn);
        Assert.assertTrue("stale connection should not be detected",
                    reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testIgnoreInvalidKeepAlive() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK", false, -1);
        response.addHeader("Connection", "keep-alive");

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testExplicitClose() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "close");

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testExplicitKeepAlive() throws Exception {
        // Use HTTP 1.0
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK", false, 10);
        response.addHeader("Connection", "keep-alive");

        Assert.assertTrue(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testHTTP10Default() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK");

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testHTTP11Default() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Assert.assertTrue(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testFutureHTTP() throws Exception {
        HttpResponse response =
            createResponse(new HttpVersion(3, 45), 200, "OK");

        Assert.assertTrue(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testBrokenConnectionDirective1() throws Exception {
        // Use HTTP 1.0
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Connection", "keep--alive");

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testBrokenConnectionDirective2() throws Exception {
        // Use HTTP 1.0
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Connection", null);

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testConnectionTokens1() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "yadda, cLOSe, dumdy");

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testConnectionTokens2() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "yadda, kEEP-alive, dumdy");

        Assert.assertTrue(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testConnectionTokens3() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "yadda, keep-alive, close, dumdy");

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testConnectionTokens4() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "yadda, close, dumdy");
        response.addHeader("Proxy-Connection", "keep-alive");

        // Connection takes precedence over Proxy-Connection
        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testConnectionTokens5() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "yadda, dumdy");
        response.addHeader("Proxy-Connection", "close");

        // Connection takes precedence over Proxy-Connection,
        // even if it doesn't contain a recognized token.
        // Default for HTTP/1.1 is to keep alive.
        Assert.assertTrue(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testConnectionTokens6() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "");
        response.addHeader("Proxy-Connection", "close");

        // Connection takes precedence over Proxy-Connection,
        // even if it is empty. Default for HTTP/1.1 is to keep alive.
        Assert.assertTrue(reuseStrategy.keepAlive(response, context));
    }

    @Test
    public void testConnectionTokensInvalid() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "keep-alive=true");

        Assert.assertFalse(reuseStrategy.keepAlive(response, context));
    }


    /**
     * Creates a response without an entity.
     *
     * @param version   the HTTP version
     * @param status    the status code
     * @param message   the status message
     *
     * @return  a response with the argument attributes, but no headers
     */
    private final static HttpResponse createResponse(HttpVersion version,
                                                     int status,
                                                     String message) {

        StatusLine statusline = new BasicStatusLine(version, status, message);
        HttpResponse response = new BasicHttpResponse(statusline);

        return response;

    } // createResponse/empty


    /**
     * Creates a response with an entity.
     *
     * @param version   the HTTP version
     * @param status    the status code
     * @param message   the status message
     * @param chunked   whether the entity should indicate chunked encoding
     * @param length    the content length to be indicated by the entity
     *
     * @return  a response with the argument attributes, but no headers
     */
    private final static HttpResponse createResponse(HttpVersion version,
                                                     int status,
                                                     String message,
                                                     boolean chunked,
                                                     int length) {

        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(chunked);
        entity.setContentLength(length);
        HttpResponse response = createResponse(version, status, message);
        response.setEntity(entity);

        return response;

    } // createResponse/entity


    /**
     * A mock connection.
     * This is neither client nor server connection, since the default
     * strategy is agnostic. It does not allow modification of it's state,
     * since the strategy is supposed to decide about keep-alive, but not
     * to modify the connection's state.
     */
    private final static class MockConnection implements HttpConnection {

        private boolean iAmOpen;
        private boolean iAmStale;

        public MockConnection(boolean open, boolean stale) {
            iAmOpen = open;
            iAmStale = stale;
        }

        public final boolean isOpen() {
            return iAmOpen;
        }

        public void setSocketTimeout(int timeout) {
        }

        public int getSocketTimeout() {
            return -1;
        }

        public final boolean isStale() {
            return iAmStale;
        }

        public final void close() {
            throw new UnsupportedOperationException
                ("connection state must not be modified");
        }

        public final void shutdown() {
            throw new UnsupportedOperationException
                ("connection state must not be modified");
        }

        public HttpConnectionMetrics getMetrics() {
            return null;
        }

    } // class MockConnection

} // class TestDefaultConnectionReuseStrategy

