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

package org.apache.http.protocol;

import java.net.InetAddress;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestStandardInterceptors {

    @Test
    public void testRequestConnControlGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.CONN_KEEP_ALIVE, header.getValue());
    }

    @Test
    public void testRequestConnControlConnectMethod() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("CONNECT", "/");
        final RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestConnControlCustom() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final Header myheader = new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        request.addHeader(myheader);
        final RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.CONN_CLOSE, header.getValue());
        Assert.assertTrue(header == myheader);
    }

    @Test
    public void testRequestConnControlInvalidInput() throws Exception {
        final RequestConnControl interceptor = new RequestConnControl();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestContentProtocolException() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request1 = new BasicHttpEntityEnclosingRequest("POST", "/");
        request1.addHeader(new BasicHeader(HTTP.TRANSFER_ENCODING, "chunked"));
        final BasicHttpRequest request2 = new BasicHttpEntityEnclosingRequest("POST", "/");
        request2.addHeader(new BasicHeader(HTTP.CONTENT_LEN, "12"));

        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request1, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
        try {
            interceptor.process(request2, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
   }

    @Test
    public void testRequestContentNullEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNotNull(header);
        Assert.assertEquals("0", header.getValue());
        Assert.assertNull(request.getFirstHeader(HTTP.TRANSFER_ENCODING));
   }

    @Test
    public void testRequestContentEntityContentLengthDelimitedHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNotNull(header);
        Assert.assertEquals(s.length(), Integer.parseInt(header.getValue()));
        Assert.assertNull(request.getFirstHeader(HTTP.TRANSFER_ENCODING));
   }

    @Test
    public void testRequestContentEntityChunkedHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, "US-ASCII");
        entity.setChunked(true);
        request.setEntity(entity);

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TRANSFER_ENCODING);
        Assert.assertNotNull(header);
        Assert.assertEquals("chunked", header.getValue());
        Assert.assertNull(request.getFirstHeader(HTTP.CONTENT_LEN));
   }

    @Test
    public void testRequestContentEntityUnknownLengthHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentLength(-1);
        entity.setChunked(false);
        request.setEntity(entity);

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TRANSFER_ENCODING);
        Assert.assertNotNull(header);
        Assert.assertEquals("chunked", header.getValue());
        Assert.assertNull(request.getFirstHeader(HTTP.CONTENT_LEN));
    }

    @Test
    public void testRequestContentEntityChunkedHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", "/", HttpVersion.HTTP_1_0);
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, "US-ASCII");
        entity.setChunked(true);
        request.setEntity(entity);

        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testRequestContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentType("whatever");
        entity.setContentEncoding("whatever");
        request.setEntity(entity);

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        final Header h1 = request.getFirstHeader(HTTP.CONTENT_TYPE);
        Assert.assertNotNull(h1);
        Assert.assertEquals("whatever", h1.getValue());
        final Header h2 = request.getFirstHeader(HTTP.CONTENT_ENCODING);
        Assert.assertNotNull(h2);
        Assert.assertEquals("whatever", h2.getValue());
    }

    @Test
    public void testRequestContentNullTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final BasicHttpEntity entity = new BasicHttpEntity();
        request.setEntity(entity);

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        Assert.assertNull(request.getFirstHeader(HTTP.CONTENT_TYPE));
        Assert.assertNull(request.getFirstHeader(HTTP.CONTENT_ENCODING));
    }

    @Test
    public void testRequestContentEntityUnknownLengthHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", "/", HttpVersion.HTTP_1_0);
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentLength(-1);
        entity.setChunked(false);
        request.setEntity(entity);

        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
   }

    @Test
    public void testRequestContentInvalidInput() throws Exception {
        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestContentIgnoreNonenclosingRequests() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        Assert.assertEquals(0, request.getAllHeaders().length);
    }

    @Test
    public void testRequestContentOverwriteHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(new BasicHeader(HTTP.CONTENT_LEN, "10"));
        request.addHeader(new BasicHeader(HTTP.TRANSFER_ENCODING, "whatever"));
        interceptor.process(request, context);
        Assert.assertEquals("0", request.getFirstHeader(HTTP.CONTENT_LEN).getValue());
    }

    @Test
    public void testRequestContentAddHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        interceptor.process(request, context);
        Assert.assertEquals("0", request.getFirstHeader(HTTP.CONTENT_LEN).getValue());
        Assert.assertNull(request.getFirstHeader(HTTP.TRANSFER_ENCODING));
    }

    @Test
    public void testRequestExpectContinueGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue(true);
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.EXPECT_CONTINUE, header.getValue());
    }

    @Test
    public void testRequestExpectContinueHTTP10() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", "/", HttpVersion.HTTP_1_0);
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue(true);
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueZeroContent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final String s = "";
        final StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue(true);
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueInvalidInput() throws Exception {
        final RequestExpectContinue interceptor = new RequestExpectContinue(true);
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        final RequestExpectContinue interceptor = new RequestExpectContinue(true);
        interceptor.process(request, context);
        Assert.assertEquals(0, request.getAllHeaders().length);
    }

    @Test
    public void testRequestTargetHostGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, host);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        Assert.assertNotNull(header);
        Assert.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    public void testRequestTargetHostFallback() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final InetAddress address = Mockito.mock(InetAddress.class);
        Mockito.when(address.getHostName()).thenReturn("somehost");
        final HttpInetConnection conn = Mockito.mock(HttpInetConnection.class);
        Mockito.when(conn.getRemoteAddress()).thenReturn(address);
        Mockito.when(conn.getRemotePort()).thenReturn(1234);
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, null);
        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        Assert.assertNotNull(header);
        Assert.assertEquals("somehost:1234", header.getValue());
    }

    @Test(expected=ProtocolException.class)
    public void testRequestTargetHostFallbackFailure() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpInetConnection conn = Mockito.mock(HttpInetConnection.class);
        Mockito.when(conn.getRemoteAddress()).thenReturn(null);
        Mockito.when(conn.getRemotePort()).thenReturn(1234);
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, null);
        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
    }

    @Test
    public void testRequestTargetHostNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, host);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.TARGET_HOST, "whatever"));
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        Assert.assertNotNull(header);
        Assert.assertEquals("whatever", header.getValue());
    }

    @Test
    public void testRequestTargetHostMissingHostHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest(
                "GET", "/", HttpVersion.HTTP_1_0);
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestTargetHostMissingHostHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final RequestTargetHost interceptor = new RequestTargetHost();
        try {
            interceptor.process(request, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testRequestTargetHostInvalidInput() throws Exception {
        final RequestTargetHost interceptor = new RequestTargetHost();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            interceptor.process(new BasicHttpRequest("GET", "/"), null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestTargetHostConnectHttp11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, host);
        final BasicHttpRequest request = new BasicHttpRequest("CONNECT", "/");
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        Assert.assertNotNull(header);
        Assert.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    public void testRequestTargetHostConnectHttp10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, host);
        final BasicHttpRequest request = new BasicHttpRequest("CONNECT", "/", HttpVersion.HTTP_1_0);
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestUserAgentGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.USER_AGENT);
        Assert.assertNotNull(header);
        Assert.assertEquals("some agent", header.getValue());
    }

    @Test
    public void testRequestUserAgentNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.USER_AGENT, "whatever"));
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.USER_AGENT);
        Assert.assertNotNull(header);
        Assert.assertEquals("whatever", header.getValue());
    }

    @Test
    public void testRequestUserAgentMissingUserAgent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final RequestUserAgent interceptor = new RequestUserAgent();
        interceptor.process(request, context);
        final Header header = request.getFirstHeader(HTTP.USER_AGENT);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestUserAgentInvalidInput() throws Exception {
        final RequestUserAgent interceptor = new RequestUserAgent();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResponseConnControlNoEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityContentLength() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final StringEntity entity = new StringEntity("whatever");
        response.setEntity(entity);
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityUnknownContentLength() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.CONN_CLOSE, header.getValue());
    }

    @Test
    public void testResponseConnControlEntityChunked() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(true);
        response.setEntity(entity);
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityUnknownContentLengthHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final BasicHttpResponse response = new BasicHttpResponse(
                HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.CONN_CLOSE, header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequest() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final StringEntity entity = new StringEntity("whatever");
        response.setEntity(entity);
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.CONN_KEEP_ALIVE, header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequest2() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final StringEntity entity = new StringEntity("whatever");
        response.setEntity(entity);
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControl10Client11Response() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final StringEntity entity = new StringEntity("whatever");
        response.setEntity(entity);
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.CONN_CLOSE, header.getValue());
    }

    @Test
    public void testResponseConnControlStatusCode() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final int [] statusCodes = new int[] {
                HttpStatus.SC_BAD_REQUEST,
                HttpStatus.SC_REQUEST_TIMEOUT,
                HttpStatus.SC_LENGTH_REQUIRED,
                HttpStatus.SC_REQUEST_TOO_LONG,
                HttpStatus.SC_REQUEST_URI_TOO_LONG,
                HttpStatus.SC_SERVICE_UNAVAILABLE,
                HttpStatus.SC_NOT_IMPLEMENTED };

        for (final int statusCode : statusCodes) {
            final BasicHttpResponse response = new BasicHttpResponse(
                    HttpVersion.HTTP_1_1, statusCode, "Unreasonable");
            interceptor.process(response, context);
            final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
            Assert.assertNotNull(header);
            Assert.assertEquals(HTTP.CONN_CLOSE, header.getValue());
        }

    }

    @Test
    public void testResponseConnControlExplicitClose() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final BasicHttpResponse response = new BasicHttpResponse(
                HttpVersion.HTTP_1_1, 200, "OK");
        response.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.CONN_CLOSE, header.getValue());
    }

    @Test
    public void testResponseConnControlHostInvalidInput() throws Exception {
        final ResponseConnControl interceptor = new ResponseConnControl();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
            interceptor.process(response, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResponseContentNoEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNotNull(header);
        Assert.assertEquals("0", header.getValue());
    }

    @Test
    public void testResponseContentStatusNoContent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseContentStatusResetContent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(HttpStatus.SC_RESET_CONTENT);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseContentStatusNotModified() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseContentEntityChunked() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(true);
        response.setEntity(entity);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        Assert.assertNotNull(h1);
        Assert.assertEquals(HTTP.CHUNK_CODING, h1.getValue());
        final Header h2 = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityContentLenghtDelimited() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentLength (10);
        response.setEntity(entity);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNotNull(h1);
        Assert.assertEquals("10", h1.getValue());
        final Header h2 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityUnknownContentLength() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        Assert.assertNull(h1);
        final Header h2 = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityChunkedHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(true);
        response.setEntity(entity);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        Assert.assertNull(h1);
        final Header h2 = response.getFirstHeader(HTTP.CONTENT_LEN);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityNoContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.CONTENT_TYPE);
        Assert.assertNull(h1);
        final Header h2 = response.getFirstHeader(HTTP.CONTENT_ENCODING);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentEncoding("whatever");
        entity.setContentType("whatever");
        response.setEntity(entity);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.CONTENT_TYPE);
        Assert.assertNotNull(h1);
        Assert.assertEquals("whatever", h1.getValue());
        final Header h2 = response.getFirstHeader(HTTP.CONTENT_ENCODING);
        Assert.assertNotNull(h2);
        Assert.assertEquals("whatever", h2.getValue());
    }

    @Test
    public void testResponseContentInvalidInput() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResponseContentInvalidResponseState() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        final HttpContext context = new BasicHttpContext(null);
        try {
            final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
            response.addHeader(new BasicHeader(HTTP.CONTENT_LEN, "10"));
            interceptor.process(response, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
        try {
            final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
            response.addHeader(new BasicHeader(HTTP.TRANSFER_ENCODING, "stuff"));
            interceptor.process(response, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testResponseContentOverwriteHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HTTP.CONTENT_LEN, "10"));
        response.addHeader(new BasicHeader(HTTP.TRANSFER_ENCODING, "whatever"));
        interceptor.process(response, context);
        Assert.assertEquals("0", response.getFirstHeader(HTTP.CONTENT_LEN).getValue());
    }

    @Test
    public void testResponseContentAddHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        interceptor.process(response, context);
        Assert.assertEquals("0", response.getFirstHeader(HTTP.CONTENT_LEN).getValue());
        Assert.assertNull(response.getFirstHeader(HTTP.TRANSFER_ENCODING));
    }

    @Test
    public void testResponseDateGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.DATE_HEADER);
        Assert.assertNotNull(h1);
        interceptor.process(response, context);
        final Header h2 = response.getFirstHeader(HTTP.DATE_HEADER);
        Assert.assertNotNull(h2);
    }

    @Test
    public void testResponseDateNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(199);
        final ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.DATE_HEADER);
        Assert.assertNull(h1);
    }

    @Test
    public void testResponseDateInvalidInput() throws Exception {
        final ResponseDate interceptor = new ResponseDate();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestDateGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request =
            new BasicHttpEntityEnclosingRequest("POST", "/");
        //BasicHttpRequest request = new BasicHttpRequest("GET", "/");

        final RequestDate interceptor = new RequestDate();
        interceptor.process(request, context);
        final Header h1 = request.getFirstHeader(HTTP.DATE_HEADER);
        Assert.assertNotNull(h1);
        interceptor.process(request, context);
        final Header h2 = request.getFirstHeader(HTTP.DATE_HEADER);
        Assert.assertNotNull(h2);
    }

    @Test
    public void testRequestDateNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");

        final RequestDate interceptor = new RequestDate();
        interceptor.process(request, context);
        final Header h1 = request.getFirstHeader(HTTP.DATE_HEADER);
        Assert.assertNull(h1);
    }

    @Test
    public void testRequestDateInvalidInput() throws Exception {
        final RequestDate interceptor = new RequestDate();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResponseServerGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.SERVER_HEADER);
        Assert.assertNotNull(h1);
        Assert.assertEquals("some server", h1.getValue());
    }

    @Test
    public void testResponseServerNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HTTP.SERVER_HEADER, "whatever"));
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.SERVER_HEADER);
        Assert.assertNotNull(h1);
        Assert.assertEquals("whatever", h1.getValue());
    }

    @Test
    public void testResponseServerMissing() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer();
        interceptor.process(response, context);
        final Header h1 = response.getFirstHeader(HTTP.SERVER_HEADER);
        Assert.assertNull(h1);
    }

    @Test
    public void testResponseServerInvalidInput() throws Exception {
        final ResponseServer interceptor = new ResponseServer();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

}
