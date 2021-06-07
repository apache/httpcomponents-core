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

package org.apache.hc.core5.http.impl.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Simple tests for {@link IdentityEncoder}.
 */
public class TestIdentityEncoder {

    private File tmpfile;

    protected File createTempFile() throws IOException {
        this.tmpfile = File.createTempFile("testFile", ".txt");
        return this.tmpfile;
    }

    @After
    public void deleteTempFile() {
        if (this.tmpfile != null && this.tmpfile.exists()) {
            this.tmpfile.delete();
        }
    }

    @Test
    public void testBasicCoding() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        encoder.complete();

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals(5, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff", s);
        Assert.assertEquals("[identity; completed: true]", encoder.toString());
    }

    @Test
    public void testCodingEmptySrcBuffer() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);
        encoder.write(CodecTestUtils.wrap("stuff"));

        final ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);
        encoder.complete();

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff", s);
    }

    @Test
    public void testCodingCompleted() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);
        encoder.write(CodecTestUtils.wrap("stuff"));
        encoder.complete();

        Assert.assertThrows(IllegalStateException.class, () -> encoder.write(CodecTestUtils.wrap("more stuff")));
    }

    @Test
    public void testInvalidConstructor() {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);

        Assert.assertThrows(NullPointerException.class, () -> new IdentityEncoder(null, null, null));
        Assert.assertThrows(NullPointerException.class, () -> new IdentityEncoder(channel, null, null));
        Assert.assertThrows(NullPointerException.class, () -> new IdentityEncoder(channel, outbuf, null));
    }

    @Test
    public void testCodingFromFile() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            testfile.write("stuff;".getBytes(StandardCharsets.US_ASCII));
            testfile.write("more stuff".getBytes(StandardCharsets.US_ASCII));
        } finally {
            testfile.close();
        }

        testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            final FileChannel fchannel = testfile.getChannel();
            encoder.transfer(fchannel, 0, 20);
        } finally {
            testfile.close();
        }

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertFalse(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);
    }

    @Test
    public void testCodingEmptyFile() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);
        encoder.write(CodecTestUtils.wrap("stuff;"));

        //Create an empty file
        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        testfile.close();

        testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            final FileChannel fchannel = testfile.getChannel();
            encoder.transfer(fchannel, 0, 20);
            encoder.write(CodecTestUtils.wrap("more stuff"));
        } finally {
            testfile.close();
        }

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertFalse(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);
    }

    @Test
    public void testCodingFromFileSmaller() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            testfile.write("stuff;".getBytes(StandardCharsets.US_ASCII));
            testfile.write("more stuff".getBytes(StandardCharsets.US_ASCII));
        } finally {
            testfile.close();
        }

        testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            final FileChannel fchannel = testfile.getChannel();
            encoder.transfer(fchannel, 0, 20);
        } finally {
            testfile.close();
        }
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertFalse(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);
    }

    @Test
    public void testCodingFromFileFlushBuffer() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            testfile.write("stuff;".getBytes(StandardCharsets.US_ASCII));
            testfile.write("more stuff".getBytes(StandardCharsets.US_ASCII));
        } finally {
            testfile.close();
        }

        testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            final FileChannel fchannel = testfile.getChannel();
            encoder.transfer(fchannel, 0, 20);
        } finally {
            testfile.close();
        }
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertFalse(encoder.isCompleted());
        Assert.assertEquals("header\r\nstuff;more stuff", s);
    }

    @Test
    public void testCodingFromFileChannelSaturated() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64, 4);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            testfile.write("stuff".getBytes(StandardCharsets.US_ASCII));
        } finally {
            testfile.close();
        }

        testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            final FileChannel fchannel = testfile.getChannel();
            encoder.transfer(fchannel, 0, 20);
            encoder.transfer(fchannel, 0, 20);
        } finally {
            testfile.close();
        }
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertFalse(encoder.isCompleted());
        Assert.assertEquals("head", s);
    }

    @Test
    public void testCodingNoFragmentBuffering() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);
        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 0);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));

        Mockito.verify(channel, Mockito.times(2)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.never()).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assert.assertEquals(13, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("header\r\nstuff", s);
    }

    @Test
    public void testCodingFragmentBuffering() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);
        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 32);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));

        Mockito.verify(channel, Mockito.never()).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(1)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.never()).flush(channel);

        Assert.assertEquals(0, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("header\r\nstuff", s);
    }

    @Test
    public void testCodingFragmentBufferingMultipleFragments() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 32);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(10, encoder.write(CodecTestUtils.wrap("more stuff")));

        Mockito.verify(channel, Mockito.never()).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.never()).flush(channel);

        Assert.assertEquals(0, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff-more stuff", s);
    }

    @Test
    public void testCodingFragmentBufferingLargeFragment() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);
        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 2);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));

        Mockito.verify(channel, Mockito.times(2)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.never()).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assert.assertEquals(13, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);
        Assert.assertEquals("header\r\nstuff", s);
    }

    @Test
    public void testCodingFragmentBufferingTinyFragments() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 1);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(10, encoder.write(CodecTestUtils.wrap("more stuff")));

        Mockito.verify(channel, Mockito.times(5)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(3)).flush(channel);

        Assert.assertEquals(18, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff---more stuff", s);
    }

    @Test
    public void testCodingFragmentBufferingTinyFragments2() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 2);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(10, encoder.write(CodecTestUtils.wrap("more stuff")));

        Mockito.verify(channel, Mockito.times(4)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(2)).flush(channel);

        Assert.assertEquals(18, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff---more stuff", s);
    }

    @Test
    public void testCodingFragmentBufferingTinyFragments3() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 3);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(2, encoder.write(CodecTestUtils.wrap("--")));
        Assert.assertEquals(10, encoder.write(CodecTestUtils.wrap("more stuff")));

        Mockito.verify(channel, Mockito.times(4)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(5)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(2)).flush(channel);

        Assert.assertEquals(21, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff------more stuff", s);
    }

    @Test
    public void testCodingFragmentBufferingBufferFlush() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 8);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(6, encoder.write(CodecTestUtils.wrap("-stuff")));

        Mockito.verify(channel, Mockito.times(1)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assert.assertEquals(8, metrics.getBytesTransferred());
        Assert.assertEquals(3, outbuf.length());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff-stuff", s);
    }

    @Test
    public void testCodingFragmentBufferingBufferFlush2() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 8);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(16, encoder.write(CodecTestUtils.wrap("-much more stuff")));

        Mockito.verify(channel, Mockito.times(2)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(1)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assert.assertEquals(21, metrics.getBytesTransferred());
        Assert.assertEquals(0, outbuf.length());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff-much more stuff", s);
    }

    @Test
    public void testCodingFragmentBufferingChannelSaturated() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64, 8));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 3);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(0, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(0, encoder.write(CodecTestUtils.wrap("more stuff")));

        Mockito.verify(channel, Mockito.times(5)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(6)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(4)).flush(channel);

        Assert.assertEquals(8, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff---", s);
        Assert.assertEquals(3, outbuf.length());
    }

    @Test
    public void testCodingFragmentBufferingChannelSaturated2() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64, 8));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics, 8);
        Assert.assertEquals(5, encoder.write(CodecTestUtils.wrap("stuff")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("-")));
        Assert.assertEquals(1, encoder.write(CodecTestUtils.wrap("much more stuff")));

        Mockito.verify(channel, Mockito.times(3)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assert.assertEquals(8, metrics.getBytesTransferred());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assert.assertEquals("stuff--m", s);
        Assert.assertEquals(0, outbuf.length());
    }

}
