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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
        Assertions.assertEquals(6, bytesRead);
        Assertions.assertEquals("stuff;", CodecTestUtils.convert(dst));
        Assertions.assertFalse(decoder.isCompleted());
        Assertions.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(10, bytesRead);
        Assertions.assertEquals("more stuff", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(16, metrics.getBytesTransferred());

        Assertions.assertEquals("[content length: 16; pos: 16; completed: true]", decoder.toString());
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
        Assertions.assertEquals(6, bytesRead);
        Assertions.assertEquals("stuff;", CodecTestUtils.convert(dst));
        Assertions.assertFalse(decoder.isCompleted());
        Assertions.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(10, bytesRead);
        Assertions.assertEquals("more stuff", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(16, metrics.getBytesTransferred());
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
        Assertions.assertEquals(4, bytesRead);
        Assertions.assertEquals("stuf", CodecTestUtils.convert(dst));
        Assertions.assertFalse(decoder.isCompleted());
        Assertions.assertEquals(4, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(2, bytesRead);
        Assertions.assertEquals("f;", CodecTestUtils.convert(dst));
        Assertions.assertFalse(decoder.isCompleted());
        Assertions.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(4, bytesRead);
        Assertions.assertEquals("more", CodecTestUtils.convert(dst));
        Assertions.assertFalse(decoder.isCompleted());
        Assertions.assertEquals(10, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(4, bytesRead);
        Assertions.assertEquals(" stu", CodecTestUtils.convert(dst));
        Assertions.assertFalse(decoder.isCompleted());
        Assertions.assertEquals(14, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(2, bytesRead);
        Assertions.assertEquals("ff", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(16, metrics.getBytesTransferred());
    }

    @Test
    void testDecodingFromSessionBuffer1() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        inbuf.fill(channel);

        Assertions.assertEquals(6, inbuf.length());

        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertEquals(6, bytesRead);
        Assertions.assertEquals("stuff;", CodecTestUtils.convert(dst));
        Assertions.assertFalse(decoder.isCompleted());
        Assertions.assertEquals(0, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(10, bytesRead);
        Assertions.assertEquals("more stuff", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(10, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(10, metrics.getBytesTransferred());
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

        Assertions.assertEquals(38, inbuf.length());

        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertEquals(16, bytesRead);
        Assertions.assertEquals("stuff;more stuff", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(0, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(0, metrics.getBytesTransferred());
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
        Assertions.assertEquals(this.tmpfile.length(), metrics.getBytesTransferred());
        Assertions.assertEquals("stuff; more stuff; a lot more stuff!",
            CodecTestUtils.readFromFile(this.tmpfile));
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
        Assertions.assertEquals(7, i);

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
        Assertions.assertEquals(this.tmpfile.length() - 7, metrics.getBytesTransferred());
        Assertions.assertEquals("stuff; more stuff; a lot more stuff!",
            CodecTestUtils.readFromFile(this.tmpfile));
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
        Assertions.assertEquals(7, i);

        final byte[] beginning =  "beginning; ".getBytes(StandardCharsets.US_ASCII);

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
                if(testfile.length() < pos) {
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
        Assertions.assertEquals(this.tmpfile.length() - 7 - beginning.length, metrics.getBytesTransferred());
        Assertions.assertEquals("beginning; stuff; more stuff; a lot more stuff!",
            CodecTestUtils.readFromFile(this.tmpfile));
    }

    @Test
    void testDecodingFileWithLimit() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; more stuff; ", "a lot more stuff!!!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(channel, inbuf, metrics, 36);

        final int i = inbuf.fill(channel);
        Assertions.assertEquals(19, i);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            long pos = 0;

            // transferred from buffer
            long bytesRead = decoder.transfer(fchannel, pos, 1);
            Assertions.assertEquals(1, bytesRead);
            Assertions.assertFalse(decoder.isCompleted());
            Assertions.assertEquals(0, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 2);
            Assertions.assertEquals(2, bytesRead);
            Assertions.assertFalse(decoder.isCompleted());
            Assertions.assertEquals(0, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 17);
            Assertions.assertEquals(16, bytesRead);
            Assertions.assertFalse(decoder.isCompleted());
            Assertions.assertEquals(0, metrics.getBytesTransferred());
            pos += bytesRead;

            // transferred from channel
            bytesRead = decoder.transfer(fchannel, pos, 1);
            Assertions.assertEquals(1, bytesRead);
            Assertions.assertFalse(decoder.isCompleted());
            Assertions.assertEquals(1, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 2);
            Assertions.assertEquals(2, bytesRead);
            Assertions.assertFalse(decoder.isCompleted());
            Assertions.assertEquals(3, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 15);
            Assertions.assertEquals(14, bytesRead);
            Assertions.assertTrue(decoder.isCompleted());
            Assertions.assertEquals(17, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 1);
            Assertions.assertEquals(-1, bytesRead);
            Assertions.assertTrue(decoder.isCompleted());
            Assertions.assertEquals(17, metrics.getBytesTransferred());
        }
        Assertions.assertEquals("stuff; more stuff; a lot more stuff!",
                CodecTestUtils.readFromFile(this.tmpfile));
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
            Assertions.assertEquals(0, testfile.length());
            Assertions.assertThrows(IOException.class, () -> decoder.transfer(fchannel, 5, 10));
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
        try (RandomAccessFile testfile  = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();

            long bytesRead = decoder.transfer(fchannel, 0, 6);
            Assertions.assertEquals(6, bytesRead);
            Assertions.assertFalse(decoder.isCompleted());
            Assertions.assertEquals(6, metrics.getBytesTransferred());

            bytesRead = decoder.transfer(fchannel,0 , 10);
            Assertions.assertEquals(10, bytesRead);
            Assertions.assertTrue(decoder.isCompleted());
            Assertions.assertEquals(16, metrics.getBytesTransferred());

            bytesRead = decoder.transfer(fchannel, 0, 1);
            Assertions.assertEquals(-1, bytesRead);
            Assertions.assertTrue(decoder.isCompleted());
            Assertions.assertEquals(16, metrics.getBytesTransferred());
        }
    }

    @Test
    void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        Assertions.assertThrows(NullPointerException.class, () -> new LengthDelimitedDecoder(null, null, null, 10));
        Assertions.assertThrows(NullPointerException.class, () -> new LengthDelimitedDecoder(channel, null, null, 10));
        Assertions.assertThrows(NullPointerException.class, () -> new LengthDelimitedDecoder(channel, inbuf, null, 10));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new LengthDelimitedDecoder(channel, inbuf, metrics, -10));
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

        Assertions.assertThrows(NullPointerException.class, () -> decoder.read(null));
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
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals(0, metrics.getBytesTransferred());
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
        Assertions.assertEquals(10, bytesRead);
        Assertions.assertThrows(ConnectionClosedException.class, () ->
                decoder.read(dst));
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
        try (RandomAccessFile testfile  = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            final long bytesRead = decoder.transfer(fchannel, 0, Integer.MAX_VALUE);
            Assertions.assertEquals(10, bytesRead);
            Assertions.assertThrows(ConnectionClosedException.class, () ->
                    decoder.transfer(fchannel, 0, Integer.MAX_VALUE));
        }
    }

}
