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

package org.apache.hc.core5.http.message;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link BasicLineParser}.
 *
 */
public class TestBasicLineParser {

    private BasicLineParser parser;

    @Before
    public void setup() {
        this.parser = BasicLineParser.INSTANCE;
    }

    @Test
    public void testRLParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical request line
        buf.clear();
        buf.append("GET /stuff HTTP/1.1");
        RequestLine requestline = this.parser.parseRequestLine(buf);
        Assert.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assert.assertEquals(Method.GET.name(), requestline.getMethod());
        Assert.assertEquals("/stuff", requestline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //Lots of blanks
        buf.clear();
        buf.append("  GET    /stuff   HTTP/1.1   ");
        requestline = this.parser.parseRequestLine(buf);
        Assert.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assert.assertEquals(Method.GET.name(), requestline.getMethod());
        Assert.assertEquals("/stuff", requestline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("\rGET /stuff HTTP/1.1");
        requestline = this.parser.parseRequestLine(buf);
        Assert.assertEquals(Method.GET.name(), requestline.getMethod());
        Assert.assertEquals("/stuff", requestline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());
    }

    @Test
    public void testRLParseFailure() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.clear();
        buf.append("    ");
        Assert.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("  GET");
        Assert.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("GET /stuff");
        Assert.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("GET/stuff HTTP/1.1");
        Assert.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("GET /stuff HTTP/1.1 Oooooooooooppsie");
        Assert.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
    }

    @Test
    public void testSLParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical status line
        buf.clear();
        buf.append("HTTP/1.1 200 OK");
        StatusLine statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals("HTTP/1.1 200 OK", statusLine.toString());
        Assert.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());

        //status line with multi word reason phrase
        buf.clear();
        buf.append("HTTP/1.1 404 Not Found");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals(404, statusLine.getStatusCode());
        Assert.assertEquals("Not Found", statusLine.getReasonPhrase());

        //reason phrase can be anyting
        buf.clear();
        buf.append("HTTP/1.1 404 Non Trouve");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals("Non Trouve", statusLine.getReasonPhrase());

        //its ok to end with a \n\r
        buf.clear();
        buf.append("HTTP/1.1 404 Not Found\r\n");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals("Not Found", statusLine.getReasonPhrase());

        //this is valid according to the Status-Line BNF
        buf.clear();
        buf.append("HTTP/1.1 200 ");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("HTTP/1.1 200");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("HTTP/1.1     200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("\nHTTP/1.1 200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());
        Assert.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("  HTTP/1.1 200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assert.assertEquals(200, statusLine.getStatusCode());
        Assert.assertEquals("OK", statusLine.getReasonPhrase());
        Assert.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
    }

    @Test
    public void testSLParseFailure() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.clear();
        buf.append("xxx 200 OK");
        Assert.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1 xxx OK");
        Assert.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1    ");
        Assert.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1");
        Assert.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1 -200 OK");
        Assert.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
    }

    @Test
    public void testHttpVersionParsing() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("HTTP/1.1");
        ParserCursor cursor = new ParserCursor(0, buffer.length());

        HttpVersion version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        Assert.assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        Assert.assertEquals("HTTP major version number", 1, version.getMajor());
        Assert.assertEquals("HTTP minor version number", 1, version.getMinor());
        Assert.assertEquals("HTTP version number", "HTTP/1.1", version.toString());
        Assert.assertEquals(buffer.length(), cursor.getPos());
        Assert.assertTrue(cursor.atEnd());

        buffer.clear();
        buffer.append("HTTP/1.123 123");
        cursor = new ParserCursor(0, buffer.length());

        version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        Assert.assertEquals("HTTP protocol name", "HTTP", version.getProtocol());
        Assert.assertEquals("HTTP major version number", 1, version.getMajor());
        Assert.assertEquals("HTTP minor version number", 123, version.getMinor());
        Assert.assertEquals("HTTP version number", "HTTP/1.123", version.toString());
        Assert.assertEquals(' ', buffer.charAt(cursor.getPos()));
        Assert.assertEquals(buffer.length() - 4, cursor.getPos());
        Assert.assertFalse(cursor.atEnd());
    }

    @Test
    public void testInvalidHttpVersionParsing() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.clear();
        buffer.append("    ");
        final ParserCursor cursor1 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor1));
        buffer.clear();
        buffer.append("HTT");
        final ParserCursor cursor2 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor2));
        buffer.clear();
        buffer.append("crap");
        final ParserCursor cursor3 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor3));
        buffer.clear();
        buffer.append("HTTP/crap");
        final ParserCursor cursor4 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor4));
        buffer.clear();
        buffer.append("HTTP/1");
        final ParserCursor cursor5 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor5));
        buffer.clear();
        buffer.append("HTTP/1234");
        final ParserCursor cursor6 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor6));
        buffer.clear();
        buffer.append("HTTP/1.");
        final ParserCursor cursor7 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor7));
        buffer.clear();
        buffer.append("HTTP/whatever.whatever whatever");
        final ParserCursor cursor8 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor8));
        buffer.clear();
        buffer.append("HTTP/1.whatever whatever");
        final ParserCursor cursor9 = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor9));
    }

    @Test
    public void testHeaderParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical request line
        buf.clear();
        buf.append("header: blah");
        Header header = this.parser.parseHeader(buf);
        Assert.assertEquals("header", header.getName());
        Assert.assertEquals("blah", header.getValue());

        //Lots of blanks
        buf.clear();
        buf.append("    header:    blah    ");
        header = this.parser.parseHeader(buf);
        Assert.assertEquals("header", header.getName());
        Assert.assertEquals("blah", header.getValue());
    }

    @Test
    public void testInvalidHeaderParsing() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.clear();
        buffer.append("");
        Assert.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append("blah");
        Assert.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append(":");
        Assert.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append("   :");
        Assert.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append(": blah");
        Assert.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append(" : blah");
        Assert.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append("header : blah");
        Assert.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
    }

}
