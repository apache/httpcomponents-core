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

import junit.framework.TestCase;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;

/**
 * Simple tests for {@link AbstractMessageParser}.
 */
public class TestHttpMessageParser extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestHttpMessageParser(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    private static ReadableByteChannel newChannel(final String s, final String charset)
            throws UnsupportedEncodingException {
        return Channels.newChannel(new ByteArrayInputStream(s.getBytes(charset)));
    }

    private static ReadableByteChannel newChannel(final String s)
            throws UnsupportedEncodingException {
        return newChannel(s, "US-ASCII");
    }

    public void testSimpleParsing() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.1\r\nSome header: stuff\r\n\r\n"));
        HttpRequest request = requestParser.parse();
        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
    }

    public void testParsingChunkedMessages() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\nSome header: stuff\r\n\r\n"));
        request = requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);

    }

    public void testParsingFoldedHeaders() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\nSome header: stuff\r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("   more\r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\tstuff\r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }

    public void testParsingBadlyFoldedFirstHeader() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\n  Some header: stuff\r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("   more stuff\r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }

    public void testParsingEmptyFoldedHeader() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\n  Some header: stuff\r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("      \r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("      more stuff\r\n"));
        request = requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("stuff  more stuff", request.getFirstHeader("Some header").getValue());
    }

    public void testParsingIncompleteRequestLine() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0");
        requestParser.fillBuffer(channel);
        requestParser.fillBuffer(channel);
        HttpRequest request = requestParser.parse();
        assertNotNull(request);
        assertEquals(HttpVersion.HTTP_1_0, request.getRequestLine().getProtocolVersion());
    }

    public void testParsingIncompleteHeader() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: whatever");
        requestParser.fillBuffer(channel);
        requestParser.fillBuffer(channel);
        HttpRequest request = requestParser.parse();
        assertNotNull(request);
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("whatever", request.getFirstHeader("Header").getValue());
    }

    public void testParsingInvalidRequestLine() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        ReadableByteChannel channel = newChannel("GET garbage\r\n");
        requestParser.fillBuffer(channel);
        try {
            requestParser.parse();
            fail("HttpException should have been thrown");
        } catch (HttpException ex) {
            // expected
        }
    }

    public void testParsingInvalidStatusLine() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        NHttpMessageParser<HttpResponse> responseParser = new DefaultHttpResponseParser(inbuf, null, responseFactory, params);

        ReadableByteChannel channel = newChannel("HTTP 200 OK\r\n");
        responseParser.fillBuffer(channel);
        try {
            responseParser.parse();
            fail("HttpException should have been thrown");
        } catch (HttpException ex) {
            // expected
        }
    }

    public void testParsingInvalidHeader() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        NHttpMessageParser<HttpResponse> responseParser = new DefaultHttpResponseParser(inbuf, null, responseFactory, params);

        ReadableByteChannel channel = newChannel("HTTP/1.0 200 OK\r\nstuff\r\n\r\n");
        responseParser.fillBuffer(channel);
        try {
            responseParser.parse();
            fail("HttpException should have been thrown");
        } catch (HttpException ex) {
            // expected
        }
    }

    public void testResetParser() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);

        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n");
        requestParser.fillBuffer(channel);
        HttpRequest request = requestParser.parse();
        assertNotNull(request);
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("one", request.getFirstHeader("Header").getValue());

        requestParser.reset();

        channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: two\r\n\r\n");
        requestParser.fillBuffer(channel);
        request = requestParser.parse();
        assertNotNull(request);
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("two", request.getFirstHeader("Header").getValue());
    }

    public void testInvalidConstructor() {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        try {
            new DefaultHttpRequestParser(null, null, null, params);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new DefaultHttpRequestParser(inbuf, null, null, params);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new DefaultHttpResponseParser(null, null, null, params);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new DefaultHttpResponseParser(inbuf, null, null, params);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }

    public void testLineLimitForStatus() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();

        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 0);
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();

        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 15);
        requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        try {
            requestParser.fillBuffer(newChannel("GET /loooooooooooooooong HTTP/1.0\r\nHeader: one\r\n\r\n"));
            requestParser.parse();
            fail("IOException should have been thrown");
        } catch (IOException expected) {
        }
    }

    public void testLineLimitForHeader() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();

        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 0);
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();

        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 15);
        requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        requestParser.fillBuffer(newChannel("GET / HTTP/1.0\r\nHeader: 9012345\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();
        try {
            requestParser.fillBuffer(newChannel("GET / HTTP/1.0\r\nHeader: 90123456\r\n\r\n"));
            requestParser.parse();
            fail("IOException should have been thrown");
        } catch (IOException expected) {
        }
    }

    public void testLineLimitForFoldedHeader() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();

        params.setIntParameter(CoreConnectionPNames.MAX_HEADER_COUNT, 2);
        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 15);
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        try {
            requestParser.fillBuffer(newChannel("GET / HTTP/1.0\r\nHeader: 9012345\r\n 23456789012345\r\n 23456789012345\r\n 23456789012345\r\n\r\n"));
            requestParser.parse();
            fail("IOException should have been thrown");
        } catch (IOException expected) {
        }
    }

    public void testMaxHeaderCount() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();

        params.setIntParameter(CoreConnectionPNames.MAX_HEADER_COUNT, 2);
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\nHeader: two\r\n\r\n"));
        requestParser.parse();
        requestParser.reset();

        try {
            requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\nHeader: two\r\nHeader: three\r\n\r\n"));
            requestParser.parse();
            fail("IOException should have been thrown");
        } catch (IOException expected) {
        }
    }

    public void testDetectLineLimitEarly() throws Exception {
        HttpParams params = new BasicHttpParams();
        SessionInputBuffer inbuf = new SessionInputBufferImpl(2, 128, params);
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();

        params.setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, 2);
        NHttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inbuf, null, requestFactory, params);
        ReadableByteChannel channel = newChannel("GET / HTTP/1.0\r\nHeader: one\r\n\r\n");
        assertEquals(2, requestParser.fillBuffer(channel));
        assertNull(requestParser.parse());
        assertEquals(4, requestParser.fillBuffer(channel));
        try {
            requestParser.parse();
            fail("IOException should have been thrown");
        } catch (IOException expected) {
        }
    }

}
