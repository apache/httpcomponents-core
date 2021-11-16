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
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultHttpResponseParser}.
 */
public class TestResponseParser {

    @Test
    public void testBasicMessageParsing() throws Exception {
        final String s =
            "HTTP/1.1 200 OK\r\n" +
            "Server: whatever\r\n" +
            "Date: some date\r\n" +
            "Set-Cookie: c1=stuff\r\n" +
            "\r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());

        final DefaultHttpResponseParser parser = new DefaultHttpResponseParser();
        final ClassicHttpResponse httpresponse = parser.parse(inBuffer, inputStream);

        Assertions.assertEquals(200, httpresponse.getCode());
        Assertions.assertEquals("OK", httpresponse.getReasonPhrase());
        final Header[] headers = httpresponse.getHeaders();
        Assertions.assertEquals(3, headers.length);
    }

    @Test
    public void testConnectionClosedException() throws Exception {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {});
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);

        final DefaultHttpResponseParser parser = new DefaultHttpResponseParser();
        final ClassicHttpResponse response = parser.parse(inBuffer, inputStream);
        Assertions.assertNull(response);
    }

    @Test
    public void testBasicMessageParsingLeadingEmptyLines() throws Exception {
        final String s =
                "\r\n" +
                "\r\n" +
                "HTTP/1.1 200 OK\r\n" +
                "Server: whatever\r\n" +
                "\r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());

        final DefaultHttpResponseParser parser = new DefaultHttpResponseParser(
                Http1Config.custom().setMaxEmptyLineCount(3).build());
        final ClassicHttpResponse httpresponse = parser.parse(inBuffer, inputStream);

        Assertions.assertEquals(200, httpresponse.getCode());
        Assertions.assertEquals("OK", httpresponse.getReasonPhrase());
        final Header[] headers = httpresponse.getHeaders();
        Assertions.assertEquals(1, headers.length);
    }

    @Test
    public void testBasicMessageParsingTooManyLeadingEmptyLines() throws Exception {
        final String s =
                "\r\n" +
                "\r\n" +
                "\r\n" +
                "HTTP/1.1 200 OK\r\n" +
                "Server: whatever\r\n" +
                "\r\n";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16, StandardCharsets.US_ASCII.newDecoder());

        final DefaultHttpResponseParser parser = new DefaultHttpResponseParser(
                Http1Config.custom().setMaxEmptyLineCount(3).build());
        Assertions.assertThrows(MessageConstraintException.class, () ->
                parser.parse(inBuffer, inputStream));
    }

    @Test
    public void testMessageParsingTimeout() throws Exception {
        final String s =
            "HTTP\000/1.1 200\000 OK\r\n" +
            "Server: wha\000tever\r\n" +
            "Date: some date\r\n" +
            "Set-Coo\000kie: c1=stuff\r\n" +
            "\000\r\n";
        final TimeoutByteArrayInputStream inputStream = new TimeoutByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);

        final DefaultHttpResponseParser parser = new DefaultHttpResponseParser();

        int timeoutCount = 0;

        ClassicHttpResponse httpresponse = null;
        for (int i = 0; i < 10; i++) {
            try {
                httpresponse = parser.parse(inBuffer, inputStream);
                break;
            } catch (final InterruptedIOException ex) {
                timeoutCount++;
            }
        }
        Assertions.assertNotNull(httpresponse);
        Assertions.assertEquals(5, timeoutCount);

        Assertions.assertEquals(200, httpresponse.getCode());
        Assertions.assertEquals("OK", httpresponse.getReasonPhrase());
        final Header[] headers = httpresponse.getHeaders();
        Assertions.assertEquals(3, headers.length);
    }

}

