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

package org.apache.hc.core5.http2.impl.io;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http2.hpack.HPackDecoder;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestHttp2RequestWriter {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testBasicWrite() throws Exception {

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);

        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());
        final HPackDecoder decoder = new HPackDecoder(StandardCharsets.US_ASCII);
        final List<Header> headers = decoder.decodeHeaders(src);

        Assert.assertNotNull(headers);
        Assert.assertEquals(5, headers.size());
        final Header header1 = headers.get(0);
        Assert.assertEquals(":method", header1.getName());
        Assert.assertEquals("GET", header1.getValue());
        final Header header2 = headers.get(1);
        Assert.assertEquals(":scheme", header2.getName());
        Assert.assertEquals("http", header2.getValue());
        final Header header3 = headers.get(2);
        Assert.assertEquals(":authority", header3.getName());
        Assert.assertEquals("host", header3.getValue());
        final Header header4 = headers.get(3);
        Assert.assertEquals(":path", header4.getName());
        Assert.assertEquals("/", header4.getValue());
        final Header header5 = headers.get(4);
        Assert.assertEquals("custom123", header5.getName());
        Assert.assertEquals("Value", header5.getValue());
    }

    @Test
    public void testWriteMissingScheme() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Request scheme is not set");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setScheme(null);

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);
    }

    @Test
    public void testWriteMissingPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Request path is not set");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Custom123", "Value");
        request.setPath(null);

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);
    }

    @Test
    public void testWriteConnect() throws Exception {

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("CONNECT", new HttpHost("host:80"), null);
        request.addHeader("Custom123", "Value");

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);

        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());
        final HPackDecoder decoder = new HPackDecoder(StandardCharsets.US_ASCII);
        final List<Header> headers = decoder.decodeHeaders(src);

        Assert.assertNotNull(headers);
        Assert.assertEquals(3, headers.size());
        final Header header1 = headers.get(0);
        Assert.assertEquals(":method", header1.getName());
        Assert.assertEquals("CONNECT", header1.getValue());
        final Header header2 = headers.get(1);
        Assert.assertEquals(":authority", header2.getName());
        Assert.assertEquals("host:80", header2.getValue());
        final Header header3 = headers.get(2);
        Assert.assertEquals("custom123", header3.getName());
        Assert.assertEquals("Value", header3.getValue());
    }

    @Test
    public void testWriteConnectMissingAuthority() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("CONNECT request authority is not set");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("CONNECT", null, null);
        request.addHeader("Custom123", "Value");

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);
    }

    @Test
    public void testWriteConnectWithPath() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("CONNECT request path must be null");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("CONNECT", "/");
        request.setAuthority("host");
        request.addHeader("Custom123", "Value");

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);
    }

    @Test
    public void testWriteConnectionHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader("Connection", "Keep-Alive");

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);
    }

    @Test
    public void testWriteInvalidHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header name ':custom' is invalid");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");
        request.addHeader(":custom", "stuff");

        final Http2RequestWriter writer = new Http2RequestWriter(StandardCharsets.US_ASCII);
        writer.write(request, buf);
    }

}

