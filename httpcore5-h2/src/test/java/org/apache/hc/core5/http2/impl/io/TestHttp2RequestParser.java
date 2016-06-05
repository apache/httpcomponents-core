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

package org.apache.hc.core5.http2.impl.io;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestHttp2RequestParser {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testBasicParse() throws Exception {

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom123", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        final HttpRequest request = parser.parse(src);
        Assert.assertNotNull(request);
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("http", request.getScheme());
        Assert.assertEquals("www.example.com", request.getAuthority());
        Assert.assertEquals("/", request.getPath());
        final Header[] allHeaders = request.getAllHeaders();
        Assert.assertEquals(1, allHeaders.length);
        Assert.assertEquals("custom123", allHeaders[0].getName());
        Assert.assertEquals("value", allHeaders[0].getValue());
    }

    @Test
    public void testParseUpperCaseHeaderName() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header name ':Path' is invalid (header name contains uppercase characters)");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":Path", "/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseConnectionHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header 'connection: keep-alive' is illegal for HTTP/2 messages");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("connection", "keep-alive")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParsePseudoHeaderSequence() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Invalid sequence of headers (pseudo-headers must precede message headers)");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader("custom", "value"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMissingMethod() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Mandatory request header ':method' not found");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMissingScheme() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Mandatory request header ':scheme' not found");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMissingPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Mandatory request header ':path' not found");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseUnknownPseudoHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Unsupported request header ':custom'");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMultipleMethod() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Multiple ':method' request headers are illegal");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMultipleScheme() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Multiple ':scheme' request headers are illegal");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMultiplePath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Multiple ':path' request headers are illegal");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseConnect() throws Exception {

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseConnectMissingAuthority() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header ':authority' is mandatory for CONNECT request");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseConnectPresentScheme() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header ':scheme' must not be set for CONNECT request");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":scheme", "http"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseConnectPresentPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header ':path' must not be set for CONNECT request");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":method", "CONNECT"),
                new BasicHeader(":authority", "www.example.com"),
                new BasicHeader(":path", "/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.array(), 0, buf.length());

        final Http2RequestParser parser = new Http2RequestParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

}

