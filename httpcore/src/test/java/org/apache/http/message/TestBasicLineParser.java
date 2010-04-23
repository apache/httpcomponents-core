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

package org.apache.http.message;

import junit.framework.TestCase;

import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.util.CharArrayBuffer;

/**
 * Tests for {@link BasicLineParser}.
 *
 *
 */
public class TestBasicLineParser extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicLineParser(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testRLParseSuccess() throws Exception {
        //typical request line
        RequestLine requestline = BasicLineParser.parseRequestLine
            ("GET /stuff HTTP/1.1", null);
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //Lots of blanks
        requestline = BasicLineParser.parseRequestLine
            ("  GET    /stuff   HTTP/1.1   ", null);
        assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //this is not strictly valid, but is lenient
        requestline = BasicLineParser.parseRequestLine
            ("\rGET /stuff HTTP/1.1", null);
        assertEquals("GET", requestline.getMethod());
        assertEquals("/stuff", requestline.getUri());
        assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());
    }

    public void testRLParseFailure() throws Exception {
        try {
            BasicLineParser.parseRequestLine("    ", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("  GET", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET /stuff", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET/stuff HTTP/1.1", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET /stuff HTTP/1.1 Oooooooooooppsie", null);
            fail();
        } catch (ParseException e) {
            // expected
        }
    }

    public void testSLParseSuccess() throws Exception {
        //typical status line
        StatusLine statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 OK", null);
        assertEquals("HTTP/1.1 200 OK", statusLine.toString());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());

        //status line with multi word reason phrase
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found", null);
        assertEquals(404, statusLine.getStatusCode());
        assertEquals("Not Found", statusLine.getReasonPhrase());

        //reason phrase can be anyting
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Non Trouve", null);
        assertEquals("Non Trouve", statusLine.getReasonPhrase());

        //its ok to end with a \n\r
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found\r\n", null);
        assertEquals("Not Found", statusLine.getReasonPhrase());

        //this is valid according to the Status-Line BNF
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 ", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1     200 OK", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("\rHTTP/1.1 200 OK", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("  HTTP/1.1 200 OK", null);
        assertEquals(200, statusLine.getStatusCode());
        assertEquals("OK", statusLine.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
    }

    public void testSLParseFailure() throws Exception {
        try {
            BasicLineParser.parseStatusLine("xxx 200 OK", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1 xxx OK", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1    ", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1", null);
            fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1 -200 OK", null);
            fail();
        } catch (ParseException e) {
            // expected
        }
    }

    public void testHttpVersionParsing() throws Exception {

        String s = "HTTP/1.1";
        HttpVersion version = (HttpVersion)
            BasicLineParser.parseProtocolVersion(s, null);
        assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        assertEquals("HTTP major version number", 1, version.getMajor());
        assertEquals("HTTP minor version number", 1, version.getMinor());
        assertEquals("HTTP version number", s, version.toString());

        s = "HTTP/123.4567";
        version = (HttpVersion)
            BasicLineParser.parseProtocolVersion(s, null);
        assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        assertEquals("HTTP major version number", 123, version.getMajor());
        assertEquals("HTTP minor version number", 4567, version.getMinor());
        assertEquals("HTTP version number", s, version.toString());
    }

    public void testHttpVersionParsingUsingCursor() throws Exception {

        String s = "HTTP/1.1";
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(s);
        ParserCursor cursor = new ParserCursor(0, s.length());

        LineParser parser = BasicLineParser.DEFAULT;

        HttpVersion version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        assertEquals("HTTP major version number", 1, version.getMajor());
        assertEquals("HTTP minor version number", 1, version.getMinor());
        assertEquals("HTTP version number", "HTTP/1.1", version.toString());
        assertEquals(s.length(), cursor.getPos());
        assertTrue(cursor.atEnd());

        s = "HTTP/1.123 123";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        assertEquals("HTTP major version number", 1, version.getMajor());
        assertEquals("HTTP minor version number", 123, version.getMinor());
        assertEquals("HTTP version number", "HTTP/1.123", version.toString());
        assertEquals(' ', buffer.charAt(cursor.getPos()));
        assertEquals(s.length() - 4, cursor.getPos());
        assertFalse(cursor.atEnd());
    }

    public void testInvalidHttpVersionParsing() throws Exception {
        try {
            BasicLineParser.parseProtocolVersion((String)null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("    ", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTT", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("crap", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/crap", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1234   ", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/whatever.whatever whatever", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.whatever whatever", null);
            fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
    }

}
