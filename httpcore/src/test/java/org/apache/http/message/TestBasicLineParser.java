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

import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link BasicLineParser}.
 *
 */
public class TestBasicLineParser {

    @Test
    public void testRLParseSuccess() throws Exception {
        //typical request line
        RequestLine requestline = BasicLineParser.parseRequestLine
            ("GET /stuff HTTP/1.1", null);
        Assert.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assert.assertEquals("GET", requestline.getMethod());
        Assert.assertEquals("/stuff", requestline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //Lots of blanks
        requestline = BasicLineParser.parseRequestLine
            ("  GET    /stuff   HTTP/1.1   ", null);
        Assert.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assert.assertEquals("GET", requestline.getMethod());
        Assert.assertEquals("/stuff", requestline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //this is not strictly valid, but is lenient
        requestline = BasicLineParser.parseRequestLine
            ("\rGET /stuff HTTP/1.1", null);
        Assert.assertEquals("GET", requestline.getMethod());
        Assert.assertEquals("/stuff", requestline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());
    }

    @Test
    public void testRLParseFailure() throws Exception {
        try {
            BasicLineParser.parseRequestLine("    ", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("  GET", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET /stuff", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET/stuff HTTP/1.1", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET /stuff HTTP/1.1 Oooooooooooppsie", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }
    }

    @Test
    public void testSLParseSuccess() throws Exception {
        //typical status line
        StatusLine statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 OK", null);
        Assert.assertEquals("HTTP/1.1 200 OK", statusLine.toString());
        Assert.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());

        //status line with multi word reason phrase
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found", null);
        Assert.assertEquals(404, statusLine.getStatusCode());
        Assert.assertEquals("Not Found", statusLine.getReasonPhrase());

        //reason phrase can be anyting
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Non Trouve", null);
        Assert.assertEquals("Non Trouve", statusLine.getReasonPhrase());

        //its ok to end with a \n\r
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found\r\n", null);
        Assert.assertEquals("Not Found", statusLine.getReasonPhrase());

        //this is valid according to the Status-Line BNF
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 ", null);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200", null);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1     200 OK", null);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("\rHTTP/1.1 200 OK", null);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());
        Assert.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("  HTTP/1.1 200 OK", null);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());
        Assert.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
    }

    @Test
    public void testSLParseFailure() throws Exception {
        try {
            BasicLineParser.parseStatusLine("xxx 200 OK", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1 xxx OK", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1    ", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1 -200 OK", null);
            Assert.fail();
        } catch (ParseException e) {
            // expected
        }
    }

    @Test
    public void testHttpVersionParsing() throws Exception {

        String s = "HTTP/1.1";
        HttpVersion version = (HttpVersion)
            BasicLineParser.parseProtocolVersion(s, null);
        Assert.assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        Assert.assertEquals("HTTP major version number", 1, version.getMajor());
        Assert.assertEquals("HTTP minor version number", 1, version.getMinor());
        Assert.assertEquals("HTTP version number", s, version.toString());

        s = "HTTP/123.4567";
        version = (HttpVersion)
            BasicLineParser.parseProtocolVersion(s, null);
        Assert.assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        Assert.assertEquals("HTTP major version number", 123, version.getMajor());
        Assert.assertEquals("HTTP minor version number", 4567, version.getMinor());
        Assert.assertEquals("HTTP version number", s, version.toString());
    }

    @Test
    public void testHttpVersionParsingUsingCursor() throws Exception {

        String s = "HTTP/1.1";
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(s);
        ParserCursor cursor = new ParserCursor(0, s.length());

        LineParser parser = BasicLineParser.DEFAULT;

        HttpVersion version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        Assert.assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        Assert.assertEquals("HTTP major version number", 1, version.getMajor());
        Assert.assertEquals("HTTP minor version number", 1, version.getMinor());
        Assert.assertEquals("HTTP version number", "HTTP/1.1", version.toString());
        Assert.assertEquals(s.length(), cursor.getPos());
        Assert.assertTrue(cursor.atEnd());

        s = "HTTP/1.123 123";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        Assert.assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        Assert.assertEquals("HTTP major version number", 1, version.getMajor());
        Assert.assertEquals("HTTP minor version number", 123, version.getMinor());
        Assert.assertEquals("HTTP version number", "HTTP/1.123", version.toString());
        Assert.assertEquals(' ', buffer.charAt(cursor.getPos()));
        Assert.assertEquals(s.length() - 4, cursor.getPos());
        Assert.assertFalse(cursor.atEnd());
    }

    @Test
    public void testInvalidHttpVersionParsing() throws Exception {
        try {
            BasicLineParser.parseProtocolVersion((String)null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("    ", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTT", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("crap", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/crap", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1234   ", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/whatever.whatever whatever", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.whatever whatever", null);
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException e) {
            //expected
        }
    }

}
