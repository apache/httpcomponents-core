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
import org.assertj.core.api.Assertions;
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
        Assertions.assertThat(buf.toString()).isEqualTo("HTTP/1.1");
    }

    @Test
    void testRLFormatting() {
        final RequestLine requestline = new RequestLine(Method.GET.name(), "/stuff", HttpVersion.HTTP_1_1);
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        this.formatter.formatRequestLine(buf, requestline);
        Assertions.assertThat(buf.toString()).isEqualTo("GET /stuff HTTP/1.1");
    }

    @Test
    void testRLFormattingInvalidInput() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final RequestLine requestline = new RequestLine(Method.GET.name(), "/stuff", HttpVersion.HTTP_1_1);
        Assertions.assertThatThrownBy(() ->
                formatter.formatRequestLine(null, requestline)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatRequestLine(buf, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSLFormatting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final StatusLine statusline1 = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        this.formatter.formatStatusLine(buf, statusline1);
        Assertions.assertThat(buf.toString()).isEqualTo("HTTP/1.1 200 OK");

        buf.clear();
        final StatusLine statusline2 = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        this.formatter.formatStatusLine(buf, statusline2);
        Assertions.assertThat(buf.toString()).isEqualTo("HTTP/1.1 200 ");
        // see "testSLParseSuccess" in TestBasicLineParser:
        // trailing space is correct
    }

    @Test
    void testSLFormattingInvalidInput() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Assertions.assertThatThrownBy(() ->
                formatter.formatStatusLine(null, statusline)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatStatusLine(buf, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHeaderFormatting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final Header header1 = new BasicHeader("name", "value");
        this.formatter.formatHeader(buf, header1);
        Assertions.assertThat(buf.toString()).isEqualTo("name: value");

        buf.clear();
        final Header header2 = new BasicHeader("name", null);
        this.formatter.formatHeader(buf, header2);
        Assertions.assertThat(buf.toString()).isEqualTo("name: ");
    }

    @Test
    void testHeaderFormattingInvalidInput() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final Header header = new BasicHeader("name", "value");
        Assertions.assertThatThrownBy(() ->
                formatter.formatHeader(null, header)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                formatter.formatHeader(buf, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHeaderFormattingRequestSplitting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        final Header header = new BasicHeader("Host", "apache.org\r\nOops: oops");
        formatter.formatHeader(buf, header);
        final String s = buf.toString();
        Assertions.assertThat(s.contains("\n")).isFalse();
        Assertions.assertThat(s).isEqualTo("Host: apache.org  Oops: oops");
    }
}
