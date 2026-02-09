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

class TestDefaultH2RequestConverter {

    @Test
    void testConvertFromFieldsBasic() throws Exception {

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
    void testConvertFromFieldsUpperCaseHeaderName() {
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
    void testConvertFromFieldsPseudoHeaderSequence() {
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
    void testConvertFromFieldsMissingMethod() {
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
    void testConvertFromFieldsMissingScheme() {
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
    void testConvertFromFieldsMissingPath() {
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
    void testConvertFromFieldsUnknownPseudoHeader() {
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
    void testConvertFromFieldsMultipleMethod() {
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
    void testConvertFromFieldsMultipleScheme() {
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
    void testConvertFromFieldsMultiplePath() {
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
    void testConvertFromFieldsConnect() throws Exception {

        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    void testConvertFromFieldsConnectMissingAuthority() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header ':authority' is mandatory for CONNECT request");
    }

    @Test
    void testConvertFromFieldsConnectPresentScheme() {
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
    void testConvertFromFieldsConnectPresentPath() {
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
    void testConvertFromFieldsExtendedConnect() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":protocol", "websocket"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/chat"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = converter.convert(headers);

        Assertions.assertEquals("CONNECT", request.getMethod());
        Assertions.assertEquals("https", request.getScheme());
        Assertions.assertEquals("/chat", request.getPath());
        Assertions.assertEquals("websocket", request.getFirstHeader(":protocol").getValue());
    }

    @Test
    void testConvertFromFieldsExtendedConnectMissingScheme() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":protocol", "websocket"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/chat"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header ':scheme' is mandatory for extended CONNECT");
    }

    @Test
    void testConvertFromFieldsExtendedConnectMissingPath() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":protocol", "websocket"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header ':path' is mandatory for extended CONNECT");
    }

    @Test
    void testConvertFromFieldsProtocolWithNonConnect() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":protocol", "websocket"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(headers),
                "Header ':protocol' must not be set for GET request");
    }

    @Test
    void testConvertFromMessageBasic() throws Exception {

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
    void testConvertFromMessageMissingScheme() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setScheme(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request), "Request scheme is not set");
    }

    @Test
    void testConvertFromMessageMissingPath() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setPath(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request), "Request path is not set");
    }

    @Test
    void testConvertFromMessageConnect() throws Exception {

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
    void testConvertFromMessageConnectMissingAuthority() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", null, null);
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "CONNECT request authority is not set");
    }

    @Test
    void testConvertFromMessageConnectWithPath() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", "/");
        request.setAuthority(new URIAuthority("host"));
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "CONNECT request path must be null");
    }

    @Test
    void testConvertFromMessageExtendedConnect() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host"), "/chat");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("host"));
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assertions.assertTrue(headers.stream().anyMatch(h -> ":protocol".equals(h.getName())));
    }

    @Test
    void testConvertFromMessageExtendedConnectMissingScheme() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host"), "/chat");
        request.setAuthority(new URIAuthority("host"));
        request.setScheme(null);
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "CONNECT request scheme is not set");
    }

    @Test
    void testConvertFromMessageExtendedConnectMissingPath() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host"), null);
        request.setAuthority(new URIAuthority("host"));
        request.setScheme("https");
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "CONNECT request path is not set");
    }

    @Test
    void testConvertFromMessageProtocolWithNonConnect() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header name ':protocol' is invalid");
    }

    @Test
    void testConvertFromFieldsTETrailerHeader() throws Exception {

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
    void testConvertFromMessageInvalidHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":custom", "stuff");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThrows(HttpException.class, () -> converter.convert(request),
                "Header name ':custom' is invalid");
    }


    @Test
    void testValidPath() throws Exception {
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
    void testInvalidPathEmpty() {
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
    void testInvalidPathNoSlash() {
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
    void testValidOptionsAsterisk() throws Exception {
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
    void testValidOptionsWithRootPath() throws HttpException {
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
    void testInvalidOptionsNeitherAsteriskNorRoot() {
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

