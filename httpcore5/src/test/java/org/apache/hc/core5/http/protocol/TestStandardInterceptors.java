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

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.EmptyInputStream;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestStandardInterceptors {

    @Test
    public void testRequestConnControlGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("keep-alive", header.getValue());
    }

    @Test
    public void testRequestConnControlConnectMethod() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.CONNECT, "/");
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    public void testRequestConnControlCustom() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final Header myheader = new BasicHeader(HttpHeaders.CONNECTION, "close");
        request.addHeader(myheader);
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
        Assertions.assertSame(header, myheader);
    }

    @Test
    public void testRequestConnControlUpgrade() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(HttpHeaders.UPGRADE, "HTTP/2");
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("upgrade", header.getValue());
    }

    @Test
    public void testRequestConnControlInvalidInput() throws Exception {
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    public void testRequestContentProtocolException() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/");
        request1.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "chunked"));
        final BasicClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/");
        request2.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "12"));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request1, request1.getEntity(), context));
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request2, request2.getEntity(), context));
   }

    @Test
    public void testRequestContentNullEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(header);
        Assertions.assertEquals(0, Integer.parseInt(header.getValue()));
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
   }

    @Test
    public void testRequestContentNullEntityNonEnclosingMethod() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
    }
    @Test
    public void testRequestContentEntityContentLengthDelimitedHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(header);
        Assertions.assertEquals(8, Integer.parseInt(header.getValue()));
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
   }

    @Test
    public void testRequestContentEntityChunkedHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII, true));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("chunked", header.getValue());
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_LENGTH));
   }

    @Test
    public void testRequestContentEntityUnknownLengthHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, -1, null));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("chunked", header.getValue());
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void testRequestContentEntityChunkedHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII, true));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE,
                ContentType.parseLenient("whatever"), "whatever"));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("whatever", h1.getValue());
        final Header h2 = request.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        Assertions.assertNotNull(h2);
        Assertions.assertEquals("whatever", h2.getValue());
    }

    @Test
    public void testRequestContentNullTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, null));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_TYPE));
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    @Test
    public void testRequestContentEntityUnknownLengthHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, -1, null));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
   }

    @Test
    public void testRequestContentInvalidInput() throws Exception {
        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    public void testRequestContentIgnoreNonenclosingRequests() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(1, request.getHeaders().length);
    }

    @Test
    public void testRequestContentOverwriteHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        request.setEntity(new StringEntity(""));
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("0", h1.getValue());
    }

    @Test
    public void testRequestContentAddHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity(""));
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("0", h1.getValue());
        final Header h2 = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNull(h2);
    }

    @Test
    public void testRequestContentEntityWithTrailers() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(HttpEntities.create("whatever", StandardCharsets.US_ASCII,
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header1 = request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNotNull(header1);
        final Header header2 = request.getFirstHeader(HttpHeaders.TRAILER);
        Assertions.assertNotNull(header2);
        Assertions.assertEquals("h1, h2", header2.getValue());
    }

    @Test
    public void testRequestContentTraceWithEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.TRACE, "/");
        request.setEntity(new StringEntity("stuff"));
        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestExpectContinueGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assertions.assertNotNull(header);
        Assertions.assertEquals(HeaderElements.CONTINUE, header.getValue());
    }

    @Test
    public void testRequestExpectContinueHTTP10() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assertions.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueZeroContent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("", StandardCharsets.US_ASCII));
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assertions.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueInvalidInput() throws Exception {
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    public void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(0, request.getHeaders().length);
    }

    @Test
    public void testRequestTargetHostGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    public void testRequestTargetHostNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        request.addHeader(new BasicHeader(HttpHeaders.HOST, "whatever"));
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("whatever", header.getValue());
    }

    @Test
    public void testRequestTargetHostMissingHostHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNull(header);
    }

    @Test
    public void testRequestTargetHostMissingHostHTTP11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestTargetHostInvalidInput() throws Exception {
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(new BasicClassicHttpRequest(Method.GET, "/"), null, null));
    }

    @Test
    public void testRequestTargetHostConnectHttp11() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.CONNECT, "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    public void testRequestTargetHostConnectHttp10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.CONNECT, "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNull(header);
    }

    @Test
    public void testRequestUserAgentGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("some agent", header.getValue());
    }

    @Test
    public void testRequestUserAgentNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.USER_AGENT, "whatever"));
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("whatever", header.getValue());
    }

    @Test
    public void testRequestUserAgentMissingUserAgent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestUserAgent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertNull(header);
    }

    @Test
    public void testRequestUserAgentInvalidInput() throws Exception {
        final HttpRequestInterceptor interceptor = RequestUserAgent.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    public void testResponseConnControlNoEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityContentLength() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityUnknownContentLengthExplicitKeepAlive() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("keep-alive", header.getValue());
    }

    @Test
    public void testResponseConnControlEntityChunked() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, true));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    public void testResponseConnControlEntityUnknownContentLengthHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequest() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("keep-alive", header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequest2() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    public void testResponseConnControl10Client11Response() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlStatusCode() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
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
            Assertions.assertNotNull(header);
            Assertions.assertEquals("close", header.getValue());
        }

    }

    @Test
    public void testResponseConnControlExplicitClose() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.setHeader(HttpHeaders.CONNECTION, "close");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlClientRequestMixUp() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "blah, keep-alive, close"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    public void testResponseConnControlUpgrade() throws Exception {
        final HttpContext context = new BasicHttpContext(null);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader(HttpHeaders.UPGRADE, "HTTP/2");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("upgrade", header.getValue());
    }

    @Test
    public void testResponseConnControlHostInvalidInput() throws Exception {
        final ResponseConnControl interceptor = new ResponseConnControl();
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(response, null, null));
    }

    @Test
    public void testResponseContentNoEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("0", header.getValue());
    }

    @Test
    public void testResponseContentStatusNoContent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(HttpStatus.SC_NO_CONTENT);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
    }

    @Test
    public void testResponseContentStatusNotModified() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(HttpStatus.SC_NOT_MODIFIED);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
    }

    @Test
    public void testResponseContentEntityChunked() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, true));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("chunked", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityContentLenghtDelimited() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, 10, null));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("10", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityUnknownContentLength() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("chunked", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(h2);
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
        Assertions.assertNull(h1);
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(h2);
    }

    @Test
    public void testResponseContentEntityNoContentTypeAndEncoding() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assertions.assertNull(h1);
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        Assertions.assertNull(h2);
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
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("whatever", h1.getValue());
        final Header h2 = response.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        Assertions.assertNotNull(h2);
        Assertions.assertEquals("whatever", h2.getValue());
    }

    @Test
    public void testResponseContentInvalidInput() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    public void testResponseContentInvalidResponseState() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response1.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(response1, response1.getEntity(), context));
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response2.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "stuff"));
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(response2, response2.getEntity(), context));
    }

    @Test
    public void testResponseContentOverwriteHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        response.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        interceptor.process(response, response.getEntity(), context);
        Assertions.assertEquals("0", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
    }

    @Test
    public void testResponseContentAddHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        interceptor.process(response, response.getEntity(), context);
        Assertions.assertEquals("0", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
        Assertions.assertNull(response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
    }

    @Test
    public void testResponseContentEntityWithTrailers() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(HttpEntities.create("whatever", StandardCharsets.US_ASCII,
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header1 = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        Assertions.assertNotNull(header1);
        final Header header2 = response.getFirstHeader(HttpHeaders.TRAILER);
        Assertions.assertNotNull(header2);
        Assertions.assertEquals("h1, h2", header2.getValue());
    }

    @Test
    public void testResponseDateGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNotNull(h1);
        interceptor.process(response, response.getEntity(), context);
        final Header h2 = response.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNotNull(h2);
    }

    @Test
    public void testResponseDateNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(199);
        final ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNull(h1);
    }

    @Test
    public void testResponseDateInvalidInput() throws Exception {
        final ResponseDate interceptor = new ResponseDate();
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    public void testRequestDateGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("stuff"));

        final HttpRequestInterceptor interceptor = RequestDate.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNotNull(h1);
        interceptor.process(request, request.getEntity(), context);
        final Header h2 = request.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNotNull(h2);
    }

    @Test
    public void testRequestDateNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        final HttpRequestInterceptor interceptor = RequestDate.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNull(h1);
    }

    @Test
    public void testRequestDateInvalidInput() throws Exception {
        final HttpRequestInterceptor interceptor = RequestDate.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    public void testResponseServerGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("some server", h1.getValue());
    }

    @Test
    public void testResponseServerNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HttpHeaders.SERVER, "whatever"));
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("whatever", h1.getValue());
    }

    @Test
    public void testResponseServerMissing() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assertions.assertNull(h1);
    }

    @Test
    public void testResponseServerInvalidInput() throws Exception {
        final ResponseServer interceptor = new ResponseServer();
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    public void testRequestHttp10HostHeaderAbsent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setVersion(HttpVersion.HTTP_1_0);
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test
    public void testRequestHttpHostHeader() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.HOST, "host:8888");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(new URIAuthority("host", 8888), request.getAuthority());
    }

    @Test
    public void testRequestHttpHostHeaderNoPort() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.HOST, "host");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(new URIAuthority("host"), request.getAuthority());
    }

    @Test
    public void testRequestHttp11HostHeaderPresent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test
    public void testRequestHttp11HostHeaderAbsent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final RequestValidateHost interceptor = new RequestValidateHost();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestHttp11MultipleHostHeaders() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(HttpHeaders.HOST, "blah");
        request.addHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestAbsoluteRequestURITakesPrecedenceOverHostHeader() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "https://somehost/blah?huh");
        request.setHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(new URIAuthority("somehost"), request.getAuthority());
    }

    @Test
    public void testRequestConformance() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("http");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test
    public void testRequestConformanceSchemeMissing() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestConformancePathMissing() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("http");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestConformanceHostMissing() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("http");
        request.setAuthority(new URIAuthority("", -1));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testRequestConformanceHttps() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setAttribute(HttpCoreContext.SSL_SESSION, Mockito.mock(SSLSession.class));
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test
    public void testRequestConformanceHttpsInsecureConnection() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        context.setAttribute(HttpCoreContext.SSL_SESSION, null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(MisdirectedRequestException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    public void testResponseConformanceNoContent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        final ResponseConformance interceptor = new ResponseConformance();
        interceptor.process(response, response.getEntity(), context);
    }

    @Test
    public void testResponseConformanceNotModified() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        final ResponseConformance interceptor = new ResponseConformance();
        interceptor.process(response, response.getEntity(), context);
    }

    @Test
    public void testResponseConformanceNoContentWithEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        response.setEntity(new StringEntity("stuff"));
        final ResponseConformance interceptor = new ResponseConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
            interceptor.process(response, response.getEntity(), context));
    }

    @Test
    public void testResponseConformanceNotModifiedWithEntity() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        response.setEntity(new StringEntity("stuff"));
        final ResponseConformance interceptor = new ResponseConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
            interceptor.process(response, response.getEntity(), context));
    }

}
