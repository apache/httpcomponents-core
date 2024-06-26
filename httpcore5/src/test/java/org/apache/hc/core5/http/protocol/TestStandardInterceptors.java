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

class TestStandardInterceptors {

    @Test
    void testRequestConnControlGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("keep-alive", header.getValue());
    }

    @Test
    void testRequestConnControlConnectMethod() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.CONNECT, "/");
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestConnControlCustom() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestConnControlUpgrade() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(HttpHeaders.UPGRADE, "HTTP/2");
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("upgrade", header.getValue());
    }

    @Test
    void testRequestConnControlInvalidInput() {
        final HttpRequestInterceptor interceptor = RequestConnControl.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    void testRequestContentProtocolException() {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentNullEntity() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(header);
        Assertions.assertEquals(0, Integer.parseInt(header.getValue()));
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
   }

    @Test
    void testRequestContentNullEntityNonEnclosingMethod() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
    }
    @Test
    void testRequestContentEntityContentLengthDelimitedHTTP11() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentEntityChunkedHTTP11() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentEntityUnknownLengthHTTP11() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentEntityChunkedHTTP10() {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII, true));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestContentTypeAndEncoding() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentNullTypeAndEncoding() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, null));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_TYPE));
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    @Test
    void testRequestContentEntityUnknownLengthHTTP10() {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, -1, null));

        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
   }

    @Test
    void testRequestContentInvalidInput() {
        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    void testRequestContentIgnoreNonenclosingRequests() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(1, request.getHeaders().length);
    }

    @Test
    void testRequestContentOverwriteHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentAddHeaders() throws Exception {
        final RequestContent interceptor = new RequestContent(true);
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentEntityWithTrailers() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestContentTraceWithEntity() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.TRACE, "/");
        request.setEntity(new StringEntity("stuff"));
        final HttpRequestInterceptor interceptor = RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestExpectContinueGenerated() throws Exception {
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
    void testRequestExpectContinueHTTP10() throws Exception {
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
    void testRequestExpectContinueZeroContent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(new StringEntity("", StandardCharsets.US_ASCII));
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestExpectContinueInvalidInput() {
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        final RequestExpectContinue interceptor = RequestExpectContinue.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(0, request.getHeaders().length);
    }

    @Test
    void testRequestTargetHostGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    void testRequestTargetHostNotGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestTargetHostMissingHostHTTP10() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestTargetHostMissingHostHTTP11() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestTargetHostInvalidInput() {
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(new BasicClassicHttpRequest(Method.GET, "/"), null, null));
    }

    @Test
    void testRequestTargetHostConnectHttp11() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.CONNECT, "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("somehost:8080", header.getValue());
    }

    @Test
    void testRequestTargetHostConnectHttp10() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.CONNECT, "/");
        request.setAuthority(new URIAuthority("somehost", 8080));
        final HttpRequestInterceptor interceptor = RequestTargetHost.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.HOST);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestUserAgentGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("some agent", header.getValue());
    }

    @Test
    void testRequestUserAgentNotGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.USER_AGENT, "whatever"));
        final RequestUserAgent interceptor = new RequestUserAgent("some agent");
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("whatever", header.getValue());
    }

    @Test
    void testRequestUserAgentMissingUserAgent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final HttpRequestInterceptor interceptor = RequestUserAgent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestUserAgentInvalidInput() {
        final HttpRequestInterceptor interceptor = RequestUserAgent.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    void testResponseConnControlNoEntity() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    void testResponseConnControlEntityContentLength() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    void testResponseConnControlEntityUnknownContentLengthExplicitKeepAlive() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setRequest(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("keep-alive", header.getValue());
    }

    @Test
    void testResponseConnControlEntityChunked() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null, true));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    void testResponseConnControlEntityUnknownContentLengthHTTP10() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setRequest(request);

        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new BasicHttpEntity(EmptyInputStream.INSTANCE, null));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    void testResponseConnControlClientRequest() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setRequest(request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("keep-alive", header.getValue());
    }

    @Test
    void testResponseConnControlClientRequest2() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setRequest(request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNull(header);
    }

    @Test
    void testResponseConnControl10Client11Response() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        context.setRequest(request);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new StringEntity("whatever"));
        final ResponseConnControl interceptor = new ResponseConnControl();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    void testResponseConnControlStatusCode() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setRequest(request);

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
    void testResponseConnControlExplicitClose() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        context.setRequest(request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.setHeader(HttpHeaders.CONNECTION, "close");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    void testResponseConnControlClientRequestMixUp() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, "blah, keep-alive, close"));
        context.setRequest(request);

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("close", header.getValue());
    }

    @Test
    void testResponseConnControlUpgrade() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();

        final ResponseConnControl interceptor = new ResponseConnControl();

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader(HttpHeaders.UPGRADE, "HTTP/2");
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONNECTION);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("upgrade", header.getValue());
    }

    @Test
    void testResponseConnControlHostInvalidInput() {
        final ResponseConnControl interceptor = new ResponseConnControl();
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(response, null, null));
    }

    @Test
    void testResponseContentNoEntity() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("0", header.getValue());
    }

    @Test
    void testResponseContentStatusNoContent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(HttpStatus.SC_NO_CONTENT);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
    }

    @Test
    void testResponseContentStatusNotModified() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(HttpStatus.SC_NOT_MODIFIED);
        final ResponseContent interceptor = new ResponseContent();
        interceptor.process(response, response.getEntity(), context);
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
    }

    @Test
    void testResponseContentEntityChunked() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseContentEntityContentLenghtDelimited() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseContentEntityUnknownContentLength() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseContentEntityChunkedHTTP10() throws Exception {
    final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseContentEntityNoContentTypeAndEncoding() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseContentEntityContentTypeAndEncoding() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseContentInvalidInput() {
        final ResponseContent interceptor = new ResponseContent();
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    void testResponseContentInvalidResponseState() {
        final ResponseContent interceptor = new ResponseContent();
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseContentOverwriteHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        response.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        interceptor.process(response, response.getEntity(), context);
        Assertions.assertEquals("0", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
    }

    @Test
    void testResponseContentAddHeaders() throws Exception {
        final ResponseContent interceptor = new ResponseContent(true);
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        interceptor.process(response, response.getEntity(), context);
        Assertions.assertEquals("0", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
        Assertions.assertNull(response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
    }

    @Test
    void testResponseContentEntityWithTrailers() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseDateGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testResponseDateNotGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setCode(199);
        final ResponseDate interceptor = new ResponseDate();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNull(h1);
    }

    @Test
    void testResponseDateInvalidInput() {
        final ResponseDate interceptor = new ResponseDate();
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    void testRequestDateGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
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
    void testRequestDateNotGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        final HttpRequestInterceptor interceptor = RequestDate.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header h1 = request.getFirstHeader(HttpHeaders.DATE);
        Assertions.assertNull(h1);
    }

    @Test
    void testRequestDateInvalidInput() {
        final HttpRequestInterceptor interceptor = RequestDate.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, null));
    }

    @Test
    void testResponseServerGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("some server", h1.getValue());
    }

    @Test
    void testResponseServerNotGenerated() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.addHeader(new BasicHeader(HttpHeaders.SERVER, "whatever"));
        final ResponseServer interceptor = new ResponseServer("some server");
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assertions.assertNotNull(h1);
        Assertions.assertEquals("whatever", h1.getValue());
    }

    @Test
    void testResponseServerMissing() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        final ResponseServer interceptor = new ResponseServer();
        interceptor.process(response, response.getEntity(), context);
        final Header h1 = response.getFirstHeader(HttpHeaders.SERVER);
        Assertions.assertNull(h1);
    }

    @Test
    void testResponseServerInvalidInput() {
        final ResponseServer interceptor = new ResponseServer();
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    void testRequestHttp10HostHeaderAbsent() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setVersion(HttpVersion.HTTP_1_0);
        final RequestValidateHost interceptor = new RequestValidateHost();
        Assertions.assertDoesNotThrow(() -> interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestHttpHostHeader() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.HOST, "host:8888");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(new URIAuthority("host", 8888), request.getAuthority());
    }

    @Test
    void testRequestHttpHostHeaderNoPort() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setHeader(HttpHeaders.HOST, "host");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(new URIAuthority("host"), request.getAuthority());
    }

    @Test
    void testRequestHttp11HostHeaderPresent() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
    }

    @Test
    void testRequestHttp11HostHeaderAbsent() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final RequestValidateHost interceptor = new RequestValidateHost();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestHttp11MultipleHostHeaders() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.addHeader(HttpHeaders.HOST, "blah");
        request.addHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestAbsoluteRequestURITakesPrecedenceOverHostHeader() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "https://somehost/blah?huh");
        request.setHeader(HttpHeaders.HOST, "blah");
        final RequestValidateHost interceptor = new RequestValidateHost();
        interceptor.process(request, request.getEntity(), context);
        Assertions.assertEquals(new URIAuthority("somehost"), request.getAuthority());
    }

    @Test
    void testRequestConformance() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("http");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertDoesNotThrow(() -> interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestConformanceSchemeMissing() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestConformancePathMissing() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("http");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestConformanceHostMissing() {
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("http");
        request.setAuthority(new URIAuthority("", -1));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestConformanceHttps() {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setSSLSession(Mockito.mock(SSLSession.class));
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertDoesNotThrow(() -> interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testRequestConformanceHttpsInsecureConnection() {
        final HttpCoreContext context = HttpCoreContext.create();
        context.setSSLSession(null);
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("somehost", 8888));
        request.setPath("/path");
        final RequestConformance interceptor = new RequestConformance();
        Assertions.assertThrows(MisdirectedRequestException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testResponseConformanceNoContent() {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        final ResponseConformance interceptor = new ResponseConformance();
        Assertions.assertDoesNotThrow(() -> interceptor.process(response, response.getEntity(), context));
    }

    @Test
    void testResponseConformanceNotModified() {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        final ResponseConformance interceptor = new ResponseConformance();
        Assertions.assertDoesNotThrow(() -> interceptor.process(response, response.getEntity(), context));
    }

    @Test
    void testResponseConformanceNoContentWithEntity() {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        response.setEntity(new StringEntity("stuff"));
        final ResponseConformance interceptor = new ResponseConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
            interceptor.process(response, response.getEntity(), context));
    }

    @Test
    void testResponseConformanceNotModifiedWithEntity() {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");
        response.setEntity(new StringEntity("stuff"));
        final ResponseConformance interceptor = new ResponseConformance();
        Assertions.assertThrows(ProtocolException.class, () ->
            interceptor.process(response, response.getEntity(), context));
    }

}
