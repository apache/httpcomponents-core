/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.impl.codecs;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.impl.SessionInputBuffer;
import org.apache.http.nio.impl.codecs.HttpMessageParser;
import org.apache.http.nio.impl.codecs.HttpRequestParser;

/**
 * Simple tests for {@link HttpMessageParser}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestHttpMessageParser extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestHttpMessageParser(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestHttpMessageParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestHttpMessageParser.class);
    }

    private static ReadableByteChannel newChannel(final String s, final String charset) 
            throws UnsupportedEncodingException {
        return Channels.newChannel(new ByteArrayInputStream(s.getBytes(charset)));
    }
    
    private static ReadableByteChannel newChannel(final String s) 
            throws UnsupportedEncodingException {
        return newChannel(s, "US-ASCII");
    }

    public void testSimpleParsing() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);
        requestParser.fillBuffer(newChannel("GET /whatever HTTP/1.1\r\nSome header: stuff\r\n\r\n"));
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
    }

    public void testParsingChunkedMessages() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\nSome header: stuff\r\n\r\n"));
        request = (HttpRequest) requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
        
    }

    public void testParsingFoldedHeaders() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\nSome header: stuff\r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("   more\r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\tstuff\r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = (HttpRequest) requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }
    
    public void testParsingBadlyFoldedFirstHeader() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\n  Some header: stuff\r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("   more stuff\r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = (HttpRequest) requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("stuff more stuff", request.getFirstHeader("Some header").getValue());
    }
    
    public void testParsingEmptyFoldedHeader() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);

        requestParser.fillBuffer(newChannel("GET /whatev"));
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("er HTTP/1.1\r"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\n  Some header: stuff\r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("      \r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("      more stuff\r\n"));
        request = (HttpRequest) requestParser.parse();
        assertNull(request);
        requestParser.fillBuffer(newChannel("\r\n"));
        request = (HttpRequest) requestParser.parse();

        assertNotNull(request);
        assertEquals("/whatever", request.getRequestLine().getUri());
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("stuff  more stuff", request.getFirstHeader("Some header").getValue());
    }
    
    public void testParsingIncompleteRequestLine() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);
        
        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0");
        requestParser.fillBuffer(channel);
        requestParser.fillBuffer(channel);
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNotNull(request);
        assertEquals(HttpVersion.HTTP_1_0, request.getRequestLine().getHttpVersion());
    }
    
    public void testParsingIncompleteHeader() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);
        
        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: whatever");
        requestParser.fillBuffer(channel);
        requestParser.fillBuffer(channel);
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNotNull(request);
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("whatever", request.getFirstHeader("Header").getValue());
    }
    
    public void testParsingInvalidRequestLine() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);
        
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
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        HttpResponseParser responseParser = new HttpResponseParser(inbuf, responseFactory);
        
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
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        HttpResponseParser responseParser = new HttpResponseParser(inbuf, responseFactory);
        
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
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
        HttpRequestParser requestParser = new HttpRequestParser(inbuf, requestFactory);
        
        ReadableByteChannel channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: one\r\n\r\n");
        requestParser.fillBuffer(channel);
        HttpRequest request = (HttpRequest) requestParser.parse();
        assertNotNull(request);
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("one", request.getFirstHeader("Header").getValue());
        
        requestParser.reset();

        channel = newChannel("GET /whatever HTTP/1.0\r\nHeader: two\r\n\r\n");
        requestParser.fillBuffer(channel);
        request = (HttpRequest) requestParser.parse();
        assertNotNull(request);
        assertEquals(1, request.getAllHeaders().length);
        assertEquals("two", request.getFirstHeader("Header").getValue());
    }
    
    public void testInvalidConstructor() {
        SessionInputBuffer inbuf = new SessionInputBuffer(1024, 128); 
        try {
            new HttpRequestParser(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new HttpRequestParser(inbuf, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new HttpResponseParser(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new HttpResponseParser(inbuf, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }

}