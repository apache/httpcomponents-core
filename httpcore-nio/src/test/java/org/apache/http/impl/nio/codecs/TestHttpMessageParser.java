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

package org.apache.http.impl.nio.codecs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

import org.apache.http.Consts;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.reactor.SessionInputBuffer;
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
        return newChannel(s, Consts.ASCII);
    }

    @Test
    public void testSimpleParsing() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.1\r\nSome header: stuff\r\n\r\n"));
        final HttpRequest request = requestParser.parse();
        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
    }

    @Test
    public void testParsingChunkedMessages() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\nSome header: stuff\r\n\r\n"));
        request = requestParser.parse();

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);

    }

    @Test
    public void testParsingFoldedHeaders() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\nSome header: stuff\r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("   more\r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\tstuff\r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = requestParser.parse();

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }

    @Test
    public void testParsingBadlyFoldedFirstHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\n  Some header: stuff\r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("   more stuff\r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = requestParser.parse();

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }

    @Test
    public void testParsingEmptyFoldedHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\n  Some header: stuff\r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("      \r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("      more stuff\r\n"));
        request = requestParser.parse();
        Assert.assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = requestParser.parse();

        Assert.assertNotNull(request);
        Assert.assertEquals("/whatever", request.getRequestLine().getUri());
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("stuff  more stuff", request.getFirstHeader("Some header").getValue());
    }

    @Test
    public void testParsingIncompleteRequestLine() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        final ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0");
        requestParser.fillBuffer(channel);
        requestParser.fillBuffer(channel);
        final HttpRequest request = requestParser.parse();
        Assert.assertNotNull(request);
        Assert.assertEquals(HttpVersion.HTTP_1_0, request.getRequestLine().getProtocolVersion());
    }

    @Test
    public void testParsingIncompleteHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        final ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: whatever");
        requestParser.fillBuffer(channel);
        requestParser.fillBuffer(channel);
        final HttpRequest request = requestParser.parse();
        Assert.assertNotNull(request);
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("whatever", request.getFirstHeader("Header").getValue());
    }

    @Test
    public void testParsingInvalidRequestLine() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        final ReadableByteChannel channel = newChannel("GET garbage\r\n");
        requestParser.fillBuffer(channel);
        try {
            requestParser.parse();
            Assert.fail("HttpException should have been thrown");
        } catch (final HttpException ex) {
            // expected
        }
    }

    @Test
    public void testParsingInvalidStatusLine() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpResponse> responseParser = new DefaultHttpResponseParser(inbuf);

        final ReadableByteChannel channel = newChannel("HTTP 200 OK\r\n");
        responseParser.fillBuffer(channel);
        try {
            responseParser.parse();
            Assert.fail("HttpException should have been thrown");
        } catch (final HttpException ex) {
            // expected
        }
    }

    @Test
    public void testParsingInvalidHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpResponse> responseParser = new DefaultHttpResponseParser(inbuf);

        final ReadableByteChannel channel = newChannel("HTTP/1.0 200 OK\r\nstuff\r\n\r\n");
        responseParser.fillBuffer(channel);
        try {
            responseParser.parse();
            Assert.fail("HttpException should have been thrown");
        } catch (final HttpException ex) {
            // expected
        }
    }

    @Test
    public void testResetParser() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf);

        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n");
        requestParser.fillBuffer(channel);
        HttpRequest request = requestParser.parse();
        Assert.assertNotNull(request);
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("one", request.getFirstHeader("Header").getValue());

        requestParser.reset();

        channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: two\r\n\r\n");
        requestParser.fillBuffer(channel);
        request = requestParser.parse();
        Assert.assertNotNull(request);
        Assert.assertEquals(1, request.getAllHeaders().length);
        Assert.assertEquals("two", request.getFirstHeader("Header").getValue());
    }

    @Test
    public void testInvalidConstructor() {
        try {
            new DefaultHttpRequestParser(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
    }

    @Test
    public void testLineLimitForStatus() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf,
                MessageConstraints.lineLen(0));
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();

        requestParser = new DefaultHttpRequestParser(inbuf, MessageConstraints.lineLen(15));
        try {
            requestParser.fillBuffer(newChannel("GET /loooooooooooooooong HTTP/1.0\r\nHeader: one\r\n\r\n"));
            requestParser.parse();
            Assert.fail("IOException should have been thrown");
        } catch (final IOException expected) {
        }
    }

    @Test
    public void testLineLimitForHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);

        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf,
                MessageConstraints.lineLen(0));
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();

        requestParser = new DefaultHttpRequestParser(inbuf, MessageConstraints.lineLen(15));
        requestParser.fillBuffer(newChannel("GET / HTTP/1.0\r\nHeader: 9012345\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();
        try {
            requestParser.fillBuffer(newChannel("GET / HTTP/1.0\r\nHeader: 90123456\r\n\r\n"));
            requestParser.parse();
            Assert.fail("IOException should have been thrown");
        } catch (final IOException expected) {
        }
    }

    @Test
    public void testLineLimitForFoldedHeader() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);

        final MessageConstraints constraints = MessageConstraints.custom()
                .setMaxHeaderCount(2).setMaxLineLength(15).build();
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, constraints);
        try {
            requestParser.fillBuffer(newChannel("GET / HTTP/1.0\r\nHeader: 9012345\r\n" +
                    " 23456789012345\r\n 23456789012345\r\n 23456789012345\r\n\r\n"));
            requestParser.parse();
            Assert.fail("IOException should have been thrown");
        } catch (final IOException expected) {
        }
    }

    @Test
    public void testMaxHeaderCount() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, Consts.ASCII);

        final MessageConstraints constraints = MessageConstraints.custom()
                .setMaxHeaderCount(2).setMaxLineLength(-1).build();
        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, constraints);
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\nHeader: two\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();

        try {
            requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n" +
                    "Header: two\r\nHeader: three\r\n\r\n"));
            requestParser.parse();
            Assert.fail("IOException should have been thrown");
        } catch (final IOException expected) {
        }
    }

    @Test
    public void testDetectLineLimitEarly() throws Exception {
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(2, 128, Consts.ASCII);

        final NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf,
                MessageConstraints.lineLen(2));
        final ReadableByteChannel channel = newChannel("GET / HTTP/1.0\r\nHeader: one\r\n\r\n");
        Assert.assertEquals(2, requestParser.fillBuffer(channel));
        Assert.assertNull(requestParser.parse());
        Assert.assertEquals(4, requestParser.fillBuffer(channel));
        try {
            requestParser.parse();
            Assert.fail("IOException should have been thrown");
        } catch (final IOException expected) {
        }
    }

}
