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

package org.apache.http.nio.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.ReadableByteChannelMock;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.io.BufferInfo;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EncodingUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Buffer tests.
 */
public class TestBuffers {

    @Test
    public void testInputBufferOperations() throws IOException {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");

        ContentDecoder decoder = new ContentDecoderMock(channel);

        SimpleInputBuffer buffer = new SimpleInputBuffer(4, new DirectByteBufferAllocator());
        int count = buffer.consumeContent(decoder);
        Assert.assertEquals(16, count);
        Assert.assertTrue(decoder.isCompleted());

        byte[] b1 = new byte[5];

        int len = buffer.read(b1);
        Assert.assertEquals("stuff", EncodingUtils.getAsciiString(b1, 0, len));

        int c = buffer.read();
        Assert.assertEquals(';', c);

        byte[] b2 = new byte[1024];

        len = buffer.read(b2);
        Assert.assertEquals("more stuff", EncodingUtils.getAsciiString(b2, 0, len));

        Assert.assertEquals(-1, buffer.read());
        Assert.assertEquals(-1, buffer.read(b2));
        Assert.assertEquals(-1, buffer.read(b2, 0, b2.length));
        Assert.assertTrue(buffer.isEndOfStream());

        buffer.reset();
        Assert.assertFalse(buffer.isEndOfStream());
    }

    @Test
    public void testOutputBufferOperations() throws IOException {
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(outstream);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        ContentEncoder encoder = new ContentEncoderMock(channel, outbuf, metrics);

        SimpleOutputBuffer buffer = new SimpleOutputBuffer(4, new DirectByteBufferAllocator());

        buffer.write(EncodingUtils.getAsciiBytes("stuff"));
        buffer.write(';');
        buffer.produceContent(encoder);

        buffer.write(EncodingUtils.getAsciiBytes("more "));
        buffer.write(EncodingUtils.getAsciiBytes("stuff"));
        buffer.produceContent(encoder);

        byte[] content = outstream.toByteArray();
        Assert.assertEquals("stuff;more stuff", EncodingUtils.getAsciiString(content));
    }

    @Test
    public void testBufferInfo() throws Exception {
        SimpleOutputBuffer buffer = new SimpleOutputBuffer(8, new DirectByteBufferAllocator());
        BufferInfo bufferinfo = buffer;

        Assert.assertEquals(0, bufferinfo.length());
        Assert.assertEquals(8, bufferinfo.available());
        buffer.write(new byte[] {'1', '2', '3', '4'});
        Assert.assertEquals(4, bufferinfo.length());
        Assert.assertEquals(4, bufferinfo.available());
        buffer.write(new byte[] {'1', '2', '3', '4', '5', '6', '7', '8'});
        Assert.assertEquals(12, bufferinfo.length());
        Assert.assertEquals(0, bufferinfo.available());
    }

    @Test
    public void testInputBufferNullInput() throws IOException {
        SimpleInputBuffer buffer = new SimpleInputBuffer(4, new DirectByteBufferAllocator());
        Assert.assertEquals(0, buffer.read(null));
        Assert.assertEquals(0, buffer.read(null, 0, 0));
    }

    @Test
    public void testOutputBufferNullInput() throws IOException {
        SimpleOutputBuffer buffer = new SimpleOutputBuffer(4, new DirectByteBufferAllocator());
        buffer.write(null);
        buffer.write(null, 0, 10);
        Assert.assertFalse(buffer.hasData());
    }

    @Test
    public void testDirectByteBufferAllocator() {
        DirectByteBufferAllocator allocator = new DirectByteBufferAllocator();
        ByteBuffer buffer = allocator.allocate(1);
        Assert.assertNotNull(buffer);
        Assert.assertTrue(buffer.isDirect());
        Assert.assertEquals(0, buffer.position());
        Assert.assertEquals(1, buffer.limit());
        Assert.assertEquals(1, buffer.capacity());

        buffer = allocator.allocate(2048);
        Assert.assertTrue(buffer.isDirect());
        Assert.assertEquals(0, buffer.position());
        Assert.assertEquals(2048, buffer.limit());
        Assert.assertEquals(2048, buffer.capacity());

        buffer = allocator.allocate(0);
        Assert.assertTrue(buffer.isDirect());
        Assert.assertEquals(0, buffer.position());
        Assert.assertEquals(0, buffer.limit());
        Assert.assertEquals(0, buffer.capacity());
    }

    @Test
    public void testHeapByteBufferAllocator() {
        HeapByteBufferAllocator allocator = new HeapByteBufferAllocator();
        ByteBuffer buffer = allocator.allocate(1);
        Assert.assertNotNull(buffer);
        Assert.assertFalse(buffer.isDirect());
        Assert.assertEquals(0, buffer.position());
        Assert.assertEquals(1, buffer.limit());
        Assert.assertEquals(1, buffer.capacity());

        buffer = allocator.allocate(2048);
        Assert.assertFalse(buffer.isDirect());
        Assert.assertEquals(0, buffer.position());
        Assert.assertEquals(2048, buffer.limit());
        Assert.assertEquals(2048, buffer.capacity());

        buffer = allocator.allocate(0);
        Assert.assertFalse(buffer.isDirect());
        Assert.assertEquals(0, buffer.position());
        Assert.assertEquals(0, buffer.limit());
        Assert.assertEquals(0, buffer.capacity());
    }

}
