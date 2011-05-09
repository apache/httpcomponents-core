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

package org.apache.http.impl.nio.codecs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EncodingUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link ChunkEncoder}.
 */
public class TestChunkEncoder {

    private static ByteBuffer wrap(final String s) {
        return ByteBuffer.wrap(EncodingUtils.getAsciiBytes(s));
    }

    private static WritableByteChannel newChannel(final ByteArrayOutputStream baos) {
        return Channels.newChannel(baos);
    }

    @Test
    public void testBasicCoding() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));
        encoder.complete();

        outbuf.flush(channel);

        String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n", s);
    }

    @Test
    public void testChunkNoExceed() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        encoder.write(wrap("1234"));
        encoder.complete();

        outbuf.flush(channel);

        String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("4\r\n1234\r\n0\r\n\r\n", s);
    }

    @Test
    public void testHttpCore239() throws Exception {
        FixedByteChannel channel = new FixedByteChannel(16);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        // fill up the channel
        channel.write(wrap("0123456789ABCDEF"));
        // fill up the out buffer
        outbuf.write(wrap("0123456789ABCDEF"));

        ByteBuffer src = wrap("0123456789ABCDEF");
        Assert.assertEquals(0, encoder.write(src));
        Assert.assertEquals(0, encoder.write(src));
        Assert.assertEquals(0, encoder.write(src));

        // should not be able to copy any bytes, until we flush the channel and buffer
        channel.reset();
        outbuf.flush(channel);
        channel.reset();

        Assert.assertEquals(4, encoder.write(src));
        channel.flush();
        Assert.assertEquals(4, encoder.write(src));
        channel.flush();
        Assert.assertEquals(4, encoder.write(src));
        channel.flush();
        Assert.assertEquals(4, encoder.write(src));
        channel.flush();
        Assert.assertEquals(0, encoder.write(src));

        outbuf.flush(channel);
        String s = channel.toString("US-ASCII");
        Assert.assertEquals("4\r\n0123\r\n4\r\n4567\r\n4\r\n89AB\r\n4\r\nCDEF\r\n", s);
    }

    @Test
    public void testChunkExceed() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        ByteBuffer src = wrap("0123456789ABCDEF");

        Assert.assertEquals(4, encoder.write(src));
        Assert.assertTrue(src.hasRemaining());
        Assert.assertEquals(12, src.remaining());

        Assert.assertEquals(4, encoder.write(src));
        Assert.assertTrue(src.hasRemaining());
        Assert.assertEquals(8, src.remaining());

        Assert.assertEquals(4, encoder.write(src));
        Assert.assertEquals(4, encoder.write(src));
        Assert.assertFalse(src.hasRemaining());

        outbuf.flush(channel);
        String s = baos.toString("US-ASCII");
        Assert.assertEquals("4\r\n0123\r\n4\r\n4567\r\n4\r\n89AB\r\n4\r\nCDEF\r\n", s);

    }

    @Test
    public void testCodingEmptyBuffer() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));

        ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);

        encoder.complete();

        outbuf.flush(channel);

        String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n", s);
    }

    @Test
    public void testCodingCompleted() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(wrap("12345"));
        encoder.write(wrap("678"));
        encoder.write(wrap("90"));
        encoder.complete();

        try {
            encoder.write(wrap("more stuff"));
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // ignore
        }
        try {
            encoder.complete();
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // ignore
        }
    }

    @Test
    public void testInvalidConstructor() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);

        try {
            new ChunkEncoder(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkEncoder(channel, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkEncoder(channel, outbuf, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }

    public class FixedByteChannel implements WritableByteChannel {

        // collect bytes written for unit test result evaluation
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private final ByteBuffer buffer;

        public FixedByteChannel(int size) {
            this.buffer = ByteBuffer.allocate(size);
        }

        public int write(ByteBuffer src) throws IOException {
            // copy bytes into baos for result evaluation
            final int start = src.position();
            int count = 0;
            for (int i=start; i<src.limit() && buffer.remaining() > 0; i++) {
                final byte b = src.get(i);
                baos.write(b);
                buffer.put(b);
                count++;
            }
            // update processed position on src buffer
            src.position(src.position() + count);
            return count;
        }

        public boolean isOpen() {
            return false;
        }

        public void close() throws IOException {
        }

        public void flush() {
            buffer.clear();
        }

        public void reset() {
            baos.reset();
            buffer.clear();
        }

        public String toString(String encoding) throws UnsupportedEncodingException {
            return baos.toString(encoding);
        }
    }

}
