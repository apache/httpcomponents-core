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

package org.apache.hc.core5.http2.impl;

import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestDefaultH2RequestConverter {

    @Test
    public void testConvertFromFieldsBasic() throws Exception {

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom123", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = converter.convert(headers);
        Assertions.assertNotNull(request);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("http", request.getScheme());
        Assertions.assertEquals(new URIAuthority("www.example.com"), request.getAuthority());
        Assertions.assertEquals("/", request.getPath());
        final Header[] allHeaders = request.getHeaders();
        Assertions.assertEquals(1, allHeaders.length);
        Assertions.assertEquals("custom123", allHeaders[0].getName());
        Assertions.assertEquals("value", allHeaders[0].getValue());
    }

    @Test
    public void testConvertFromFieldsUpperCaseHeaderName() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":Path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header name ':Path' is invalid (header name contains uppercase characters)");
    }

    @Test
    public void testConvertFromFieldsConnectionHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("connection", "keep-alive"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header 'connection: keep-alive' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsPseudoHeaderSequence() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader("custom", "value"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Invalid sequence of headers (pseudo-headers must precede message headers)");
    }

    @Test
    public void testConvertFromFieldsMissingMethod() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Mandatory request header ':method' not found");
    }

    @Test
    public void testConvertFromFieldsMissingScheme() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Mandatory request header ':scheme' not found");
    }

    @Test
    public void testConvertFromFieldsMissingPath() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Mandatory request header ':path' not found");
    }

    @Test
    public void testConvertFromFieldsUnknownPseudoHeader() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Unsupported request header ':custom'");
    }

    @Test
    public void testConvertFromFieldsMultipleMethod() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Multiple ':method' request headers are illegal");
    }

    @Test
    public void testConvertFromFieldsMultipleScheme() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Multiple ':scheme' request headers are illegal");
    }

    @Test
    public void testConvertFromFieldsMultiplePath() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Multiple ':path' request headers are illegal");
    }

    @Test
    public void testConvertFromFieldsConnect() throws Exception {

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsConnectMissingAuthority() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header ':authority' is mandatory for CONNECT request");
    }

    @Test
    public void testConvertFromFieldsConnectPresentScheme() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header ':scheme' must not be set for CONNECT request");
    }

    @Test
    public void testConvertFromFieldsConnectPresentPath() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header ':path' must not be set for CONNECT request");
    }

    @Test
    public void testConvertFromMessageBasic() throws Exception {

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assertions.assertNotNull(headers);
        Assertions.assertEquals(5, headers.size());
        final Header header1 = headers.get(0);
        Assertions.assertEquals(":method", header1.getName());
        Assertions.assertEquals("GET", header1.getValue());
        final Header header2 = headers.get(1);
        Assertions.assertEquals(":scheme", header2.getName());
        Assertions.assertEquals("http", header2.getValue());
        final Header header3 = headers.get(2);
        Assertions.assertEquals(":authority", header3.getName());
        Assertions.assertEquals("host", header3.getValue());
        final Header header4 = headers.get(3);
        Assertions.assertEquals(":path", header4.getName());
        Assertions.assertEquals("/", header4.getValue());
        final Header header5 = headers.get(4);
        Assertions.assertEquals("custom123", header5.getName());
        Assertions.assertEquals("Value", header5.getValue());
    }

    @Test
    public void testConvertFromMessageMissingScheme() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setScheme(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request), "Request scheme is not set");
    }

    @Test
    public void testConvertFromMessageMissingPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setPath(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request), "Request path is not set");
    }

    @Test
    public void testConvertFromMessageConnect() throws Exception {

        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host:80"), null);
        request.addHeader("custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assertions.assertNotNull(headers);
        Assertions.assertEquals(3, headers.size());
        final Header header1 = headers.get(0);
        Assertions.assertEquals(":method", header1.getName());
        Assertions.assertEquals("CONNECT", header1.getValue());
        final Header header2 = headers.get(1);
        Assertions.assertEquals(":authority", header2.getName());
        Assertions.assertEquals("host:80", header2.getValue());
        final Header header3 = headers.get(2);
        Assertions.assertEquals("custom123", header3.getName());
        Assertions.assertEquals("Value", header3.getValue());
    }

    @Test
    public void testConvertFromMessageConnectMissingAuthority() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", null, null);
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "CONNECT request authority is not set");
    }

    @Test
    public void testConvertFromMessageConnectWithPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", "/");
        request.setAuthority(new URIAuthority("host"));
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "CONNECT request path must be null");
    }

    @Test
    public void testConvertFromMessageConnectionHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Connection", "Keep-Alive");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsKeepAliveHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Keep-Alive", "timeout=5, max=1000");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header 'Keep-Alive: timeout=5, max=1000' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsProxyConnectionHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Proxy-Connection", "keep-alive");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header 'Proxy-Connection: Keep-Alive' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsTransferEncodingHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Transfer-Encoding", "gzip");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header 'Transfer-Encoding: gzip' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsHostHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Host", "host");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header 'Host: host' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsUpgradeHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Upgrade", "example/1, foo/2");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header 'Upgrade: example/1, foo/2' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsTEHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("TE", "gzip");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header 'TE: gzip' is illegal for HTTP/2 messages");
    }

    @Test
    public void testConvertFromFieldsTETrailerHeader() throws Exception {

        final List<Header> headers = Arrays.asList(
            new BasicHeader(":method", "GET"),
            new BasicHeader(":scheme", "http"),
            new BasicHeader(":authority", "www.example.com"),
            new BasicHeader(":path", "/"),
            new BasicHeader("te", "trailers"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = converter.convert(headers);
        Assertions.assertNotNull(request);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("http", request.getScheme());
        Assertions.assertEquals(new URIAuthority("www.example.com"), request.getAuthority());
        Assertions.assertEquals("/", request.getPath());
        final Header[] allHeaders = request.getHeaders();
        Assertions.assertEquals(1, allHeaders.length);
        Assertions.assertEquals("te", allHeaders[0].getName());
        Assertions.assertEquals("trailers", allHeaders[0].getValue());
    }

    @Test
    public void testConvertFromMessageInvalidHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":custom", "stuff");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header name ':custom' is invalid");
    }


    @Test
    public void testValidPath() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("te", "trailers")
        );

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = converter.convert(headers);

        Assertions.assertNotNull(request);
        Assertions.assertEquals("/", request.getPath());

    }

    @Test
    public void testInvalidPathEmpty() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", ""),
                new BasicHeader("te", "trailers")
        );

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(ProtocolException.class, () -> converter.convert(headers));
    }

    @Test
    public void testInvalidPathNoSlash() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "noSlash"),
                new BasicHeader("te", "trailers")
        );

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(ProtocolException.class, () -> converter.convert(headers));
    }

    @Test
    public void testValidOptionsAsterisk() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "OPTIONS"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "*"),
                new BasicHeader("te", "trailers")
        );

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = converter.convert(headers);
        Assertions.assertNotNull(request);
        Assertions.assertEquals("*", request.getPath());
    }

    @Test
    public void testValidOptionsWithRootPath() throws HttpException {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "OPTIONS"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("te", "trailers")
        );

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = converter.convert(headers);
        Assertions.assertNotNull(request);
        Assertions.assertEquals("/", request.getPath());
    }

    @Test
    public void testInvalidOptionsNeitherAsteriskNorRoot() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "OPTIONS"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "invalid"),
                new BasicHeader("te", "trailers")
        );

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(ProtocolException.class, () -> converter.convert(headers));
    }


}

