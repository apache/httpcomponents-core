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

package org.apache.hc.core5.http.impl.nio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.config.MessageConstraints;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link AbstractMessageParser}.
 */
public class TestHttpMessageParser {

    private static ReadableByteChannel newChannel(final String s, final Charset charset)
            throws UnsupportedEncodingException {
        return Channels.newChannel(new ByteArrayInputStream(s.getBytes(charset)));
    }

    private static ReadableByteChannel newChannel(final String s)
            throws UnsupportedEncodingException {
        return newChannel(s, StandardCharsets.US_ASCII);
    }

    @Test
    public void testSimpleParsing() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();
        inbuf.fill(newChannel("GET /whatever HTTP/1.1\r\nSome header: stuff\r\n\r\n"));
        final HttpRequest request = requestParser.parse(inbuf, false);
        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
    }

    @Test
    public void testParsingChunkedMessages() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        inbuf.fill(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\nSome header: stuff\r\n\r\n"));
        request = requestParser.parse(inbuf, false);

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);

    }

    @Test
    public void testParsingFoldedHeaders() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        inbuf.fill(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\nSome header: stuff\r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("   more\r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\tstuff\r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\r\n"));
        request = requestParser.parse(inbuf, false);

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }

    @Test
    public void testParsingBadlyFoldedFirstHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        inbuf.fill(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\n  Some header: stuff\r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("   more stuff\r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\r\n"));
        request = requestParser.parse(inbuf, false);

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }

    @Test
    public void testParsingEmptyFoldedHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        inbuf.fill(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\n  Some header: stuff\r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("      \r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("      more stuff\r\n"));
        request = requestParser.parse(inbuf, false);
        Assert.assertNull(request);
        inbuf.fill(newChannel("\r\n"));
        request = requestParser.parse(inbuf, false);

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("stuff  more stuff", request.getFirstHeader("Some header").getValue());
    }

    @Test
    public void testParsingIncompleteRequestLine() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        final ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0");
        inbuf.fill(channel);
        final HttpRequest request = requestParser.parse(inbuf, true);
        Assert.assertNotNull(request);
        Assert.assertEquals(HttpVersion.HTTP_1_0, request.getRequestLine().getProtocolVersion());
    }

    @Test
    public void testParsingIncompleteHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        final ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: whatever");
        inbuf.fill(channel);
        final HttpRequest request = requestParser.parse(inbuf, true);
        Assert.assertNotNull(request);
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("whatever", request.getFirstHeader("Header").getValue());
    }

    @Test(expected = HttpException.class)
    public void testParsingInvalidRequestLine() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        final ReadableByteChannel channel = newChannel("GET garbage\r\n");
        inbuf.fill(channel);
        requestParser.parse(inbuf, false);
    }

    @Test
    public void testResetParser() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();

        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n");
        inbuf.fill(channel);
        HttpRequest request = requestParser.parse(inbuf, false);
        Assert.assertNotNull(request);
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("one", request.getFirstHeader("Header").getValue());

        requestParser.reset();

        channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: two\r\n\r\n");
        inbuf.fill(channel);
        request = requestParser.parse(inbuf, false);
        Assert.assertNotNull(request);
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("two", request.getFirstHeader("Header").getValue());
    }

    @Test(expected = IOException.class)
    public void testLineLimitForStatus() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(
                MessageConstraints.lineLen(0));
        inbuf.fill(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n"));
        requestParser.parse(inbuf, false);
        requestParser.reset();

        requestParser = new DefaultHttpRequestParser(MessageConstraints.lineLen(15));
        inbuf.fill(newChannel("GET /loooooooooooooooong HTTP/1.0\r\nHeader: one\r\n\r\n"));
        requestParser.parse(inbuf, false);
    }

    @Test(expected = IOException.class)
    public void testLineLimitForHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);

        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(
                MessageConstraints.lineLen(0));
        inbuf.fill(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n"));
        requestParser.parse(inbuf, false);
        requestParser.reset();

        requestParser = new DefaultHttpRequestParser(MessageConstraints.lineLen(15));
        inbuf.fill(newChannel("GET / HTTP/1.0\r\nHeader: 9012345\r\n\r\n"));
        requestParser.parse(inbuf, false);
        requestParser.reset();
        inbuf.fill(newChannel("GET / HTTP/1.0\r\nHeader: 90123456\r\n\r\n"));
        requestParser.parse(inbuf, false);
    }

    @Test(expected = IOException.class)
    public void testLineLimitForFoldedHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);

        final MessageConstraints constraints = MessageConstraints.custom()
                .setMaxHeaderCount(2).setMaxLineLength(15).build();
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(constraints);
        inbuf.fill(newChannel("GET / HTTP/1.0\r\nHeader: 9012345\r\n" +
                " 23456789012345\r\n 23456789012345\r\n 23456789012345\r\n\r\n"));
        requestParser.parse(inbuf, false);
    }

    @Test(expected = IOException.class)
    public void testMaxHeaderCount() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);

        final MessageConstraints constraints = MessageConstraints.custom()
                .setMaxHeaderCount(2).setMaxLineLength(-1).build();
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(constraints);
        inbuf.fill(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\nHeader: two\r\n\r\n"));
        requestParser.parse(inbuf, false);
        requestParser.reset();

        inbuf.fill(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n" +
                "Header: two\r\nHeader: three\r\n\r\n"));
        requestParser.parse(inbuf, false);
    }

    @Test(expected = IOException.class)
    public void testDetectLineLimitEarly() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(2, 128, StandardCharsets.US_ASCII);

        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(
                MessageConstraints.lineLen(2));
        final ReadableByteChannel channel = newChannel("GET / HTTP/1.0\r\nHeader: one\r\n\r\n");
        Assert.assertEquals(2, inbuf.fill(channel));
        Assert.assertNull(requestParser.parse(inbuf, false));
        Assert.assertEquals(4, inbuf.fill(channel));
        requestParser.parse(inbuf, false);
    }

    @Test
    public void testParsingEmptyLines() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final MessageConstraints constraints = MessageConstraints.custom()
                .setMaxEmptyLineCount(3).build();
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(constraints);
        inbuf.fill(newChannel("\r\n\r\nGET /whatever HTTP/1.1\r\nSome header: stuff\r\n\r\n"));
        final HttpRequest request = requestParser.parse(inbuf, false);
        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
    }

    @Test(expected = MessageConstraintException.class)
    public void testParsingTooManyEmptyLines() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);

        final MessageConstraints constraints = MessageConstraints.custom()
                .setMaxEmptyLineCount(3).build();
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(constraints);
        inbuf.fill(newChannel("\r\n\r\n\r\nGET /whatever HTTP/1.0\r\nHeader: one\r\nHeader: two\r\n\r\n"));
        requestParser.parse(inbuf, false);
    }

    @Test(expected = UnsupportedHttpVersionException.class)
    public void testParsingUnsupportedRequestVersion() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser();
        inbuf.fill(newChannel("GET /whatever HTTP/2.0\r\nSome header: stuff\r\n\r\n"));
        requestParser.parse(inbuf, false);
    }

    @Test(expected = UnsupportedHttpVersionException.class)
    public void testParsingUnsupportedVersion() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpResponse> requestParser = new DefaultHttpResponseParser();
        inbuf.fill(newChannel("HTTP/2.0 200 OK\r\nSome header: stuff\r\n\r\n"));
        requestParser.parse(inbuf, false);
    }

    @Test(expected = HttpException.class)
    public void testParsingInvalidStatusLine() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpResponse> responseParser = new DefaultHttpResponseParser();

        final ReadableByteChannel channel = newChannel("HTTP 200 OK\r\n");
        inbuf.fill(channel);
        responseParser.parse(inbuf, false);
    }

    @Test(expected = HttpException.class)
    public void testParsingInvalidHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, StandardCharsets.US_ASCII);
        final NHttpMessageParser<HttpResponse> responseParser = new DefaultHttpResponseParser();

        final ReadableByteChannel channel = newChannel("HTTP/1.0 200 OK\r\nstuff\r\n\r\n");
        inbuf.fill(channel);
        responseParser.parse(inbuf, false);
    }

}
