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

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.HttpHost;
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
import org.apache.http.params.CoreProtocolPNames;

/**
 */
public class TestStandardInterceptors extends TestCase {

    public TestStandardInterceptors(String testName) {
        super(testName);
    }

    public void testRequestConnControlGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNotNull(header);
        assertEquals(HTTP.CONN_KEEP_ALIVE, header.getValue());
    }

    public void testRequestConnControlCustom() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        Header myheader = new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        request.addHeader(myheader);
        RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNotNull(header);
        assertEquals(HTTP.CONN_CLOSE, header.getValue());
        assertTrue(header == myheader);
    }

    public void testRequestConnControlInvalidInput() throws Exception {
        RequestConnControl interceptor = new RequestConnControl();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testRequestContentProtocolException() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request1 = new BasicHttpEntityEnclosingRequest("POST", "/");
        request1.addHeader(new BasicHeader(HTTP.TRANSFER_ENCODING, "chunked"));
        BasicHttpRequest request2 = new BasicHttpEntityEnclosingRequest("POST", "/");
        request2.addHeader(new BasicHeader(HTTP.CONTENT_LEN, "12"));

        RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request1, context);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
        try {
            interceptor.process(request2, context);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
   }

    public void testRequestContentNullEntity() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");

        RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.CONTENT_LEN);
        assertNotNull(header);
        assertEquals("0", header.getValue());
        assertNull(request.getFirstHeader(HTTP.TRANSFER_ENCODING));
   }

    public void testRequestContentEntityContentLengthDelimitedHTTP11() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);

        RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.CONTENT_LEN);
        assertNotNull(header);
        assertEquals(s.length(), Integer.parseInt(header.getValue()));
        assertNull(request.getFirstHeader(HTTP.TRANSFER_ENCODING));
   }

    public void testRequestContentEntityChunkedHTTP11() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        entity.setChunked(true);
        request.setEntity(entity);

        RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.TRANSFER_ENCODING);
        assertNotNull(header);
        assertEquals("chunked", header.getValue());
        assertNull(request.getFirstHeader(HTTP.CONTENT_LEN));
   }

    public void testRequestContentEntityUnknownLengthHTTP11() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentLength(-1);
        entity.setChunked(false);
        request.setEntity(entity);

        RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.TRANSFER_ENCODING);
        assertNotNull(header);
        assertEquals("chunked", header.getValue());
        assertNull(request.getFirstHeader(HTTP.CONTENT_LEN));
    }

    public void testRequestContentEntityChunkedHTTP10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", "/", HttpVersion.HTTP_1_0);
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        entity.setChunked(true);
        request.setEntity(entity);

        RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request, context);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testRequestContentTypeAndEncoding() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentType("whatever");
        entity.setContentEncoding("whatever");
        request.setEntity(entity);

        RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        Header h1 = request.getFirstHeader(HTTP.CONTENT_TYPE);
        assertNotNull(h1);
        assertEquals("whatever", h1.getValue());
        Header h2 = request.getFirstHeader(HTTP.CONTENT_ENCODING);
        assertNotNull(h2);
        assertEquals("whatever", h2.getValue());
    }

    public void testRequestContentNullTypeAndEncoding() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        BasicHttpEntity entity = new BasicHttpEntity();
        request.setEntity(entity);

        RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        assertNull(request.getFirstHeader(HTTP.CONTENT_TYPE));
        assertNull(request.getFirstHeader(HTTP.CONTENT_ENCODING));
    }

    public void testRequestContentEntityUnknownLengthHTTP10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", "/", HttpVersion.HTTP_1_0);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentLength(-1);
        entity.setChunked(false);
        request.setEntity(entity);

        RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request, context);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
   }

    public void testRequestContentInvalidInput() throws Exception {
        RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testRequestContentIgnoreNonenclosingRequests() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        RequestContent interceptor = new RequestContent();
        interceptor.process(request, context);
        assertEquals(0, request.getAllHeaders().length);
    }

   public void testRequestExpectContinueGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        assertNotNull(header);
        assertEquals(HTTP.EXPECT_CONTINUE, header.getValue());
    }

    public void testRequestExpectContinueNotGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        assertNull(header);
    }

    public void testRequestExpectContinueHTTP10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", "/", HttpVersion.HTTP_1_0);
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        assertNull(header);
    }

    public void testRequestExpectContinueZeroContent() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        assertNull(header);
    }

    public void testRequestExpectContinueInvalidInput() throws Exception {
        RequestExpectContinue interceptor = new RequestExpectContinue();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        assertEquals(0, request.getAllHeaders().length);
    }

    public void testRequestTargetHostGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, host);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        assertNotNull(header);
        assertEquals("somehost:8080", header.getValue());
    }

    public void testRequestTargetHostNotGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, host);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.TARGET_HOST, "whatever"));
        RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        assertNotNull(header);
        assertEquals("whatever", header.getValue());
    }

    public void testRequestTargetHostMissingHostHTTP10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest(
                "GET", "/", HttpVersion.HTTP_1_0);
        RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        assertNull(header);
    }

    public void testRequestTargetHostMissingHostHTTP11() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        RequestTargetHost interceptor = new RequestTargetHost();
        try {
            interceptor.process(request, context);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testRequestTargetHostInvalidInput() throws Exception {
        RequestTargetHost interceptor = new RequestTargetHost();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            interceptor.process(new BasicHttpRequest("GET", "/"), null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testRequestTargetHostConnectHttp11() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, host);
        BasicHttpRequest request = new BasicHttpRequest("CONNECT", "/");
        RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        assertNotNull(header);
        assertEquals("somehost:8080", header.getValue());
    }

    public void testRequestTargetHostConnectHttp10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpHost host = new HttpHost("somehost", 8080, "http");
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, host);
        BasicHttpRequest request = new BasicHttpRequest("CONNECT", "/", HttpVersion.HTTP_1_0);
        RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.TARGET_HOST);
        assertNull(header);
    }

    public void testRequestUserAgentGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "some agent");
        RequestUserAgent interceptor = new RequestUserAgent();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.USER_AGENT);
        assertNotNull(header);
        assertEquals("some agent", header.getValue());
    }

    public void testRequestUserAgentNotGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "some agent");
        request.addHeader(new BasicHeader(HTTP.USER_AGENT, "whatever"));
        RequestUserAgent interceptor = new RequestUserAgent();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.USER_AGENT);
        assertNotNull(header);
        assertEquals("whatever", header.getValue());
    }

    public void testRequestUserAgentMissingUserAgent() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        RequestUserAgent interceptor = new RequestUserAgent();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.USER_AGENT);
        assertNull(header);
    }

    public void testRequestUserAgentInvalidInput() throws Exception {
        RequestUserAgent interceptor = new RequestUserAgent();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testResponseConnControlNoEntity() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNull(header);
    }

    public void testResponseConnControlEntityContentLength() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        StringEntity entity = new StringEntity("whatever");
        response.setEntity(entity);
        ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNull(header);
    }

    public void testResponseConnControlEntityUnknownContentLength() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNotNull(header);
        assertEquals(HTTP.CONN_CLOSE, header.getValue());
    }

    public void testResponseConnControlEntityChunked() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(true);
        response.setEntity(entity);
        ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNull(header);
    }

    public void testResponseConnControlEntityUnknownContentLengthHTTP10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);

        BasicHttpResponse response = new BasicHttpResponse(
                HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNotNull(header);
        assertEquals(HTTP.CONN_CLOSE, header.getValue());
    }

    public void testResponseConnControlClientRequest() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);

        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        StringEntity entity = new StringEntity("whatever");
        response.setEntity(entity);
        ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNotNull(header);
        assertEquals(HTTP.CONN_KEEP_ALIVE, header.getValue());
    }

    public void testResponseConnControlClientRequest2() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);

        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        StringEntity entity = new StringEntity("whatever");
        response.setEntity(entity);
        ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
        assertNull(header);
    }

    public void testResponseConnControlStatusCode() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE));
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);

        ResponseConnControl interceptor = new ResponseConnControl();

        int [] statusCodes = new int[] {
                HttpStatus.SC_BAD_REQUEST,
                HttpStatus.SC_REQUEST_TIMEOUT,
                HttpStatus.SC_LENGTH_REQUIRED,
                HttpStatus.SC_REQUEST_TOO_LONG,
                HttpStatus.SC_REQUEST_URI_TOO_LONG,
                HttpStatus.SC_SERVICE_UNAVAILABLE,
                HttpStatus.SC_NOT_IMPLEMENTED };

        for (int i = 0; i < statusCodes.length; i++) {
            BasicHttpResponse response = new BasicHttpResponse(
                    HttpVersion.HTTP_1_1, statusCodes[i], "Unreasonable");
            interceptor.process(response, context);
            Header header = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
            assertNotNull(header);
            assertEquals(HTTP.CONN_CLOSE, header.getValue());
        }

    }

    public void testResponseConnControlHostInvalidInput() throws Exception {
        ResponseConnControl interceptor = new ResponseConnControl();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
            interceptor.process(response, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testResponseContentNoEntity() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNotNull(header);
        assertEquals("0", header.getValue());
    }

    public void testResponseContentStatusNoContent() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNull(header);
    }

    public void testResponseContentStatusResetContent() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(HttpStatus.SC_RESET_CONTENT);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNull(header);
    }

    public void testResponseContentStatusNotModified() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header header = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNull(header);
    }

    public void testResponseContentEntityChunked() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(true);
        response.setEntity(entity);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        assertNotNull(h1);
        assertEquals(HTTP.CHUNK_CODING, h1.getValue());
        Header h2 = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNull(h2);
    }

    public void testResponseContentEntityContentLenghtDelimited() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentLength (10);
        response.setEntity(entity);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNotNull(h1);
        assertEquals("10", h1.getValue());
        Header h2 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        assertNull(h2);
    }

    public void testResponseContentEntityUnknownContentLength() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        assertNull(h1);
        Header h2 = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNull(h2);
    }

    public void testResponseContentEntityChunkedHTTP10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(true);
        response.setEntity(entity);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        assertNull(h1);
        Header h2 = response.getFirstHeader(HTTP.CONTENT_LEN);
        assertNull(h2);
    }

    public void testResponseContentEntityNoContentTypeAndEncoding() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.CONTENT_TYPE);
        assertNull(h1);
        Header h2 = response.getFirstHeader(HTTP.CONTENT_ENCODING);
        assertNull(h2);
    }

    public void testResponseContentEntityContentTypeAndEncoding() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentEncoding("whatever");
        entity.setContentType("whatever");
        response.setEntity(entity);
        ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.CONTENT_TYPE);
        assertNotNull(h1);
        assertEquals("whatever", h1.getValue());
        Header h2 = response.getFirstHeader(HTTP.CONTENT_ENCODING);
        assertNotNull(h2);
        assertEquals("whatever", h2.getValue());
    }

    public void testResponseContentInvalidInput() throws Exception {
        ResponseContent interceptor = new ResponseContent();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testResponseContentInvalidResponseState() throws Exception {
        ResponseContent interceptor = new ResponseContent();
        HttpContext context = new BasicHttpContext(null);
        try {
            HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
            response.addHeader(new BasicHeader(HTTP.CONTENT_LEN, "10"));
            interceptor.process(response, context);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
        try {
            HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
            response.addHeader(new BasicHeader(HTTP.TRANSFER_ENCODING, "stuff"));
            interceptor.process(response, context);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testResponseDateGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.DATE_HEADER);
        assertNotNull(h1);
        interceptor.process(response, context);
        Header h2 = response.getFirstHeader(HTTP.DATE_HEADER);
        assertNotNull(h2);
    }

    public void testResponseDateNotGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setStatusCode(199);
        ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.DATE_HEADER);
        assertNull(h1);
    }

    public void testResponseDateInvalidInput() throws Exception {
        ResponseDate interceptor = new ResponseDate();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testRequestDateGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request =
            new BasicHttpEntityEnclosingRequest("POST", "/");
        //BasicHttpRequest request = new BasicHttpRequest("GET", "/");

        RequestDate interceptor = new RequestDate();
        interceptor.process(request, context);
        Header h1 = request.getFirstHeader(HTTP.DATE_HEADER);
        assertNotNull(h1);
        interceptor.process(request, context);
        Header h2 = request.getFirstHeader(HTTP.DATE_HEADER);
        assertNotNull(h2);
    }

    public void testRequestDateNotGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/");

        RequestDate interceptor = new RequestDate();
        interceptor.process(request, context);
        Header h1 = request.getFirstHeader(HTTP.DATE_HEADER);
        assertNull(h1);
    }

    public void testRequestDateInvalidInput() throws Exception {
        RequestDate interceptor = new RequestDate();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testResponseServerGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.getParams().setParameter(CoreProtocolPNames.ORIGIN_SERVER, "some server");
        ResponseServer interceptor = new ResponseServer();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.SERVER_HEADER);
        assertNotNull(h1);
        assertEquals("some server", h1.getValue());
    }

    public void testResponseServerNotGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.getParams().setParameter(CoreProtocolPNames.ORIGIN_SERVER, "some server");
        response.addHeader(new BasicHeader(HTTP.SERVER_HEADER, "whatever"));
        ResponseServer interceptor = new ResponseServer();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.SERVER_HEADER);
        assertNotNull(h1);
        assertEquals("whatever", h1.getValue());
    }

    public void testResponseServerMissing() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        ResponseServer interceptor = new ResponseServer();
        interceptor.process(response, context);
        Header h1 = response.getFirstHeader(HTTP.SERVER_HEADER);
        assertNull(h1);
    }

    public void testResponseServerInvalidInput() throws Exception {
        ResponseServer interceptor = new ResponseServer();
        try {
            interceptor.process(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
