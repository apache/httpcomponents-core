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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Simple tests for {@link LengthDelimitedEncoder}.
 */
class TestLengthDelimitedEncoder {

    private File tmpfile;

    protected File createTempFile() throws IOException {
        this.tmpfile = File.createTempFile("testFile", ".txt");
        return this.tmpfile;
    }

    @AfterEach
    void deleteTempFile() {
        if (this.tmpfile != null && this.tmpfile.exists()) {
            this.tmpfile.delete();
        }
    }

    @Test
    void testBasicCoding() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
        encoder.write(CodecTestUtils.wrap("stuff;"));
        encoder.write(CodecTestUtils.wrap("more stuff"));

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("stuff;more stuff");
        Assertions.assertThat(encoder.toString()).isEqualTo("[content length: 16; pos: 16; completed: true]");
    }

    @Test
    void testCodingBeyondContentLimit() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
        encoder.write(CodecTestUtils.wrap("stuff;"));
        encoder.write(CodecTestUtils.wrap("more stuff; and a lot more stuff"));

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("stuff;more stuff");
    }

    @Test
    void testCodingEmptyBuffer() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
        encoder.write(CodecTestUtils.wrap("stuff;"));

        final ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);

        encoder.write(CodecTestUtils.wrap("more stuff"));

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("stuff;more stuff");
    }

    @Test
    void testCodingCompleted() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 5);
        encoder.write(CodecTestUtils.wrap("stuff"));

        final ByteBuffer wrapped = CodecTestUtils.wrap("more stuff");

        Assertions.assertThatThrownBy(() ->
                        encoder.write(wrapped))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testInvalidConstructor() {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedEncoder(null, null, null, 10))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedEncoder(channel, null, null, 10))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedEncoder(channel, outbuf, null, 10))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedEncoder(channel, outbuf, metrics, -10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCodingBeyondContentLimitFromFile() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            testfile.write("stuff;".getBytes(StandardCharsets.US_ASCII));
            testfile.write("more stuff; and a lot more stuff".getBytes(StandardCharsets.US_ASCII));
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

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("stuff;more stuff");
    }

    @Test
    void testCodingEmptyFile() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
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

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("stuff;more stuff");
    }

    @Test
    void testCodingCompletedFromFile() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 5);
        encoder.write(CodecTestUtils.wrap("stuff"));

        createTempFile();
        try (final RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            testfile.write("more stuff".getBytes(StandardCharsets.US_ASCII));
        }

        try (final RandomAccessFile file = new RandomAccessFile(this.tmpfile, "rw");
             final FileChannel fchannel = file.getChannel()) {
            Assertions.assertThatThrownBy(() ->
                            encoder.transfer(fchannel, 0, 10))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testCodingFromFileSmaller() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);

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

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("stuff;more stuff");
    }

    @Test
    void testCodingFromFileFlushBuffer() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);

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

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("header\r\nstuff;more stuff");
    }

    @Test
    void testCodingFromFileChannelSaturated() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64, 4);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);

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

        Assertions.assertThat(encoder.isCompleted()).isFalse();
        Assertions.assertThat(s).isEqualTo("head");
    }

    @Test
    void testCodingNoFragmentBuffering() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);
        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 0);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);

        Mockito.verify(channel, Mockito.times(2)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.never()).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(13);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("header\r\nstuff");
    }

    @Test
    void testCodingFragmentBuffering() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);
        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 32);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);

        Mockito.verify(channel, Mockito.never()).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(1)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.never()).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("header\r\nstuff");
    }

    @Test
    void testCodingFragmentBufferingMultipleFragments() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 32);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("more stuff"))).isEqualTo(10);

        Mockito.verify(channel, Mockito.never()).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.never()).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff-more stuff");
    }

    @Test
    void testCodingFragmentBufferingMultipleFragmentsBeyondContentLimit() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            16, 32);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("more stuff; and a lot more stuff"))).isEqualTo(10);

        Mockito.verify(channel, Mockito.never()).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.never()).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff-more stuff");
    }

    @Test
    void testCodingFragmentBufferingLargeFragment() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("header");
        outbuf.writeLine(chbuffer);
        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 2);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);

        Mockito.verify(channel, Mockito.times(2)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.never()).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(13);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("header\r\nstuff");
    }

    @Test
    void testCodingFragmentBufferingTinyFragments() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("more stuff"))).isEqualTo(10);

        Mockito.verify(channel, Mockito.times(5)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(3)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(18);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff---more stuff");
    }

    @Test
    void testCodingFragmentBufferingTinyFragments2() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 2);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("more stuff"))).isEqualTo(10);

        Mockito.verify(channel, Mockito.times(4)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(2)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(18);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff---more stuff");
    }

    @Test
    void testCodingFragmentBufferingTinyFragments3() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 3);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("--"))).isEqualTo(2);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("more stuff"))).isEqualTo(10);

        Mockito.verify(channel, Mockito.times(4)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(5)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(2)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(21);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff------more stuff");
    }

    @Test
    void testCodingFragmentBufferingBufferFlush() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 8);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-stuff"))).isEqualTo(6);

        Mockito.verify(channel, Mockito.times(1)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(8);
        Assertions.assertThat(outbuf.length()).isEqualTo(3);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff-stuff");
    }

    @Test
    void testCodingFragmentBufferingBufferFlush2() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 8);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-much more stuff"))).isEqualTo(16);

        Mockito.verify(channel, Mockito.times(2)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(1)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(21);
        Assertions.assertThat(outbuf.length()).isEqualTo(0);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff-much more stuff");
    }

    @Test
    void testCodingFragmentBufferingChannelSaturated() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64, 8));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 3);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(0);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("more stuff"))).isEqualTo(0);

        Mockito.verify(channel, Mockito.times(5)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(6)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(4)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(8);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff---");
        Assertions.assertThat(outbuf.length()).isEqualTo(3);
    }

    @Test
    void testCodingFragmentBufferingChannelSaturated2() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(64, 8));
        final SessionOutputBuffer outbuf = Mockito.spy(new SessionOutputBufferImpl(1024, 128));
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(channel, outbuf, metrics,
            100, 8);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("stuff"))).isEqualTo(5);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("-"))).isEqualTo(1);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("much more stuff"))).isEqualTo(1);

        Mockito.verify(channel, Mockito.times(3)).write(ArgumentMatchers.any());
        Mockito.verify(outbuf, Mockito.times(3)).write(ArgumentMatchers.<ByteBuffer>any());
        Mockito.verify(outbuf, Mockito.times(1)).flush(channel);

        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(8);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(s).isEqualTo("stuff--m");
        Assertions.assertThat(outbuf.length()).isEqualTo(0);
    }

}
