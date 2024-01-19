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
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicLineParser}.
 *
 */
public class TestBasicLineParser {

    private BasicLineParser parser;

    @BeforeEach
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
        Assertions.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assertions.assertEquals(Method.GET.name(), requestline.getMethod());
        Assertions.assertEquals("/stuff", requestline.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //Lots of blanks
        buf.clear();
        buf.append("  GET    /stuff   HTTP/1.1   ");
        requestline = this.parser.parseRequestLine(buf);
        Assertions.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assertions.assertEquals(Method.GET.name(), requestline.getMethod());
        Assertions.assertEquals("/stuff", requestline.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("\rGET /stuff HTTP/1.1");
        requestline = this.parser.parseRequestLine(buf);
        Assertions.assertEquals(Method.GET.name(), requestline.getMethod());
        Assertions.assertEquals("/stuff", requestline.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());
    }

    @Test
    public void testRLParseFailure() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.clear();
        buf.append("    ");
        Assertions.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("  GET");
        Assertions.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("GET /stuff");
        Assertions.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("GET/stuff HTTP/1.1");
        Assertions.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
        buf.clear();
        buf.append("GET /stuff HTTP/1.1 Oooooooooooppsie");
        Assertions.assertThrows(ParseException.class, () -> parser.parseRequestLine(buf));
    }

    @Test
    public void testSLParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical status line
        buf.clear();
        buf.append("HTTP/1.1 200 OK");
        StatusLine statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals("HTTP/1.1 200 OK", statusLine.toString());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());

        //status line with multi word reason phrase
        buf.clear();
        buf.append("HTTP/1.1 404 Not Found");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals(404, statusLine.getStatusCode());
        Assertions.assertEquals("Not Found", statusLine.getReasonPhrase());

        //reason phrase can be anyting
        buf.clear();
        buf.append("HTTP/1.1 404 Non Trouve");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals("Non Trouve", statusLine.getReasonPhrase());

        //its ok to end with a \n\r
        buf.clear();
        buf.append("HTTP/1.1 404 Not Found\r\n");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals("Not Found", statusLine.getReasonPhrase());

        //this is valid according to the Status-Line BNF
        buf.clear();
        buf.append("HTTP/1.1 200 ");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("HTTP/1.1 200");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("HTTP/1.1     200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("\nHTTP/1.1 200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("  HTTP/1.1 200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
    }

    @Test
    public void testSLParseFailure() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.clear();
        buf.append("xxx 200 OK");
        Assertions.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1 xxx OK");
        Assertions.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1    ");
        Assertions.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1");
        Assertions.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1 -200 OK");
        Assertions.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1 0200 OK");
        Assertions.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
        buf.clear();
        buf.append("HTTP/1.1 2000 OK");
        Assertions.assertThrows(ParseException.class, () -> parser.parseStatusLine(buf));
    }

    @Test
    public void testHttpVersionParsing() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("HTTP/1.1");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());

        final ProtocolVersion version = parser.parseProtocolVersion(buffer, cursor);
        Assertions.assertEquals("HTTP", version.getProtocol(), "HTTP protocol name");
        Assertions.assertEquals(1, version.getMajor(), "HTTP major version number");
        Assertions.assertEquals(1, version.getMinor(), "HTTP minor version number");
        Assertions.assertEquals("HTTP/1.1", version.toString(), "HTTP version number");
        Assertions.assertEquals(buffer.length(), cursor.getPos());
        Assertions.assertTrue(cursor.atEnd());

        buffer.clear();
        buffer.append("HTTP/1.123 123");
        final ParserCursor cursor2 = new ParserCursor(0, buffer.length());

        final ProtocolVersion version2 = parser.parseProtocolVersion(buffer, cursor2);
        Assertions.assertEquals( "HTTP", version2.getProtocol(), "HTTP protocol name");
        Assertions.assertEquals( 1, version2.getMajor(), "HTTP major version number");
        Assertions.assertEquals(123, version2.getMinor(), "HTTP minor version number");
        Assertions.assertEquals("HTTP/1.123", version2.toString(), "HTTP version number");
        Assertions.assertEquals(' ', buffer.charAt(cursor2.getPos()));
        Assertions.assertEquals(buffer.length() - 4, cursor2.getPos());
        Assertions.assertFalse(cursor2.atEnd());
    }

    @Test
    public void testInvalidHttpVersionParsing() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.clear();
        buffer.append("    ");
        final ParserCursor cursor1 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor1));
        buffer.clear();
        buffer.append("HTT");
        final ParserCursor cursor2 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor2));
        buffer.clear();
        buffer.append("crap");
        final ParserCursor cursor3 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor3));
        buffer.clear();
        buffer.append("HTTP/crap");
        final ParserCursor cursor4 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor4));
        buffer.clear();
        buffer.append("HTTP/1");
        final ParserCursor cursor5 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor5));
        buffer.clear();
        buffer.append("HTTP/1.");
        final ParserCursor cursor7 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor7));
        buffer.clear();
        buffer.append("HTTP/whatever.whatever whatever");
        final ParserCursor cursor8 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor8));
        buffer.clear();
        buffer.append("HTTP/1.whatever whatever");
        final ParserCursor cursor9 = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parseProtocolVersion(buffer, cursor9));
    }

    @Test
    public void testHeaderParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical request line
        buf.clear();
        buf.append("header: blah");
        Header header = this.parser.parseHeader(buf);
        Assertions.assertEquals("header", header.getName());
        Assertions.assertEquals("blah", header.getValue());

        //Lots of blanks
        buf.clear();
        buf.append("    header:    blah    ");
        header = this.parser.parseHeader(buf);
        Assertions.assertEquals("header", header.getName());
        Assertions.assertEquals("blah", header.getValue());
    }

    @Test
    public void testInvalidHeaderParsing() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.clear();
        buffer.append("");
        Assertions.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append("blah");
        Assertions.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append(":");
        Assertions.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append("   :");
        Assertions.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append(": blah");
        Assertions.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append(" : blah");
        Assertions.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
        buffer.clear();
        buffer.append("header : blah");
        Assertions.assertThrows(ParseException.class, () -> parser.parseHeader(buffer));
    }

}
