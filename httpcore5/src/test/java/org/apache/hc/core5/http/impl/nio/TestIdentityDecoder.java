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

import org.apache.hc.core5.http.ReadableByteChannelMock;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link LengthDelimitedDecoder}.
 */
class TestIdentityDecoder {

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
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

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
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(16);

        Assertions.assertThat(decoder.toString()).isEqualTo("[identity; completed: true]");
    }

    @Test
    void testDecodingFromSessionBuffer() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        inbuf.fill(channel);

        Assertions.assertThat(inbuf.length()).isEqualTo(6);

        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(6);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("stuff;");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(0); // doesn't count if from session buffer

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(10);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("more stuff");
        Assertions.assertThat(decoder.isCompleted()).isFalse();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(10);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(10);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(10);

    }

    @Test
    void testBasicDecodingFile() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

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

            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(testfile.length());
        }
        Assertions.assertThat(CodecTestUtils.readFromFile(this.tmpfile)).isEqualTo("stuff; more stuff; a lot more stuff!");
    }

    @Test
    void testDecodingFileWithBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

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

            // count everything except the initial 7 bytes that went to the session buffer
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(testfile.length() - 7);
        }
        Assertions.assertThat(CodecTestUtils.readFromFile(this.tmpfile)).isEqualTo("stuff; more stuff; a lot more stuff!");
    }

    @Test
    void testDecodingFileWithOffsetAndBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

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

            // count everything except the initial 7 bytes that went to the session buffer
            Assertions.assertThat(metrics.getBytesTransferred()).isEqualTo(testfile.length() - 7 - beginning.length);
        } finally {
            testfile.close();
        }

        Assertions.assertThat(CodecTestUtils.readFromFile(this.tmpfile)).isEqualTo("beginning; stuff; more stuff; a lot more stuff!");
    }

    @Test
    void testDecodingFileWithLimit() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

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
            Assertions.assertThat(decoder.isCompleted()).isFalse();
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
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            Assertions.assertThat(testfile.length()).isEqualTo(0);
            final FileChannel fchannel = testfile.getChannel();
            Assertions.assertThatThrownBy(() ->
                            decoder.transfer(fchannel, 5, 10))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        Assertions.assertThatThrownBy(() ->
                        new IdentityDecoder(null, null, null))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new IdentityDecoder(channel, null, null))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new IdentityDecoder(channel, inbuf, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidInput() {
        final String s = "stuff";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        Assertions.assertThatThrownBy(() ->
                        decoder.read(null))
                .isInstanceOf(NullPointerException.class);
    }

}
