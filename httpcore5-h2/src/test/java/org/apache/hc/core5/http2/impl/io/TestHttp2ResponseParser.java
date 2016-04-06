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
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestHttp2ResponseParser {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testBasicParse() throws Exception {

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());

        final Http2ResponseParser parser = new Http2ResponseParser(StandardCharsets.US_ASCII);
        final HttpResponse response = parser.parse(src);
        Assert.assertNotNull(response );
        Assert.assertEquals(200, response .getCode());
        final Header[] allHeaders = response.getAllHeaders();
        Assert.assertEquals(2, allHeaders.length);
        Assert.assertEquals("location", allHeaders[0].getName());
        Assert.assertEquals("http://www.example.com/", allHeaders[0].getValue());
        Assert.assertEquals("custom123", allHeaders[1].getName());
        Assert.assertEquals("value", allHeaders[1].getValue());
    }

    @Test
    public void testParseUpperCaseHeaderName() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header name ':Status' is invalid (header name contains uppercase characters)");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":Status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());

        final Http2ResponseParser parser = new Http2ResponseParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseInvalidStatusCode() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Invalid response status: boom");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":status", "boom"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom123", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());

        final Http2ResponseParser parser = new Http2ResponseParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseConnectionHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header 'connection: keep-alive' is illegal for HTTP/2 messages");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("connection", "keep-alive")));
        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());

        final Http2ResponseParser parser = new Http2ResponseParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMissingStatus() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Mandatory response header ':status' not found");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());

        final Http2ResponseParser parser = new Http2ResponseParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseUnknownPseudoHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Unsupported response header ':custom'");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":custom", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());

        final Http2ResponseParser parser = new Http2ResponseParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

    @Test
    public void testParseMultipleStatus() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Multiple ':status' response headers are illegal");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);
        final HPackEncoder encoder = new HPackEncoder(StandardCharsets.US_ASCII);
        encoder.encodeHeaders(buf, Arrays.<Header>asList(
                new BasicHeader(":status", "200"),
                new BasicHeader(":status", "200"),
                new BasicHeader("location", "http://www.example.com/"),
                new BasicHeader("custom1", "value")));
        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());

        final Http2ResponseParser parser = new Http2ResponseParser(StandardCharsets.US_ASCII);
        parser.parse(src);
    }

}

