/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.SessionInputBufferMock;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicLineParser;
import org.apache.http.params.BasicHttpParams;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DefaultHttpRequestParser}.
 */
public class TestRequestParser {

    @Test
    public void testInvalidConstructorInput() throws Exception {
        try {
            new DefaultHttpRequestParser(
                    null,
                    BasicLineParser.DEFAULT,
                    new DefaultHttpRequestFactory(),
                    new BasicHttpParams());
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            SessionInputBuffer inbuffer = new SessionInputBufferMock(new byte[] {});
            new DefaultHttpRequestParser(
                    inbuffer,
                    BasicLineParser.DEFAULT,
                    null,
                    new BasicHttpParams());
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            SessionInputBuffer inbuffer = new SessionInputBufferMock(new byte[] {});
            new DefaultHttpRequestParser(
                    inbuffer,
                    BasicLineParser.DEFAULT,
                    new DefaultHttpRequestFactory(),
                    null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasicMessageParsing() throws Exception {
        String s =
            "GET / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "User-Agent: whatever\r\n" +
            "Cookie: c1=stuff\r\n" +
            "\r\n";
        SessionInputBuffer inbuffer = new SessionInputBufferMock(s, "US-ASCII");

        DefaultHttpRequestParser parser = new DefaultHttpRequestParser(
                inbuffer,
                BasicLineParser.DEFAULT,
                new DefaultHttpRequestFactory(),
                new BasicHttpParams());

        HttpRequest httprequest = parser.parse();

        RequestLine reqline = httprequest.getRequestLine();
        Assert.assertNotNull(reqline);
        Assert.assertEquals("GET", reqline.getMethod());
        Assert.assertEquals("/", reqline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, reqline.getProtocolVersion());
        Header[] headers = httprequest.getAllHeaders();
        Assert.assertEquals(3, headers.length);
    }

    @Test
    public void testConnectionClosedException() throws Exception {
        SessionInputBuffer inbuffer = new SessionInputBufferMock(new byte[] {});

        DefaultHttpRequestParser parser = new DefaultHttpRequestParser(
                inbuffer,
                BasicLineParser.DEFAULT,
                new DefaultHttpRequestFactory(),
                new BasicHttpParams());

        try {
            parser.parse();
            Assert.fail("ConnectionClosedException should have been thrown");
        } catch (ConnectionClosedException expected) {
        }
    }

    @Test
    public void testMessageParsingTimeout() throws Exception {
        String s =
            "GET \000/ HTTP/1.1\r\000\n" +
            "Host: loca\000lhost\r\n" +
            "User-Agent: whatever\r\n" +
            "Coo\000kie: c1=stuff\r\n" +
            "\000\r\n";
        SessionInputBuffer inbuffer = new SessionInputBufferMock(
                new TimeoutByteArrayInputStream(s.getBytes("US-ASCII")), 16);

        DefaultHttpRequestParser parser = new DefaultHttpRequestParser(
                inbuffer,
                BasicLineParser.DEFAULT,
                new DefaultHttpRequestFactory(),
                new BasicHttpParams());

        int timeoutCount = 0;

        HttpRequest httprequest = null;
        for (int i = 0; i < 10; i++) {
            try {
                httprequest = parser.parse();
                break;
            } catch (InterruptedIOException ex) {
                timeoutCount++;
            }

        }
        Assert.assertNotNull(httprequest);
        Assert.assertEquals(5, timeoutCount);

        @SuppressWarnings("null") // httprequest cannot be null here
        RequestLine reqline = httprequest.getRequestLine();
        Assert.assertNotNull(reqline);
        Assert.assertEquals("GET", reqline.getMethod());
        Assert.assertEquals("/", reqline.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_1, reqline.getProtocolVersion());
        Header[] headers = httprequest.getAllHeaders();
        Assert.assertEquals(3, headers.length);
    }

}

