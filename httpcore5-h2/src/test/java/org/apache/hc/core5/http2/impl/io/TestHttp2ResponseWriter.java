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
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http2.hpack.HPackDecoder;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestHttp2ResponseWriter {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testBasicWrite() throws Exception {

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader("Custom123", "Value");

        final Http2ResponseWriter writer = new Http2ResponseWriter(StandardCharsets.US_ASCII);
        writer.write(response, buf);

        final ByteBuffer src = ByteBuffer.wrap(buf.buffer(), 0, buf.length());
        final HPackDecoder decoder = new HPackDecoder(StandardCharsets.US_ASCII);
        final List<Header> headers = decoder.decodeHeaders(src);

        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.size());
        final Header header1 = headers.get(0);
        Assert.assertEquals(":status", header1.getName());
        Assert.assertEquals("200", header1.getValue());
        final Header header2 = headers.get(1);
        Assert.assertEquals("custom123", header2.getName());
        Assert.assertEquals("Value", header2.getValue());
    }

    @Test
    public void testWriteInvalidStatus() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Response status 99 is invalid");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpResponse response = new BasicHttpResponse(99);
        response.addHeader("Custom123", "Value");

        final Http2ResponseWriter writer = new Http2ResponseWriter(StandardCharsets.US_ASCII);
        writer.write(response, buf);
    }

    @Test
    public void testWriteConnectionHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header 'Connection: Keep-Alive' is illegal for HTTP/2 messages");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader("Connection", "Keep-Alive");

        final Http2ResponseWriter writer = new Http2ResponseWriter(StandardCharsets.US_ASCII);
        writer.write(response, buf);
    }

    @Test
    public void testWriteInvalidHeader() throws Exception {

        thrown.expect(HttpException.class);
        thrown.expectMessage("Header name ':custom' is invalid");

        final ByteArrayBuffer buf = new ByteArrayBuffer(128);

        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(":custom", "stuff");

        final Http2ResponseWriter writer = new Http2ResponseWriter(StandardCharsets.US_ASCII);
        writer.write(response, buf);
    }

}

