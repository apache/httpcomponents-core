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

package org.apache.http.impl.io;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BufferedHeader;
import org.apache.http.mockup.SessionInputBufferMockup;

/**
 * Unit tests for {@link AbstractMessageParser}.
 */
public class TestMessageParser extends TestCase {

    public TestMessageParser(String testName) {
        super(testName);
    }

    public void testInvalidInput() throws Exception {
        try {
            // the first argument must not be null
            AbstractMessageParser.parseHeaders(null, -1, -1, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            new BufferedHeader(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testBasicHeaderParsing() throws Exception {
        String s =
            "header1: stuff\r\n" +
            "header2  : stuff \r\n" +
            "header3: stuff\r\n" +
            "     and more stuff\r\n" +
            "\t and even more stuff\r\n" +
            "     \r\n" +
            "\r\n";
        SessionInputBuffer receiver = new SessionInputBufferMockup(s, "US-ASCII");
        Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        assertNotNull(headers);
        assertEquals(3, headers.length);
        assertEquals("header1", headers[0].getName());
        assertEquals("stuff", headers[0].getValue());
        assertEquals("header2", headers[1].getName());
        assertEquals("stuff", headers[1].getValue());
        assertEquals("header3", headers[2].getName());
        assertEquals("stuff and more stuff and even more stuff", headers[2].getValue());

        Header h = headers[0];

        assertTrue(h instanceof BufferedHeader);
        assertNotNull(((BufferedHeader)h).getBuffer());
        assertEquals("header1: stuff", ((BufferedHeader)h).toString());
        assertEquals(8, ((BufferedHeader)h).getValuePos());
    }

    public void testBufferedHeader() throws Exception {
        String s =
            "header1  : stuff; param1 = value1; param2 = \"value 2\" \r\n" +
            "\r\n";
        SessionInputBuffer receiver = new SessionInputBufferMockup(s, "US-ASCII");
        Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals("header1  : stuff; param1 = value1; param2 = \"value 2\" ", headers[0].toString());
        HeaderElement[] elements = headers[0].getElements();
        assertNotNull(elements);
        assertEquals(1, elements.length);
        assertEquals("stuff", elements[0].getName());
        assertEquals(null, elements[0].getValue());
        NameValuePair[] params = elements[0].getParameters();
        assertNotNull(params);
        assertEquals(2, params.length);
        assertEquals("param1", params[0].getName());
        assertEquals("value1", params[0].getValue());
        assertEquals("param2", params[1].getName());
        assertEquals("value 2", params[1].getValue());
    }

    public void testParsingInvalidHeaders() throws Exception {
        String s = "    stuff\r\n" +
            "header1: stuff\r\n" +
            "\r\n";
        SessionInputBuffer receiver = new SessionInputBufferMockup(s, "US-ASCII");
        try {
            AbstractMessageParser.parseHeaders(receiver, -1, -1, null);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
        s = "  :  stuff\r\n" +
            "header1: stuff\r\n" +
            "\r\n";
        receiver = new SessionInputBufferMockup(s, "US-ASCII");
        try {
            AbstractMessageParser.parseHeaders(receiver, -1, -1, null);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testParsingMalformedFirstHeader() throws Exception {
        String s =
            "    header1: stuff\r\n" +
            "header2  : stuff \r\n";
        SessionInputBuffer receiver = new SessionInputBufferMockup(s, "US-ASCII");
        Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        assertNotNull(headers);
        assertEquals(2, headers.length);
        assertEquals("header1", headers[0].getName());
        assertEquals("stuff", headers[0].getValue());
        assertEquals("header2", headers[1].getName());
        assertEquals("stuff", headers[1].getValue());
    }

    public void testEmptyDataStream() throws Exception {
        String s = "";
        SessionInputBuffer receiver = new SessionInputBufferMockup(s, "US-ASCII");
        Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        assertNotNull(headers);
        assertEquals(0, headers.length);
    }

    public void testMaxHeaderCount() throws Exception {
        String s =
            "header1: stuff\r\n" +
            "header2: stuff \r\n" +
            "header3: stuff\r\n" +
            "\r\n";
        SessionInputBuffer receiver = new SessionInputBufferMockup(s, "US-ASCII");
        try {
            AbstractMessageParser.parseHeaders(receiver, 2, -1, null);
            fail("IOException should have been thrown");
        } catch (IOException ex) {
            // expected
        }
    }

    public void testMaxHeaderCountForFoldedHeader() throws Exception {
        String s =
            "header1: stuff\r\n" +
            " stuff \r\n" +
            " stuff\r\n" +
            "\r\n";
        SessionInputBuffer receiver = new SessionInputBufferMockup(s, "US-ASCII");
        try {
            AbstractMessageParser.parseHeaders(receiver, 2, 15, null);
            fail("IOException should have been thrown");
        } catch (IOException ex) {
            // expected
        }
    }

}

