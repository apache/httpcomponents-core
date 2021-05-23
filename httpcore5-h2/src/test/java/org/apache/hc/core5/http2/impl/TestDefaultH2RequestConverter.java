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
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertNotNull(request);
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("http", request.getScheme());
        Assert.assertEquals(new URIAuthority("www.example.com"), request.getAuthority());
        Assert.assertEquals("/", request.getPath());
        final Header[] allHeaders = request.getHeaders();
        Assert.assertEquals(1, allHeaders.length);
        Assert.assertEquals("custom123", allHeaders[0].getName());
        Assert.assertEquals("value", allHeaders[0].getValue());
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

        Assert.assertThrows("Header name ':Path' is invalid (header name contains uppercase characters)",
                HttpException.class, () -> converter.convert(headers));
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
        Assert.assertThrows("Header 'connection: keep-alive' is illegal for HTTP/2 messages",
                HttpException.class, () -> converter.convert(headers));
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
        Assert.assertThrows("Invalid sequence of headers (pseudo-headers must precede message headers)",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsMissingMethod() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Mandatory request header ':method' not found",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsMissingScheme() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Mandatory request header ':scheme' not found",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsMissingPath() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Mandatory request header ':path' not found",
                HttpException.class, () -> converter.convert(headers));
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
        Assert.assertThrows("Unsupported request header ':custom'",
                HttpException.class, () -> converter.convert(headers));
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
        Assert.assertThrows("Multiple ':method' request headers are illegal",
                HttpException.class, () -> converter.convert(headers));
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
        Assert.assertThrows("Multiple ':scheme' request headers are illegal",
                HttpException.class, () -> converter.convert(headers));
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
        Assert.assertThrows("Multiple ':path' request headers are illegal",
                HttpException.class, () -> converter.convert(headers));
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
        Assert.assertThrows("Header ':authority' is mandatory for CONNECT request",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsConnectPresentScheme() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Header ':scheme' must not be set for CONNECT request",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromFieldsConnectPresentPath() throws Exception {
        final List<Header> headers = Arrays.asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Header ':path' must not be set for CONNECT request",
                HttpException.class, () -> converter.convert(headers));
    }

    @Test
    public void testConvertFromMessageBasic() throws Exception {

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assert.assertNotNull(headers);
        Assert.assertEquals(5, headers.size());
        final Header header1 = headers.get(0);
        Assert.assertEquals(":method", header1.getName());
        Assert.assertEquals("GET", header1.getValue());
        final Header header2 = headers.get(1);
        Assert.assertEquals(":scheme", header2.getName());
        Assert.assertEquals("http", header2.getValue());
        final Header header3 = headers.get(2);
        Assert.assertEquals(":authority", header3.getName());
        Assert.assertEquals("host", header3.getValue());
        final Header header4 = headers.get(3);
        Assert.assertEquals(":path", header4.getName());
        Assert.assertEquals("/", header4.getValue());
        final Header header5 = headers.get(4);
        Assert.assertEquals("custom123", header5.getName());
        Assert.assertEquals("Value", header5.getValue());
    }

    @Test
    public void testConvertFromMessageMissingScheme() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setScheme(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Request scheme is not set",
                HttpException.class, () -> converter.convert(request));
    }

    @Test
    public void testConvertFromMessageMissingPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setPath(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Request path is not set",
                HttpException.class, () -> converter.convert(request));
    }

    @Test
    public void testConvertFromMessageConnect() throws Exception {

        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host:80"), null);
        request.addHeader("custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final List<Header> headers = converter.convert(request);

        Assert.assertNotNull(headers);
        Assert.assertEquals(3, headers.size());
        final Header header1 = headers.get(0);
        Assert.assertEquals(":method", header1.getName());
        Assert.assertEquals("CONNECT", header1.getValue());
        final Header header2 = headers.get(1);
        Assert.assertEquals(":authority", header2.getName());
        Assert.assertEquals("host:80", header2.getValue());
        final Header header3 = headers.get(2);
        Assert.assertEquals("custom123", header3.getName());
        Assert.assertEquals("Value", header3.getValue());
    }

    @Test
    public void testConvertFromMessageConnectMissingAuthority() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", null, null);
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("CONNECT request authority is not set",
                HttpException.class, () -> converter.convert(request));
    }

    @Test
    public void testConvertFromMessageConnectWithPath() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", "/");
        request.setAuthority(new URIAuthority("host"));
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("CONNECT request path must be null",
                HttpException.class, () -> converter.convert(request));
    }

    @Test
    public void testConvertFromMessageConnectionHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Connection", "Keep-Alive");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages",
                HttpException.class, () -> converter.convert(request));
    }

    @Test
    public void testConvertFromMessageInvalidHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":custom", "stuff");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        Assert.assertThrows("Header name ':custom' is invalid",
                HttpException.class, () -> converter.convert(request));
    }

}

