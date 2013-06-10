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

import org.apache.http.Consts;
import org.apache.http.ReadableByteChannelMock;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.util.EncodingUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Buffer tests.
 */
public class TestBuffers {

    @Test
    public void testInputBufferOperations() throws IOException {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, Consts.ASCII);

        final ContentDecoder decoder = new ContentDecoderMock(channel);

        final SimpleInputBuffer buffer = new SimpleInputBuffer(4, DirectByteBufferAllocator.INSTANCE);
        final int count = buffer.consumeContent(decoder);
        Assert.assertEquals(16, count);
        Assert.assertTrue(decoder.isCompleted());

        final byte[] b1 = new byte[5];

        int len = buffer.read(b1);
        Assert.assertEquals("stuff", EncodingUtils.getAsciiString(b1, 0, len));

        final int c = buffer.read();
        Assert.assertEquals(';', c);

        final byte[] b2 = new byte[1024];

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
        final ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        final WritableByteChannel channel = Channels.newChannel(outstream);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final ContentEncoder encoder = new ContentEncoderMock(channel, outbuf, metrics);

        final SimpleOutputBuffer buffer = new SimpleOutputBuffer(4, DirectByteBufferAllocator.INSTANCE);

        buffer.write(EncodingUtils.getAsciiBytes("stuff"));
        buffer.write(';');
        buffer.produceContent(encoder);

        buffer.write(EncodingUtils.getAsciiBytes("more "));
        buffer.write(EncodingUtils.getAsciiBytes("stuff"));
        buffer.produceContent(encoder);

        final byte[] content = outstream.toByteArray();
        Assert.assertEquals("stuff;more stuff", EncodingUtils.getAsciiString(content));
    }

    @Test
    public void testBufferInfo() throws Exception {
        final SimpleOutputBuffer buffer = new SimpleOutputBuffer(8, DirectByteBufferAllocator.INSTANCE);

        Assert.assertEquals(0, buffer.length());
        Assert.assertEquals(8, buffer.available());
        buffer.write(new byte[] {'1', '2', '3', '4'});
        Assert.assertEquals(4, buffer.length());
        Assert.assertEquals(4, buffer.available());
        buffer.write(new byte[] {'1', '2', '3', '4', '5', '6', '7', '8'});
        Assert.assertEquals(12, buffer.length());
        Assert.assertEquals(0, buffer.available());
    }

    @Test
    public void testInputBufferNullInput() throws IOException {
        final SimpleInputBuffer buffer = new SimpleInputBuffer(4, DirectByteBufferAllocator.INSTANCE);
        Assert.assertEquals(0, buffer.read(null));
        Assert.assertEquals(0, buffer.read(null, 0, 0));
    }

    @Test
    public void testOutputBufferNullInput() throws IOException {
        final SimpleOutputBuffer buffer = new SimpleOutputBuffer(4, DirectByteBufferAllocator.INSTANCE);
        buffer.write(null);
        buffer.write(null, 0, 10);
        Assert.assertFalse(buffer.hasData());
    }

    @Test
    public void testDirectByteBufferAllocator() {
        final DirectByteBufferAllocator allocator = new DirectByteBufferAllocator();
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
        final HeapByteBufferAllocator allocator = new HeapByteBufferAllocator();
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
