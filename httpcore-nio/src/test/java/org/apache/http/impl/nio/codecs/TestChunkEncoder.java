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

import java.nio.ByteBuffer;

import org.apache.http.Consts;
import org.apache.http.WritableByteChannelMock;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

/**
 * Simple tests for {@link ChunkEncoder}.
 */
public class TestChunkEncoder {

    @Test
    public void testBasicCoding() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(CodecTestUtils.wrap("12345"));
        encoder.write(CodecTestUtils.wrap("678"));
        encoder.write(CodecTestUtils.wrap("90"));
        encoder.complete();

        outbuf.flush(channel);

        final String s = channel.dump(Consts.ASCII);

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n", s);
        Assert.assertEquals("[chunk-coded; completed: true]", encoder.toString());
    }

    @Test
    public void testChunkNoExceed() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        encoder.write(CodecTestUtils.wrap("1234"));
        encoder.complete();

        outbuf.flush(channel);

        final String s = channel.dump(Consts.ASCII);

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("4\r\n1234\r\n0\r\n\r\n", s);
    }

    @Test // See HTTPCORE-239
    public void testLimitedChannel() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(16, 16);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        // fill up the channel
        channel.write(CodecTestUtils.wrap("0123456789ABCDEF"));
        // fill up the out buffer
        outbuf.write(CodecTestUtils.wrap("0123456789ABCDEF"));

        final ByteBuffer src = CodecTestUtils.wrap("0123456789ABCDEF");
        Assert.assertEquals(0, encoder.write(src));
        Assert.assertEquals(0, encoder.write(src));
        Assert.assertEquals(0, encoder.write(src));

        // should not be able to copy any bytes, until we flush the channel and buffer
        channel.reset();
        outbuf.flush(channel);
        channel.reset();

        Assert.assertEquals(10, encoder.write(src));
        channel.flush();
        Assert.assertEquals(6, encoder.write(src));
        channel.flush();
        Assert.assertEquals(0, encoder.write(src));

        outbuf.flush(channel);
        final String s = channel.dump(Consts.ASCII);
        Assert.assertEquals("4\r\n0123\r\n4\r\n4567\r\n2\r\n89\r\n4\r\nABCD\r\n2\r\nEF\r\n", s);
    }

    @Test
    public void testBufferFragments() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(1024));
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 1024);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics, 1024);

        Assert.assertEquals(16, encoder.write(CodecTestUtils.wrap("0123456789ABCDEF")));
        Assert.assertEquals(16, encoder.write(CodecTestUtils.wrap("0123456789ABCDEF")));
        Assert.assertEquals(16, encoder.write(CodecTestUtils.wrap("0123456789ABCDEF")));

        Mockito.verify(channel, Mockito.never()).write(Matchers.<ByteBuffer>any());

        outbuf.flush(channel);
        final String s = channel.dump(Consts.ASCII);
        Assert.assertEquals("10\r\n0123456789ABCDEF\r\n10\r\n0123456789ABCDEF\r\n" +
                "10\r\n0123456789ABCDEF\r\n", s);
    }

    @Test
    public void testChunkExceed() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        final ByteBuffer src = CodecTestUtils.wrap("0123456789ABCDEF");

        Assert.assertEquals(16, encoder.write(src));
        Assert.assertEquals(0, src.remaining());

        outbuf.flush(channel);
        final String s = channel.dump(Consts.ASCII);
        Assert.assertEquals("4\r\n0123\r\n4\r\n4567\r\n4\r\n89AB\r\n4\r\nCDEF\r\n", s);

    }

    @Test
    public void testCodingEmptyBuffer() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(CodecTestUtils.wrap("12345"));
        encoder.write(CodecTestUtils.wrap("678"));
        encoder.write(CodecTestUtils.wrap("90"));

        final ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);

        encoder.complete();

        outbuf.flush(channel);

        final String s = channel.dump(Consts.ASCII);

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n", s);
    }

    @Test
    public void testCodingCompleted() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(CodecTestUtils.wrap("12345"));
        encoder.write(CodecTestUtils.wrap("678"));
        encoder.write(CodecTestUtils.wrap("90"));
        encoder.complete();

        try {
            encoder.write(CodecTestUtils.wrap("more stuff"));
            Assert.fail("IllegalStateException should have been thrown");
        } catch (final IllegalStateException ex) {
            // ignore
        }
        try {
            encoder.complete();
            Assert.fail("IllegalStateException should have been thrown");
        } catch (final IllegalStateException ex) {
            // ignore
        }
    }

    @Test
    public void testInvalidConstructor() {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);

        try {
            new ChunkEncoder(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkEncoder(channel, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkEncoder(channel, outbuf, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
    }

}
