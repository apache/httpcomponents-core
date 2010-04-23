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

import junit.framework.TestCase;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.io.BufferInfo;
import org.apache.http.mockup.MockupDecoder;
import org.apache.http.mockup.MockupEncoder;
import org.apache.http.mockup.ReadableByteChannelMockup;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EncodingUtils;

/**
 * Buffer tests.
 *
 *
 * @version $Id:TestBuffers.java 503277 2007-02-03 18:22:45 +0000 (Sat, 03 Feb 2007) olegk $
 */
public class TestBuffers extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBuffers(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testInputBufferOperations() throws IOException {
        ReadableByteChannel channel = new ReadableByteChannelMockup(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");

        ContentDecoder decoder = new MockupDecoder(channel);

        SimpleInputBuffer buffer = new SimpleInputBuffer(4, new DirectByteBufferAllocator());
        int count = buffer.consumeContent(decoder);
        assertEquals(16, count);
        assertTrue(decoder.isCompleted());

        byte[] b1 = new byte[5];

        int len = buffer.read(b1);
        assertEquals("stuff", EncodingUtils.getAsciiString(b1, 0, len));

        int c = buffer.read();
        assertEquals(';', c);

        byte[] b2 = new byte[1024];

        len = buffer.read(b2);
        assertEquals("more stuff", EncodingUtils.getAsciiString(b2, 0, len));

        assertEquals(-1, buffer.read());
        assertEquals(-1, buffer.read(b2));
        assertEquals(-1, buffer.read(b2, 0, b2.length));
        assertTrue(buffer.isEndOfStream());

        buffer.reset();
        assertFalse(buffer.isEndOfStream());
    }

    public void testOutputBufferOperations() throws IOException {
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(outstream);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        ContentEncoder encoder = new MockupEncoder(channel, outbuf, metrics);

        SimpleOutputBuffer buffer = new SimpleOutputBuffer(4, new DirectByteBufferAllocator());

        buffer.write(EncodingUtils.getAsciiBytes("stuff"));
        buffer.write(';');
        buffer.produceContent(encoder);

        buffer.write(EncodingUtils.getAsciiBytes("more "));
        buffer.write(EncodingUtils.getAsciiBytes("stuff"));
        buffer.produceContent(encoder);

        byte[] content = outstream.toByteArray();
        assertEquals("stuff;more stuff", EncodingUtils.getAsciiString(content));
    }

    public void testBufferInfo() throws Exception {
        SimpleOutputBuffer buffer = new SimpleOutputBuffer(8, new DirectByteBufferAllocator());
        BufferInfo bufferinfo = buffer;

        assertEquals(0, bufferinfo.length());
        assertEquals(8, bufferinfo.available());
        buffer.write(new byte[] {'1', '2', '3', '4'});
        assertEquals(4, bufferinfo.length());
        assertEquals(4, bufferinfo.available());
        buffer.write(new byte[] {'1', '2', '3', '4', '5', '6', '7', '8'});
        assertEquals(12, bufferinfo.length());
        assertEquals(0, bufferinfo.available());
    }

    public void testInputBufferNullInput() throws IOException {
        SimpleInputBuffer buffer = new SimpleInputBuffer(4, new DirectByteBufferAllocator());
        assertEquals(0, buffer.read(null));
        assertEquals(0, buffer.read(null, 0, 0));
    }

    public void testOutputBufferNullInput() throws IOException {
        SimpleOutputBuffer buffer = new SimpleOutputBuffer(4, new DirectByteBufferAllocator());
        buffer.write(null);
        buffer.write(null, 0, 10);
        assertFalse(buffer.hasData());
    }

    public void testDirectByteBufferAllocator() {
        DirectByteBufferAllocator allocator = new DirectByteBufferAllocator();
        ByteBuffer buffer = allocator.allocate(1);
        assertNotNull(buffer);
        assertTrue(buffer.isDirect());
        assertEquals(0, buffer.position());
        assertEquals(1, buffer.limit());
        assertEquals(1, buffer.capacity());

        buffer = allocator.allocate(2048);
        assertTrue(buffer.isDirect());
        assertEquals(0, buffer.position());
        assertEquals(2048, buffer.limit());
        assertEquals(2048, buffer.capacity());

        buffer = allocator.allocate(0);
        assertTrue(buffer.isDirect());
        assertEquals(0, buffer.position());
        assertEquals(0, buffer.limit());
        assertEquals(0, buffer.capacity());
    }

    public void testHeapByteBufferAllocator() {
        HeapByteBufferAllocator allocator = new HeapByteBufferAllocator();
        ByteBuffer buffer = allocator.allocate(1);
        assertNotNull(buffer);
        assertFalse(buffer.isDirect());
        assertEquals(0, buffer.position());
        assertEquals(1, buffer.limit());
        assertEquals(1, buffer.capacity());

        buffer = allocator.allocate(2048);
        assertFalse(buffer.isDirect());
        assertEquals(0, buffer.position());
        assertEquals(2048, buffer.limit());
        assertEquals(2048, buffer.capacity());

        buffer = allocator.allocate(0);
        assertFalse(buffer.isDirect());
        assertEquals(0, buffer.position());
        assertEquals(0, buffer.limit());
        assertEquals(0, buffer.capacity());
    }

}
