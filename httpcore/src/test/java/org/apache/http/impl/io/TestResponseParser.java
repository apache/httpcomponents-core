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

import java.io.InterruptedIOException;

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicLineParser;
import org.apache.http.mockup.SessionInputBufferMockup;
import org.apache.http.mockup.TimeoutByteArrayInputStream;
import org.apache.http.params.BasicHttpParams;

/**
 * Unit tests for {@link HttpResponseParser}.
 */
public class TestResponseParser extends TestCase {

    public TestResponseParser(String testName) {
        super(testName);
    }

    public void testInvalidConstructorInput() throws Exception {
        try {
            new HttpResponseParser(
                    null,
                    BasicLineParser.DEFAULT,
                    new DefaultHttpResponseFactory(),
                    new BasicHttpParams());
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            SessionInputBuffer inbuffer = new SessionInputBufferMockup(new byte[] {});
            new HttpResponseParser(
                    inbuffer,
                    BasicLineParser.DEFAULT,
                    null,
                    new BasicHttpParams());
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            SessionInputBuffer inbuffer = new SessionInputBufferMockup(new byte[] {});
            new HttpResponseParser(
                    inbuffer,
                    BasicLineParser.DEFAULT,
                    new DefaultHttpResponseFactory(),
                    null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testBasicMessageParsing() throws Exception {
        String s =
            "HTTP/1.1 200 OK\r\n" +
            "Server: whatever\r\n" +
            "Date: some date\r\n" +
            "Set-Cookie: c1=stuff\r\n" +
            "\r\n";
        SessionInputBuffer inbuffer = new SessionInputBufferMockup(s, "US-ASCII");

        HttpResponseParser parser = new HttpResponseParser(
                inbuffer,
                BasicLineParser.DEFAULT,
                new DefaultHttpResponseFactory(),
                new BasicHttpParams());

        HttpResponse httpresponse = (HttpResponse) parser.parse();

        StatusLine statusline = httpresponse.getStatusLine();
        assertNotNull(statusline);
        assertEquals(200, statusline.getStatusCode());
        assertEquals("OK", statusline.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusline.getProtocolVersion());
        Header[] headers = httpresponse.getAllHeaders();
        assertEquals(3, headers.length);
    }

    public void testConnectionClosedException() throws Exception {
        SessionInputBuffer inbuffer = new SessionInputBufferMockup(new byte[] {});

        HttpResponseParser parser = new HttpResponseParser(
                inbuffer,
                BasicLineParser.DEFAULT,
                new DefaultHttpResponseFactory(),
                new BasicHttpParams());

        try {
            parser.parse();
            fail("NoHttpResponseException should have been thrown");
        } catch (NoHttpResponseException expected) {
        }
    }

    public void testMessageParsingTimeout() throws Exception {
        String s =
            "HTTP\000/1.1 200\000 OK\r\n" +
            "Server: wha\000tever\r\n" +
            "Date: some date\r\n" +
            "Set-Coo\000kie: c1=stuff\r\n" +
            "\000\r\n";
        SessionInputBuffer inbuffer = new SessionInputBufferMockup(
                new TimeoutByteArrayInputStream(s.getBytes("US-ASCII")), 16);

        HttpResponseParser parser = new HttpResponseParser(
                inbuffer,
                BasicLineParser.DEFAULT,
                new DefaultHttpResponseFactory(),
                new BasicHttpParams());

        int timeoutCount = 0;

        HttpResponse httpresponse = null;
        for (int i = 0; i < 10; i++) {
            try {
                httpresponse = (HttpResponse) parser.parse();
                break;
            } catch (InterruptedIOException ex) {
                timeoutCount++;
            }

        }
        assertNotNull(httpresponse);
        assertEquals(5, timeoutCount);

        StatusLine statusline = httpresponse.getStatusLine();
        assertNotNull(statusline);
        assertEquals(200, statusline.getStatusCode());
        assertEquals("OK", statusline.getReasonPhrase());
        assertEquals(HttpVersion.HTTP_1_1, statusline.getProtocolVersion());
        Header[] headers = httpresponse.getAllHeaders();
        assertEquals(3, headers.length);
    }

}

