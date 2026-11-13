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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicLineParser}.
 *
 */
class TestBasicLineParser {

    private BasicLineParser parser;

    @BeforeEach
    void setup() {
        this.parser = BasicLineParser.INSTANCE;
    }

    @Test
    void testRLParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical request line
        buf.clear();
        buf.append("GET /stuff HTTP/1.1");
        RequestLine requestline = this.parser.parseRequestLine(buf);
        Assertions.assertThat(requestline.toString()).isEqualTo("GET /stuff HTTP/1.1");
        Assertions.assertThat(requestline.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(requestline.getUri()).isEqualTo("/stuff");
        Assertions.assertThat(requestline.getProtocolVersion()).isEqualTo(HttpVersion.HTTP_1_1);

        //Lots of blanks
        buf.clear();
        buf.append("  GET    /stuff   HTTP/1.1   ");
        requestline = this.parser.parseRequestLine(buf);
        Assertions.assertThat(requestline.toString()).isEqualTo("GET /stuff HTTP/1.1");
        Assertions.assertThat(requestline.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(requestline.getUri()).isEqualTo("/stuff");
        Assertions.assertThat(requestline.getProtocolVersion()).isEqualTo(HttpVersion.HTTP_1_1);

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("\rGET /stuff HTTP/1.1");
        requestline = this.parser.parseRequestLine(buf);
        Assertions.assertThat(requestline.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(requestline.getUri()).isEqualTo("/stuff");
        Assertions.assertThat(requestline.getProtocolVersion()).isEqualTo(HttpVersion.HTTP_1_1);
    }

    @Test
    void testRLParseFailure() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.clear();
        buf.append("    ");
        Assertions.assertThatThrownBy(() ->
                        parser.parseRequestLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("  GET");
        Assertions.assertThatThrownBy(() ->
                        parser.parseRequestLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("GET /stuff");
        Assertions.assertThatThrownBy(() ->
                        parser.parseRequestLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("GET/stuff HTTP/1.1");
        Assertions.assertThatThrownBy(() ->
                        parser.parseRequestLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("GET /stuff HTTP/1.1 Oooooooooooppsie");
        Assertions.assertThatThrownBy(() ->
                        parser.parseRequestLine(buf))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void testSLParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical status line
        buf.clear();
        buf.append("HTTP/1.1 200 OK");
        StatusLine statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.toString()).isEqualTo("HTTP/1.1 200 OK");
        Assertions.assertThat(statusLine.getProtocolVersion()).isEqualTo(HttpVersion.HTTP_1_1);
        Assertions.assertThat(statusLine.getStatusCode()).isEqualTo(200);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("OK");

        //status line with multi word reason phrase
        buf.clear();
        buf.append("HTTP/1.1 404 Not Found");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getStatusCode()).isEqualTo(404);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("Not Found");

        //reason phrase can be anyting
        buf.clear();
        buf.append("HTTP/1.1 404 Non Trouve");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("Non Trouve");

        //its ok to end with a \n\r
        buf.clear();
        buf.append("HTTP/1.1 404 Not Found\r\n");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("Not Found");

        //this is valid according to the Status-Line BNF
        buf.clear();
        buf.append("HTTP/1.1 200 ");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getStatusCode()).isEqualTo(200);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("");

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("HTTP/1.1 200");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getStatusCode()).isEqualTo(200);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("");

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("HTTP/1.1     200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getStatusCode()).isEqualTo(200);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("OK");

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("\nHTTP/1.1 200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getStatusCode()).isEqualTo(200);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("OK");
        Assertions.assertThat(statusLine.getProtocolVersion()).isEqualTo(HttpVersion.HTTP_1_1);

        //this is not strictly valid, but is lenient
        buf.clear();
        buf.append("  HTTP/1.1 200 OK");
        statusLine = this.parser.parseStatusLine(buf);
        Assertions.assertThat(statusLine.getStatusCode()).isEqualTo(200);
        Assertions.assertThat(statusLine.getReasonPhrase()).isEqualTo("OK");
        Assertions.assertThat(statusLine.getProtocolVersion()).isEqualTo(HttpVersion.HTTP_1_1);
    }

    @Test
    void testSLParseFailure() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.clear();
        buf.append("xxx 200 OK");
        Assertions.assertThatThrownBy(() ->
                        parser.parseStatusLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("HTTP/1.1 xxx OK");
        Assertions.assertThatThrownBy(() ->
                        parser.parseStatusLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("HTTP/1.1    ");
        Assertions.assertThatThrownBy(() ->
                        parser.parseStatusLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("HTTP/1.1");
        Assertions.assertThatThrownBy(() ->
                        parser.parseStatusLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("HTTP/1.1 -200 OK");
        Assertions.assertThatThrownBy(() ->
                        parser.parseStatusLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("HTTP/1.1 0200 OK");
        Assertions.assertThatThrownBy(() ->
                        parser.parseStatusLine(buf))
                .isInstanceOf(ParseException.class);
        buf.clear();
        buf.append("HTTP/1.1 2000 OK");
        Assertions.assertThatThrownBy(() ->
                        parser.parseStatusLine(buf))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void testHttpVersionParsing() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("HTTP/1.1");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());

        final ProtocolVersion version = parser.parseProtocolVersion(buffer, cursor);
        Assertions.assertThat(version.getProtocol()).withFailMessage("HTTP protocol name").isEqualTo("HTTP");
        Assertions.assertThat(version.getMajor()).withFailMessage("HTTP major version number").isEqualTo(1);
        Assertions.assertThat(version.getMinor()).withFailMessage("HTTP minor version number").isEqualTo(1);
        Assertions.assertThat(version.toString()).withFailMessage("HTTP version number").isEqualTo("HTTP/1.1");
        Assertions.assertThat(cursor.getPos()).isEqualTo(buffer.length());
        Assertions.assertThat(cursor.atEnd()).isTrue();

        buffer.clear();
        buffer.append("HTTP/1.123 123");
        final ParserCursor cursor2 = new ParserCursor(0, buffer.length());

        final ProtocolVersion version2 = parser.parseProtocolVersion(buffer, cursor2);
        Assertions.assertThat(version2.getProtocol()).withFailMessage("HTTP protocol name").isEqualTo("HTTP");
        Assertions.assertThat(version2.getMajor()).withFailMessage("HTTP major version number").isEqualTo(1);
        Assertions.assertThat(version2.getMinor()).withFailMessage("HTTP minor version number").isEqualTo(123);
        Assertions.assertThat(version2.toString()).withFailMessage("HTTP version number").isEqualTo("HTTP/1.123");
        Assertions.assertThat(buffer.charAt(cursor2.getPos())).isEqualTo(' ');
        Assertions.assertThat(cursor2.getPos()).isEqualTo(buffer.length() - 4);
        Assertions.assertThat(cursor2.atEnd()).isFalse();
    }

    @Test
    void testInvalidHttpVersionParsing() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.clear();
        buffer.append("    ");
        final ParserCursor cursor1 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor1)).isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("HTT");
        final ParserCursor cursor2 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor2)).isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("crap");
        final ParserCursor cursor3 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor3)).isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("HTTP/crap");
        final ParserCursor cursor4 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor4)).isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("HTTP/1");
        final ParserCursor cursor5 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor5)).isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("HTTP/1.");
        final ParserCursor cursor7 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor7)).isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("HTTP/whatever.whatever whatever");
        final ParserCursor cursor8 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor8)).isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("HTTP/1.whatever whatever");
        final ParserCursor cursor9 = new ParserCursor(0, buffer.length());
        Assertions.assertThatThrownBy(() ->
                parser.parseProtocolVersion(buffer, cursor9)).isInstanceOf(ParseException.class);
    }

    @Test
    void testHeaderParse() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        //typical request line
        buf.clear();
        buf.append("header: blah");
        Header header = this.parser.parseHeader(buf);
        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(header.getValue()).isEqualTo("blah");

        //Lots of blanks
        buf.clear();
        buf.append("    header:    blah    ");
        header = this.parser.parseHeader(buf);
        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(header.getValue()).isEqualTo("blah");
    }

    @Test
    void testInvalidHeaderParsing() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.clear();
        buffer.append("");
        Assertions.assertThatThrownBy(() ->
                        parser.parseHeader(buffer))
                .isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("blah");
        Assertions.assertThatThrownBy(() ->
                        parser.parseHeader(buffer))
                .isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append(":");
        Assertions.assertThatThrownBy(() ->
                        parser.parseHeader(buffer))
                .isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("   :");
        Assertions.assertThatThrownBy(() ->
                        parser.parseHeader(buffer))
                .isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append(": blah");
        Assertions.assertThatThrownBy(() ->
                        parser.parseHeader(buffer))
                .isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append(" : blah");
        Assertions.assertThatThrownBy(() ->
                        parser.parseHeader(buffer))
                .isInstanceOf(ParseException.class);
        buffer.clear();
        buffer.append("header : blah");
        Assertions.assertThatThrownBy(() ->
                        parser.parseHeader(buffer))
                .isInstanceOf(ParseException.class);
    }

}
