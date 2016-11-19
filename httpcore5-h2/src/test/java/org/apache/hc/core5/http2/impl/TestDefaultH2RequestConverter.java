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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestDefaultH2RequestConverter {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testConvertFromFieldsBasic() throws Exception {

        final List<Header> headers = Arrays.<Header>asList(
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
        final Header[] allHeaders = request.getAllHeaders();
        Assert.assertEquals(1, allHeaders.length);
        Assert.assertEquals("custom123", allHeaders[0].getName());
        Assert.assertEquals("value", allHeaders[0].getValue());
    }

    @Test
    public void testConvertFromFieldsUpperCaseHeaderName() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header name ':Path' is invalid (header name contains uppercase characters)");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":Path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsConnectionHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header 'connection: keep-alive' is illegal for HTTP/2 messages");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("connection", "keep-alive"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsPseudoHeaderSequence() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Invalid sequence of headers (pseudo-headers must precede message headers)");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader("custom", "value"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsMissingMethod() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Mandatory request header ':method' not found");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsMissingScheme() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Mandatory request header ':scheme' not found");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsMissingPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Mandatory request header ':path' not found");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsUnknownPseudoHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Unsupported request header ':custom'");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsMultipleMethod() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Multiple ':method' request headers are illegal");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsMultipleScheme() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Multiple ':scheme' request headers are illegal");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsMultiplePath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Multiple ':path' request headers are illegal");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsConnect() throws Exception {

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsConnectMissingAuthority() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header ':authority' is mandatory for CONNECT request");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsConnectPresentScheme() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header ':scheme' must not be set for CONNECT request");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
    }

    @Test
    public void testConvertFromFieldsConnectPresentPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header ':path' must not be set for CONNECT request");

        final List<Header> headers = Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value"));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(headers);
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

        thrown.expect(HttpException.class);
        thrown.expectMessage("Request scheme is not set");

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setScheme(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(request);
    }

    @Test
    public void testConvertFromMessageMissingPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Request path is not set");

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setPath(null);

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(request);
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

        thrown.expect(HttpException.class);
        thrown.expectMessage("CONNECT request authority is not set");

        final HttpRequest request = new BasicHttpRequest("CONNECT", null, null);
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(request);
    }

    @Test
    public void testConvertFromMessageConnectWithPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("CONNECT request path must be null");

        final HttpRequest request = new BasicHttpRequest("CONNECT", "/");
        request.setAuthority(new URIAuthority("host"));
        request.addHeader("Custom123", "Value");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(request);
    }

    @Test
    public void testConvertFromMessageConnectionHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages");

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Connection", "Keep-Alive");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(request);
    }

    @Test
    public void testConvertFromMessageInvalidHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header name ':custom' is invalid");

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":custom", "stuff");

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        converter.convert(request);
    }

}

