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

package org.apache.hc.core5.http.impl.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AbstractMessageParser}.
 */
class TestMessageParser {

    @Test
    void testBasicHeaderParsing() throws Exception {
        final String s =
            "header1: stuff\r\n" +
            "header2:  stuff \r\n" +
            "header3: stuff\r\n" +
            "     and more stuff\r\n" +
            "\t and even more stuff\r\n" +
            "     \r\n" +
            "\r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        final Header[] headers = AbstractMessageParser.parseHeaders(inBuffer, inputStream, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(3, headers.length);
        Assertions.assertEquals("header1", headers[0].getName());
        Assertions.assertEquals("stuff", headers[0].getValue());
        Assertions.assertEquals("header2", headers[1].getName());
        Assertions.assertEquals("stuff", headers[1].getValue());
        Assertions.assertEquals("header3", headers[2].getName());
        Assertions.assertEquals("stuff and more stuff and even more stuff", headers[2].getValue());
    }

    @Test
    void testParsingHeader() throws Exception {
        final String s = "header1: stuff; param1 = value1; param2 = \"value 2\" \r\n" +
                "\r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        final Header[] headers = AbstractMessageParser.parseHeaders(inBuffer, inputStream, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(1, headers.length);
        Assertions.assertEquals("header1: stuff; param1 = value1; param2 = \"value 2\" ", headers[0].toString());
    }

    @Test
    void testParsingInvalidHeaders() {
        final String s1 = "    stuff\r\n" +
            "header1: stuff\r\n" +
            "\r\n";
        final ByteArrayInputStream inputStream1 = new ByteArrayInputStream(s1.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer1 = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        Assertions.assertThrows(ProtocolException.class, () ->
                AbstractMessageParser.parseHeaders(inBuffer1, inputStream1, -1, -1, null));
        final String s2 = "  :  stuff\r\n" +
            "header1: stuff\r\n" +
            "\r\n";
        final ByteArrayInputStream inputStream2 = new ByteArrayInputStream(s2.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer2 = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        Assertions.assertThrows(ProtocolException.class, () ->
                AbstractMessageParser.parseHeaders(inBuffer2, inputStream2, -1, -1, null));
    }

    @Test
    void testParsingMalformedFirstHeader() throws Exception {
        final String s =
            "    header1: stuff\r\n" +
            "header2: stuff \r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        final Header[] headers = AbstractMessageParser.parseHeaders(inBuffer, inputStream, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(2, headers.length);
        Assertions.assertEquals("header1", headers[0].getName());
        Assertions.assertEquals("stuff", headers[0].getValue());
        Assertions.assertEquals("header2", headers[1].getName());
        Assertions.assertEquals("stuff", headers[1].getValue());
    }

    @Test
    void testEmptyDataStream() throws Exception {
        final String s = "";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        final Header[] headers = AbstractMessageParser.parseHeaders(inBuffer, inputStream, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(0, headers.length);
    }

    @Test
    void testMaxHeaderCount() {
        final String s =
            "header1: stuff\r\n" +
            "header2: stuff \r\n" +
            "header3: stuff\r\n" +
            "\r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        Assertions.assertThrows(IOException.class, () ->
                AbstractMessageParser.parseHeaders(inBuffer, inputStream, 2, -1, null));
    }

    @Test
    void testMaxHeaderCountForFoldedHeader() {
        final String s =
            "header1: stuff\r\n" +
            " stuff \r\n" +
            " stuff\r\n" +
            "\r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());
        Assertions.assertThrows(IOException.class, () ->
                AbstractMessageParser.parseHeaders(inBuffer, inputStream, 2, 15, null));
    }

}

