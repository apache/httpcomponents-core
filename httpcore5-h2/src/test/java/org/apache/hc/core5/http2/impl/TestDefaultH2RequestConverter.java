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
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
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
        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getMethod()).isEqualTo("GET");
        Assertions.assertThat(request.getScheme()).isEqualTo("http");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("www.example.com"));
        Assertions.assertThat(request.getPath()).isEqualTo("/");
        final Header[] allHeaders = request.getHeaders();
        Assertions.assertThat(allHeaders.length).isEqualTo(1);
        Assertions.assertThat(allHeaders[0].getName()).isEqualTo("custom123");
        Assertions.assertThat(allHeaders[0].getValue()).isEqualTo("value");
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header name ':Path' is invalid (header name contains uppercase characters)").isInstanceOf(HttpException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Invalid sequence of headers (pseudo-headers must precede message headers)").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsMissingMethod() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Mandatory request header ':method' not found").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsMissingScheme() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Mandatory request header ':scheme' not found").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsMissingPath() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Mandatory request header ':path' not found").isInstanceOf(HttpException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Unsupported request header ':custom'").isInstanceOf(HttpException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Multiple ':method' request headers are illegal").isInstanceOf(HttpException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Multiple ':scheme' request headers are illegal").isInstanceOf(HttpException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Multiple ':path' request headers are illegal").isInstanceOf(HttpException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header ':authority' is mandatory for CONNECT request").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsConnectPresentScheme() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header ':scheme' must not be set for CONNECT request").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsConnectPresentPath() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header ':path' must not be set for CONNECT request").isInstanceOf(HttpException.class);
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

        Assertions.assertThat(request.getMethod()).isEqualTo("CONNECT");
        Assertions.assertThat(request.getScheme()).isEqualTo("https");
        Assertions.assertThat(request.getPath()).isEqualTo("/chat");
        Assertions.assertThat(request.getFirstHeader(":protocol").getValue()).isEqualTo("websocket");
    }

    @Test
    void testConvertFromFieldsExtendedConnectMissingScheme() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":protocol", "websocket"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/chat"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header ':scheme' is mandatory for extended CONNECT").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromFieldsExtendedConnectMissingPath() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":protocol", "websocket"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header ':path' is mandatory for extended CONNECT").isInstanceOf(HttpException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .withFailMessage("Header ':protocol' must not be set for GET request").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageBasic() throws Exception {

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assertions.assertThat(headers).isNotNull();
        Assertions.assertThat(headers.size()).isEqualTo(5);
        final Header header1 = headers.get(0);
        Assertions.assertThat(header1.getName()).isEqualTo(":method");
        Assertions.assertThat(header1.getValue()).isEqualTo("GET");
        final Header header2 = headers.get(1);
        Assertions.assertThat(header2.getName()).isEqualTo(":scheme");
        Assertions.assertThat(header2.getValue()).isEqualTo("http");
        final Header header3 = headers.get(2);
        Assertions.assertThat(header3.getName()).isEqualTo(":authority");
        Assertions.assertThat(header3.getValue()).isEqualTo("host");
        final Header header4 = headers.get(3);
        Assertions.assertThat(header4.getName()).isEqualTo(":path");
        Assertions.assertThat(header4.getValue()).isEqualTo("/");
        final Header header5 = headers.get(4);
        Assertions.assertThat(header5.getName()).isEqualTo("custom123");
        Assertions.assertThat(header5.getValue()).isEqualTo("Value");
    }

    @Test
    void testConvertFromMessageMissingScheme() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setScheme(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("Request scheme is not set").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageMissingPath() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setPath(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("Request path is not set").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageConnect() throws Exception {

        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host:80"), null);
        request.addHeader("custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assertions.assertThat(headers).isNotNull();
        Assertions.assertThat(headers.size()).isEqualTo(3);
        final Header header1 = headers.get(0);
        Assertions.assertThat(header1.getName()).isEqualTo(":method");
        Assertions.assertThat(header1.getValue()).isEqualTo("CONNECT");
        final Header header2 = headers.get(1);
        Assertions.assertThat(header2.getName()).isEqualTo(":authority");
        Assertions.assertThat(header2.getValue()).isEqualTo("host:80");
        final Header header3 = headers.get(2);
        Assertions.assertThat(header3.getName()).isEqualTo("custom123");
        Assertions.assertThat(header3.getValue()).isEqualTo("Value");
    }

    @Test
    void testConvertFromMessageConnectMissingAuthority() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", null, null);
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("CONNECT request authority is not set").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageConnectWithPath() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", "/");
        request.setAuthority(new URIAuthority("host"));
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("CONNECT request path must be null").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageExtendedConnect() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host"), "/chat");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("host"));
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assertions.assertThat(headers.stream().anyMatch(h -> ":protocol".equals(h.getName()))).isTrue();
    }

    @Test
    void testConvertFromMessageExtendedConnectMissingScheme() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host"), "/chat");
        request.setAuthority(new URIAuthority("host"));
        request.setScheme(null);
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("CONNECT request scheme is not set").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageExtendedConnectMissingPath() {
        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host"), null);
        request.setAuthority(new URIAuthority("host"));
        request.setScheme("https");
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("CONNECT request path is not set").isInstanceOf(HttpException.class);
    }

    @Test
    void testConvertFromMessageProtocolWithNonConnect() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":protocol", "websocket");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("Header name ':protocol' is invalid").isInstanceOf(HttpException.class);
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
        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getMethod()).isEqualTo("GET");
        Assertions.assertThat(request.getScheme()).isEqualTo("http");
        Assertions.assertThat(request.getAuthority()).isEqualTo(new URIAuthority("www.example.com"));
        Assertions.assertThat(request.getPath()).isEqualTo("/");
        final Header[] allHeaders = request.getHeaders();
        Assertions.assertThat(allHeaders.length).isEqualTo(1);
        Assertions.assertThat(allHeaders[0].getName()).isEqualTo("te");
        Assertions.assertThat(allHeaders[0].getValue()).isEqualTo("trailers");
    }

    @Test
    void testConvertFromMessageInvalidHeader() {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":custom", "stuff");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assertions.assertThatThrownBy(() ->
                        converter.convert(request))
                .withFailMessage("Header name ':custom' is invalid").isInstanceOf(HttpException.class);
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

        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("/");

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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .isInstanceOf(ProtocolException.class);
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .isInstanceOf(ProtocolException.class);
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
        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("*");
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
        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("/");
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
        Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void testConvertFromFieldsMultipleAuthority() {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":authority", "www2.example.com"),
                new BasicHeader(":path", "/"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();

        final ProtocolException ex = Assertions.assertThatThrownBy(() ->
                        converter.convert(headers))
                .isInstanceOf(ProtocolException.class).asInstanceOf(InstanceOfAssertFactories.type(ProtocolException.class)).actual();

        Assertions.assertThat(ex.getMessage().contains("Multiple ':authority' request headers are illegal")).withFailMessage(ex::getMessage).isTrue();
    }


}

