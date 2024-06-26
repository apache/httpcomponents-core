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
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicLineFormatter}.
 */
class TestBasicLineFormatter {

    private BasicLineFormatter formatter;

    @BeforeEach
    void setup() {
        this.formatter = BasicLineFormatter.INSTANCE;
    }

    @Test
    void testHttpVersionFormatting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        this.formatter.formatProtocolVersion(buf, HttpVersion.HTTP_1_1);
        Assertions.assertEquals("HTTP/1.1", buf.toString());
    }

    @Test
    void testRLFormatting() {
        final RequestLine requestline = new RequestLine(Method.GET.name(), "/stuff", HttpVersion.HTTP_1_1);
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        this.formatter.formatRequestLine(buf, requestline);
        Assertions.assertEquals("GET /stuff HTTP/1.1", buf.toString());
    }

    @Test
    void testRLFormattingInvalidInput() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final RequestLine requestline = new RequestLine(Method.GET.name(), "/stuff", HttpVersion.HTTP_1_1);
        Assertions.assertThrows(NullPointerException.class, () ->
                formatter.formatRequestLine(null, requestline));
        Assertions.assertThrows(NullPointerException.class, () ->
                formatter.formatRequestLine(buf, null));
    }

    @Test
    void testSLFormatting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final StatusLine statusline1 = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        this.formatter.formatStatusLine(buf, statusline1);
        Assertions.assertEquals("HTTP/1.1 200 OK", buf.toString());

        buf.clear();
        final StatusLine statusline2 = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        this.formatter.formatStatusLine(buf, statusline2);
        Assertions.assertEquals("HTTP/1.1 200 ", buf.toString());
        // see "testSLParseSuccess" in TestBasicLineParser:
        // trailing space is correct
    }

    @Test
    void testSLFormattingInvalidInput() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Assertions.assertThrows(NullPointerException.class, () ->
                formatter.formatStatusLine(null, statusline));
        Assertions.assertThrows(NullPointerException.class, () ->
                formatter.formatStatusLine(buf, null));
    }

    @Test
    void testHeaderFormatting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final Header header1 = new BasicHeader("name", "value");
        this.formatter.formatHeader(buf, header1);
        Assertions.assertEquals("name: value", buf.toString());

        buf.clear();
        final Header header2 = new BasicHeader("name", null);
        this.formatter.formatHeader(buf, header2);
        Assertions.assertEquals("name: ", buf.toString());
    }

    @Test
    void testHeaderFormattingInvalidInput() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final Header header = new BasicHeader("name", "value");
        Assertions.assertThrows(NullPointerException.class, () ->
                formatter.formatHeader(null, header));
        Assertions.assertThrows(NullPointerException.class, () ->
                formatter.formatHeader(buf, null));
    }

    @Test
    void testHeaderFormattingRequestSplitting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final Header header = new BasicHeader("Host", "apache.org\r\nOops: oops");
        formatter.formatHeader(buf, header);
        final String s = buf.toString();
        Assertions.assertFalse(s.contains("\n"));
        Assertions.assertEquals("Host: apache.org  Oops: oops", s);
    }
}
