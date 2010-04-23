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

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.util.CharArrayBuffer;

/**
 * Tests for {@link BasicLineFormatter}.
 *
 *
 */
public class TestBasicLineFormatter extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicLineFormatter(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testHttpVersionFormatting() throws Exception {
        String s = BasicLineFormatter.formatProtocolVersion
            (HttpVersion.HTTP_1_1, null);
        assertEquals("HTTP/1.1", s);
    }

    public void testHttpVersionFormattingInvalidInput() throws Exception {
        try {
            BasicLineFormatter.formatProtocolVersion
                (null, BasicLineFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineFormatter.DEFAULT.appendProtocolVersion
                (new CharArrayBuffer(10), (HttpVersion) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }


    public void testRLFormatting() throws Exception {
        RequestLine requestline = new BasicRequestLine("GET", "/stuff", HttpVersion.HTTP_1_1);
        String s = BasicLineFormatter.formatRequestLine(requestline, null);
        assertEquals("GET /stuff HTTP/1.1", s);
    }

    public void testRLFormattingInvalidInput() throws Exception {
        try {
            BasicLineFormatter.formatRequestLine
                (null, BasicLineFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineFormatter.DEFAULT.formatRequestLine
                (new CharArrayBuffer(10), (RequestLine) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }



    public void testSLFormatting() throws Exception {
        StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        String s = BasicLineFormatter.formatStatusLine(statusline, null);
        assertEquals("HTTP/1.1 200 OK", s);
        statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
        s = BasicLineFormatter.formatStatusLine(statusline, null);
        assertEquals("HTTP/1.1 200 ", s);
        // see "testSLParseSuccess" in TestBasicLineParser:
        // trailing space is correct
    }

    public void testSLFormattingInvalidInput() throws Exception {
        try {
            BasicLineFormatter.formatStatusLine
                (null, BasicLineFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineFormatter.DEFAULT.formatStatusLine
                (new CharArrayBuffer(10), (StatusLine) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }


    public void testHeaderFormatting() throws Exception {
        Header header1 = new BasicHeader("name", "value");
        String s = BasicLineFormatter.formatHeader(header1, null);
        assertEquals("name: value", s);
        Header header2 = new BasicHeader("name", null);
        s = BasicLineFormatter.formatHeader(header2, null);
        assertEquals("name: ", s);
    }

    public void testHeaderFormattingInvalidInput() throws Exception {
        try {
            BasicLineFormatter.formatHeader
                (null, BasicLineFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineFormatter.DEFAULT.formatHeader
                (new CharArrayBuffer(10), (Header) null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
