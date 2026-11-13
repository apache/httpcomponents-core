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
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ReadableByteChannelMock;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link LengthDelimitedDecoder}.
 */
class TestLengthDelimitedDecoder {

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
    void testBasicDecoding() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(6);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("stuff;");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(6);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(10);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("more stuff");
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

        Assertions.assertThat(decoder.toString()).isEqualTo("[content length: 16; pos: 16; completed: true]");
    }

    @Test
    void testCodingBeyondContentLimit() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {
                        "stuff;",
                        "more stuff; and a lot more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(6);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("stuff;");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(6);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(10);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("more stuff");
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);
    }

    @Test
    void testBasicDecodingSmallBuffer() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        final ByteBuffer dst = ByteBuffer.allocate(4);

        int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(4);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("stuf");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(4);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(2);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("f;");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(6);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(4);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("more");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(10);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(4);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo(" stu");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(14);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(2);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("ff");
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);
    }

    @Test
    void testDecodingFromSessionBuffer1() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        inbuf.fill(channel);

        Assertions.assertThat(inbuf.length()).isEqualTo(6);

        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(6);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("stuff;");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(10);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("more stuff");
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(10);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(10);
    }

    @Test
    void testDecodingFromSessionBuffer2() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {
                        "stuff;",
                        "more stuff; and a lot more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        inbuf.fill(channel);
        inbuf.fill(channel);

        Assertions.assertThat(inbuf.length()).isEqualTo(38);

        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(16);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("stuff;more stuff");
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);
    }

    /* ----------------- FileChannel Part testing --------------------------- */
    @Test
    void testBasicDecodingFile() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!!!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            long pos = 0;
            while (!decoder.isCompleted()) {
                final long bytesRead = decoder.transfer(fchannel, pos, 10);
                if (bytesRead > 0) {
                    pos += bytesRead;
                }
            }
        }
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(this.tmpfile.length());
        Assertions.assertThat(CodecTestUtils.readFromFile(this.tmpfile)).isEqualTo("stuff; more stuff; a lot more stuff!");
    }

    @Test
    void testDecodingFileWithBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!!!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36);

        final int i = inbuf.fill(channel);
        Assertions.assertThat(i).isEqualTo(7);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            long pos = 0;
            while (!decoder.isCompleted()) {
                final long bytesRead = decoder.transfer(fchannel, pos, 10);
                if (bytesRead > 0) {
                    pos += bytesRead;
                }
            }
        }
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(this.tmpfile.length() - 7);
        Assertions.assertThat(CodecTestUtils.readFromFile(this.tmpfile)).isEqualTo("stuff; more stuff; a lot more stuff!");
    }

    @Test
    void testDecodingFileWithOffsetAndBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36);

        final int i = inbuf.fill(channel);
        Assertions.assertThat(i).isEqualTo(7);

        final byte[] beginning = "beginning; ".getBytes(StandardCharsets.US_ASCII);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            testfile.write(beginning);
        } finally {
            testfile.close();
        }

        testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            final FileChannel fchannel = testfile.getChannel();

            long pos = beginning.length;
            while (!decoder.isCompleted()) {
                if (testfile.length() < pos) {
                    testfile.setLength(pos);
                }
                final long bytesRead = decoder.transfer(fchannel, pos, 10);
                if (bytesRead > 0) {
                    pos += bytesRead;
                }
            }
        } finally {
            testfile.close();
        }

        // count everything except the initial 7 bytes that went to the session buffer
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(this.tmpfile.length() - 7 - beginning.length);
        Assertions.assertThat(CodecTestUtils.readFromFile(this.tmpfile)).isEqualTo("beginning; stuff; more stuff; a lot more stuff!");
    }

    @Test
    void testDecodingFileWithLimit() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; more stuff; ", "a lot more stuff!!!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(channel, inbuf, metrics, 36);

        final int i = inbuf.fill(channel);
        Assertions.assertThat(i).isEqualTo(19);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            long pos = 0;

            // transferred from buffer
            long bytesRead = decoder.transfer(fchannel, pos, 1);
            Assertions.assertThat(bytesRead).isEqualTo(1);
            Assertions.assertThat(decoder.isCompleted()).isFalse();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 2);
            Assertions.assertThat(bytesRead).isEqualTo(2);
            Assertions.assertThat(decoder.isCompleted()).isFalse();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 17);
            Assertions.assertThat(bytesRead).isEqualTo(16);
            Assertions.assertThat(decoder.isCompleted()).isFalse();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);
            pos += bytesRead;

            // transferred from channel
            bytesRead = decoder.transfer(fchannel, pos, 1);
            Assertions.assertThat(bytesRead).isEqualTo(1);
            Assertions.assertThat(decoder.isCompleted()).isFalse();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(1);
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 2);
            Assertions.assertThat(bytesRead).isEqualTo(2);
            Assertions.assertThat(decoder.isCompleted()).isFalse();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(3);
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 15);
            Assertions.assertThat(bytesRead).isEqualTo(14);
            Assertions.assertThat(decoder.isCompleted()).isTrue();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(17);
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 1);
            Assertions.assertThat(bytesRead).isEqualTo(-1);
            Assertions.assertThat(decoder.isCompleted()).isTrue();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(17);
        }
        Assertions.assertThat(CodecTestUtils.readFromFile(this.tmpfile)).isEqualTo("stuff; more stuff; a lot more stuff!");
    }

    @Test
    void testWriteBeyondFileSize() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"a"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 1);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            Assertions.assertThat(testfile.length()).isEqualTo(0);
            Assertions.assertThatThrownBy(() ->
                            decoder.transfer(fchannel, 5, 10))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testCodingBeyondContentLimitFile() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {
                        "stuff;",
                        "more stuff; and a lot more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();

            long bytesRead = decoder.transfer(fchannel, 0, 6);
            Assertions.assertThat(bytesRead).isEqualTo(6);
            Assertions.assertThat(decoder.isCompleted()).isFalse();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(6);

            bytesRead = decoder.transfer(fchannel,0 , 10);
            Assertions.assertThat(bytesRead).isEqualTo(10);
            Assertions.assertThat(decoder.isCompleted()).isTrue();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

            bytesRead = decoder.transfer(fchannel, 0, 1);
            Assertions.assertThat(bytesRead).isEqualTo(-1);
            Assertions.assertThat(decoder.isCompleted()).isTrue();
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);
        }
    }

    @Test
    void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedDecoder(null, null, null, 10))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedDecoder(channel, null, null, 10))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedDecoder(channel, inbuf, null, 10))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new LengthDelimitedDecoder(channel, inbuf, metrics, -10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidInput() {
        final String s = "stuff";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 3);

        Assertions.assertThatThrownBy(() ->
                        decoder.read(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testZeroLengthDecoding() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 0);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0);
    }

    @Test
    void testTruncatedContent() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"1234567890"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 20);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(10);
        Assertions.assertThatThrownBy(() ->
                decoder.read(dst)).isInstanceOf(ConnectionClosedException.class);
    }

    @Test
    void testTruncatedContentWithFile() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"1234567890"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 20);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            final long bytesRead = decoder.transfer(fchannel, 0, Integer.MAX_VALUE);
            Assertions.assertThat(bytesRead).isEqualTo(10);
            Assertions.assertThatThrownBy(() ->
                    decoder.transfer(fchannel, 0, Integer.MAX_VALUE)).isInstanceOf(ConnectionClosedException.class);
        }
    }

}
