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

package org.apache.hc.core5.http.protocol;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.io.EmptyInputStream;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWithTrailers;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.Assert;
import org.junit.Test;

public class TestStandardInterceptors {

    @Test
    public void testRequestConnControlGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("keep-alive", header.getValue());
    }

    @Test
    public void testRequestConnControlConnectMethod() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("CONNECT", "/");
        final RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestConnControlCustom() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final Header myheader = new BasicHeader(HttpHeaders.CONNECTION, "close");
        request.addHeader(myheader);
        final RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("close", header.getValue());
        Assert.assertTrue(header == myheader);
    }

    @Test
    public void testRequestConnControlUpgrade() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.UPGRADE, "HTTP/2");
        final RequestConnControl interceptor = new RequestConnControl();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("upgrade", header.getValue());
    }

    @Test
    public void testRequestConnControlInvalidInput() throws Exception {
        final RequestConnControl interceptor = new RequestConnControl();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestContentProtocolException() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest("POST", "/");
        request1.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "chunked"));
        final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest("POST", "/");
        request2.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "12"));

        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request1, request1.getEntity(), context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
        try {
            interceptor.process(request2, request2.getEntity(), context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
   }

    @Test
    public void testRequestContentNullEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNull(header);
        Assert.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
   }

    @Test
    public void testRequestContentEntityContentLengthDelimitedHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNotNull(header);
        Assert.assertEquals(8, Integer.parseInt(header.getValue()));
        Assert.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
   }

    @Test
    public void testRequestContentEntityChunkedHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII, true));

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNotNull(header);
        Assert.assertEquals("chunked", header.getValue());
        Assert.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_LENGTH));
   }

    @Test
    public void testRequestContentEntityUnknownLengthHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, -1, null));

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNotNull(header);
        Assert.assertEquals("chunked", header.getValue());
        Assert.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void testRequestContentEntityChunkedHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII, true));

        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request, request.getEntity(), context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testRequestContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE,
                ContentType.parseLenient("whatever"), "whatever"));

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assert.assertNotNull(h1);
        Assert.assertEquals("whatever", h1.getValue());
        final Header h2 = request.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        Assert.assertNotNull(h2);
        Assert.assertEquals("whatever", h2.getValue());
    }

    @Test
    public void testRequestContentNullTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, null));

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        Assert.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_TYPE));
        Assert.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    @Test
    public void testRequestContentEntityUnknownLengthHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, -1, null));

        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(request, request.getEntity(), context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
   }

    @Test
    public void testRequestContentInvalidInput() throws Exception {
        final RequestContent interceptor = new RequestContent();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestContentIgnoreNonenclosingRequests() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        Assert.assertEquals(0, request.getHeaders().length);
    }

    @Test
    public void testRequestContentOverwriteHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        request.setEntity(new StringEntity(""));
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNotNull(h1);
        Assert.assertEquals("0", h1.getValue());
    }

    @Test
    public void testRequestContentAddHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity(""));
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNotNull(h1);
        Assert.assertEquals("0", h1.getValue());
        final Header h2 = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNull(h2);
    }

    @Test
    public void testRequestContentEntityWithTrailers() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new HttpEntityWithTrailers(new StringEntity("whatever", StandardCharsets.US_ASCII),
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final RequestContent interceptor = new RequestContent();
        interceptor.process(request, request.getEntity(), context);
        final Header header1 = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNotNull(header1);
        final Header header2 = request.getFirstHeader(HttpHeaders.TRAILER);
        Assert.assertNotNull(header2);
        Assert.assertEquals("h1, h2", header2.getValue());
    }

    @Test
    public void testRequestExpectContinueGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assert.assertNotNull(header);
        Assert.assertEquals(HeaderElements.CONTINUE, header.getValue());
    }

    @Test
    public void testRequestExpectContinueHTTP10() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueZeroContent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("", StandardCharsets.US_ASCII));
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueInvalidInput() throws Exception {
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, request.getEntity(), context);
        Assert.assertEquals(0, request.getHeaders().length);
    }

    @Test
    public void testRequestTargetHostGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assert.assertNotNull(header);
        Assert.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    public void testRequestTargetHostNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        request.addHeader(new BasicHeader(HttpHeaders.HOST, "whatever"));
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assert.assertNotNull(header);
        Assert.assertEquals("whatever", header.getValue());
    }

    @Test
    public void testRequestTargetHostMissingHostHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestTargetHostMissingHostHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final RequestTargetHost interceptor = new RequestTargetHost();
        try {
            interceptor.process(request, request.getEntity(), context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testRequestTargetHostInvalidInput() throws Exception {
        final RequestTargetHost interceptor = new RequestTargetHost();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            interceptor.process(new BasicClassicHttpRequest("GET", "/"), null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestTargetHostConnectHttp11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("CONNECT", "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assert.assertNotNull(header);
        Assert.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    public void testRequestTargetHostConnectHttp10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("CONNECT", "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final RequestTargetHost interceptor = new RequestTargetHost();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestUserAgentGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assert.assertNotNull(header);
        Assert.assertEquals("some agent", header.getValue());
    }

    @Test
    public void testRequestUserAgentNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HttpHeaders.USER_AGENT, "whatever"));
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assert.assertNotNull(header);
        Assert.assertEquals("whatever", header.getValue());
    }

    @Test
    public void testRequestUserAgentMissingUserAgent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final RequestUserAgent interceptor = new RequestUserAgent();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestUserAgentInvalidInput() throws Exception {
        final RequestUserAgent interceptor = new RequestUserAgent();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResponseConnControlNoEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityContentLength() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityUnknownContentLengthExplicitKeepAlive() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("keep-alive", header.getValue());
    }

    @Test
    public void testResponseConnControlEntityChunked() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, true));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityUnknownContentLengthHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequest() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("keep-alive", header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequest2() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseConnControl10Client11Response() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlStatusCode() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
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
            final ClassicHttpResponse response = new BasicClassicHttpResponse(statusCode, "Unreasonable");
            interceptor.process(response, response.getEntity(), context);
            final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
            Assert.assertNotNull(header);
            Assert.assertEquals("close", header.getValue());
        }

    }

    @Test
    public void testResponseConnControlExplicitClose() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.setHeader(HttpHeaders.CONNECTION, "close");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequestMixUp() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "blah, keep-alive, close"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlUpgrade() throws Exception {
        final HttpContext context = new BasicHttpContext(null);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader(HttpHeaders.UPGRADE, "HTTP/2");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals("upgrade", header.getValue());
    }

    @Test
    public void testResponseConnControlHostInvalidInput() throws Exception {
        final ResponseConnControl interceptor = new ResponseConnControl();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
            interceptor.process(response, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResponseContentNoEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNotNull(header);
        Assert.assertEquals("0", header.getValue());
    }

    @Test
    public void testResponseContentStatusNoContent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(HttpStatus.SC_NO_CONTENT);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseContentStatusNotModified() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(HttpStatus.SC_NOT_MODIFIED);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNull(header);
    }

    @Test
    public void testResponseContentEntityChunked() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, true));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNotNull(h1);
        Assert.assertEquals("chunked", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityContentLenghtDelimited() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, 10, null));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNotNull(h1);
        Assert.assertEquals("10", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityUnknownContentLength() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNotNull(h1);
        Assert.assertEquals("chunked", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityChunkedHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, true));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNull(h1);
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityNoContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assert.assertNull(h1);
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        Assert.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE,
                ContentType.parseLenient("whatever"), "whatever"));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assert.assertNotNull(h1);
        Assert.assertEquals("whatever", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        Assert.assertNotNull(h2);
        Assert.assertEquals("whatever", h2.getValue());
    }

    @Test
    public void testResponseContentInvalidInput() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        try {
            interceptor.process(null, null, null);
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
            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
            response.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
            interceptor.process(response, response.getEntity(), context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
        try {
            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
            response.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "stuff"));
            interceptor.process(response, response.getEntity(), context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testResponseContentOverwriteHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        response.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        interceptor.process(response, response.getEntity(), context);
        Assert.assertEquals("0", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
    }

    @Test
    public void testResponseContentAddHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        interceptor.process(response, response.getEntity(), context);
        Assert.assertEquals("0", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
        Assert.assertNull(response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
    }

    @Test
    public void testResponseContentEntityWithTrailers() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new HttpEntityWithTrailers(new StringEntity("whatever", StandardCharsets.US_ASCII),
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header1 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assert.assertNotNull(header1);
        final Header header2 = response.getFirstHeader(HttpHeaders.TRAILER);
        Assert.assertNotNull(header2);
        Assert.assertEquals("h1, h2", header2.getValue());
    }

    @Test
    public void testResponseDateGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.DATE);
        Assert.assertNotNull(h1);
        interceptor.process(response, response.getEntity(), context);
        final Header h2 = response.getFirstHeader(HttpHeaders.DATE);
        Assert.assertNotNull(h2);
    }

    @Test
    public void testResponseDateNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(199);
        final ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.DATE);
        Assert.assertNull(h1);
    }

    @Test
    public void testResponseDateInvalidInput() throws Exception {
        final ResponseDate interceptor = new ResponseDate();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestDateGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("stuff"));

        final RequestDate interceptor = new RequestDate();
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.DATE);
        Assert.assertNotNull(h1);
        interceptor.process(request, request.getEntity(), context);
        final Header h2 = request.getFirstHeader(HttpHeaders.DATE);
        Assert.assertNotNull(h2);
    }

    @Test
    public void testRequestDateNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");

        final RequestDate interceptor = new RequestDate();
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.DATE);
        Assert.assertNull(h1);
    }

    @Test
    public void testRequestDateInvalidInput() throws Exception {
        final RequestDate interceptor = new RequestDate();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testResponseServerGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assert.assertNotNull(h1);
        Assert.assertEquals("some server", h1.getValue());
    }

    @Test
    public void testResponseServerNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HttpHeaders.SERVER, "whatever"));
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assert.assertNotNull(h1);
        Assert.assertEquals("whatever", h1.getValue());
    }

    @Test
    public void testResponseServerMissing() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assert.assertNull(h1);
    }

    @Test
    public void testResponseServerInvalidInput() throws Exception {
        final ResponseServer interceptor = new ResponseServer();
        try {
            interceptor.process(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestHttp10HostHeaderAbsent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setVersion(HttpVersion.HTTP_1_0);
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test
    public void testRequestHttpHostHeader() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.HOST, "host:8888");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assert.assertEquals(new URIAuthority("host", 8888), request.getAuthority());
    }

    @Test
    public void testRequestHttpHostHeaderNoPort() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.HOST, "host");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assert.assertEquals(new URIAuthority("host"), request.getAuthority());
    }

    @Test
    public void testRequestHttp11HostHeaderPresent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test(expected = ProtocolException.class)
    public void testRequestHttp11HostHeaderAbsent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test(expected = ProtocolException.class)
    public void testRequestHttp11MultipleHostHeaders() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.HOST, "blah");
        request.addHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
    }

}
