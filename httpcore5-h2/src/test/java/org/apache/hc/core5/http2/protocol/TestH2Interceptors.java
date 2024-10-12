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

package org.apache.hc.core5.http2.protocol;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestH2Interceptors {

    /**
     * HTTP context.
     */
    private HttpCoreContext context;


    @BeforeEach
    void setUp() {
        context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_2);
    }

    @Test
    void testH2RequestContentProtocolException() {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.TRACE, "/");
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "chunked"));
        request.setEntity(new StringEntity("whatever", StandardCharsets.US_ASCII));
        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));

    }

    @Test
    void testH2RequestContentNullEntity() throws Exception {

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        Assertions.assertNull(header);
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.TRANSFER_ENCODING));
    }


    @Test
    void testH2RequestContentValid() throws Exception {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        // Create a mock entity with ContentType, ContentEncoding, and TrailerNames
        request.setEntity(HttpEntities.create("whatever", StandardCharsets.US_ASCII,
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);

        // Assertions to validate headers
        final Header header1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assertions.assertNotNull(header1);
        final Header header2 = request.getFirstHeader(HttpHeaders.TRAILER);
        Assertions.assertNotNull(header2);
        Assertions.assertEquals("h1, h2", header2.getValue());
    }

    @Test
    void testH2RequestContentOptionMethodNullContentTypeProtocolException() {
        final H2RequestContent interceptor = new H2RequestContent();
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.OPTIONS, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        request.setEntity(new StringEntity(""));
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testH2RequestContentOptionMethodInvalidContentTypeProtocolException() {
        final H2RequestContent interceptor = new H2RequestContent();
        final HttpCoreContext context = HttpCoreContext.create();
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.OPTIONS, "/");
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_LENGTH, "10"));
        request.addHeader(new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "whatever"));
        request.setEntity(new StringEntity(""));
        request.addHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=invalid_charset_name!"));
        Assertions.assertThrows(ProtocolException.class, () ->
                interceptor.process(request, request.getEntity(), context));
    }

    @Test
    void testH2RequestContentValidOptionsMethod() throws Exception {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.OPTIONS, "/");

        // Create a mock entity with ContentType, ContentEncoding, and TrailerNames
        request.setEntity(HttpEntities.create("whatever", StandardCharsets.US_ASCII,
                new BasicHeader("h1", "this"), new BasicHeader("h1", "that"), new BasicHeader("h2", "this and that")));

        final HttpRequestInterceptor interceptor = H2RequestContent.INSTANCE;
        interceptor.process(request, request.getEntity(), context);

        // Assertions to validate headers
        final Header header1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Assertions.assertNotNull(header1);
    }

    @Test
    void testH2RequestConformanceConnectionHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Connection", "Keep-Alive");

        final H2RequestConformance interceptor = new H2RequestConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(request, null, context),
                "Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2RequestConformanceKeepAliveHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Keep-Alive", "timeout=5, max=1000");

        final H2RequestConformance interceptor = new H2RequestConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(request, null, context),
                "Header 'Keep-Alive: timeout=5, max=1000' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2RequestConformanceProxyConnectionHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Proxy-Connection", "keep-alive");

        final H2RequestConformance interceptor = new H2RequestConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(request, null, context),
                "Header 'Proxy-Connection: Keep-Alive' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2RequestConformanceTransferEncodingHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Transfer-Encoding", "gzip");

        final H2RequestConformance interceptor = new H2RequestConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(request, null, context),
                "Header 'Transfer-Encoding: gzip' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2RequestConformanceHostHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Host", "host");

        final H2RequestConformance interceptor = new H2RequestConformance();
        Assertions.assertDoesNotThrow(() -> interceptor.process(request, null, context),
                "Header 'Host: host' is permissible for HTTP/2 messages");
    }

    @Test
    void testH2RequestConformanceUpgradeHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Upgrade", "example/1, foo/2");

        final H2RequestConformance interceptor = new H2RequestConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(request, null, context),
                "Header 'Upgrade: example/1, foo/2' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2RequestConformanceTEHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("TE", "gzip");

        final H2RequestConformance interceptor = new H2RequestConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(request, null, context),
                "Header 'TE: gzip' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2ResponseConformanceConnectionHeader() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader("Connection", "Keep-Alive");

        final H2ResponseConformance interceptor = new H2ResponseConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(response, null, context),
                "Header 'connection: keep-alive' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2ResponseConformanceKeepAliveHeader() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader("keep-alive", "timeout=5, max=1000");

        final H2ResponseConformance interceptor = new H2ResponseConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(response, null, context),
                "Header 'keep-alive: timeout=5, max=1000' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2ResponseConformanceTransferEncodingHeader() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader("transfer-encoding", "gzip");

        final H2ResponseConformance interceptor = new H2ResponseConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(response, null, context),
                "Header 'transfer-encoding: gzip' is illegal for HTTP/2 messages");
    }

    @Test
    void testH2ResponseConformanceUpgradeHeader() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader("upgrade", "example/1, foo/2");

        final H2ResponseConformance interceptor = new H2ResponseConformance();
        Assertions.assertThrows(HttpException.class, () -> interceptor.process(response, null, context),
                "Header 'upgrade: example/1, foo/2' is illegal for HTTP/2 messages");
    }

}
