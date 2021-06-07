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
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link LengthDelimitedDecoder}.
 */
public class TestIdentityDecoder {

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
    public void testBasicDecoding() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(6, bytesRead);
        Assert.assertEquals("stuff;", CodecTestUtils.convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        Assert.assertEquals("more stuff", CodecTestUtils.convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());

        Assert.assertEquals("[identity; completed: true]", decoder.toString());
    }

    @Test
    public void testDecodingFromSessionBuffer() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();

        inbuf.fill(channel);

        Assert.assertEquals(6, inbuf.length());

        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(6, bytesRead);
        Assert.assertEquals("stuff;", CodecTestUtils.convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(0, metrics.getBytesTransferred()); // doesn't count if from session buffer

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        Assert.assertEquals("more stuff", CodecTestUtils.convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(10, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(10, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(10, metrics.getBytesTransferred());

    }

    @Test
    public void testBasicDecodingFile() throws Exception {
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

            Assert.assertEquals(testfile.length(), metrics.getBytesTransferred());
        }
        Assert.assertEquals("stuff; more stuff; a lot more stuff!",
            CodecTestUtils.readFromFile(this.tmpfile));
    }

    @Test
    public void testDecodingFileWithBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        final int i = inbuf.fill(channel);
        Assert.assertEquals(7, i);

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
            Assert.assertEquals(testfile.length() - 7, metrics.getBytesTransferred());
        }
        Assert.assertEquals("stuff; more stuff; a lot more stuff!",
            CodecTestUtils.readFromFile(this.tmpfile));
    }

    @Test
    public void testDecodingFileWithOffsetAndBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        final int i = inbuf.fill(channel);
        Assert.assertEquals(7, i);

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
                if(testfile.length() < pos) {
                    testfile.setLength(pos);
                }
                final long bytesRead = decoder.transfer(fchannel, pos, 10);
                if (bytesRead > 0) {
                    pos += bytesRead;
                }
            }

            // count everything except the initial 7 bytes that went to the session buffer
            Assert.assertEquals(testfile.length() - 7 - beginning.length, metrics.getBytesTransferred());
        } finally {
            testfile.close();
        }

        Assert.assertEquals("beginning; stuff; more stuff; a lot more stuff!",
            CodecTestUtils.readFromFile(this.tmpfile));
    }

    @Test
    public void testDecodingFileWithLimit() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; more stuff; ", "a lot more stuff!"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        final int i = inbuf.fill(channel);
        Assert.assertEquals(19, i);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            final FileChannel fchannel = testfile.getChannel();
            long pos = 0;

            // transferred from buffer
            long bytesRead = decoder.transfer(fchannel, pos, 1);
            Assert.assertEquals(1, bytesRead);
            Assert.assertFalse(decoder.isCompleted());
            Assert.assertEquals(0, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 2);
            Assert.assertEquals(2, bytesRead);
            Assert.assertFalse(decoder.isCompleted());
            Assert.assertEquals(0, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 17);
            Assert.assertEquals(16, bytesRead);
            Assert.assertFalse(decoder.isCompleted());
            Assert.assertEquals(0, metrics.getBytesTransferred());
            pos += bytesRead;

            // transferred from channel
            bytesRead = decoder.transfer(fchannel, pos, 1);
            Assert.assertEquals(1, bytesRead);
            Assert.assertFalse(decoder.isCompleted());
            Assert.assertEquals(1, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 2);
            Assert.assertEquals(2, bytesRead);
            Assert.assertFalse(decoder.isCompleted());
            Assert.assertEquals(3, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 15);
            Assert.assertEquals(14, bytesRead);
            Assert.assertFalse(decoder.isCompleted());
            Assert.assertEquals(17, metrics.getBytesTransferred());
            pos += bytesRead;

            bytesRead = decoder.transfer(fchannel, pos, 1);
            Assert.assertEquals(-1, bytesRead);
            Assert.assertTrue(decoder.isCompleted());
            Assert.assertEquals(17, metrics.getBytesTransferred());
        }
        Assert.assertEquals("stuff; more stuff; a lot more stuff!",
                CodecTestUtils.readFromFile(this.tmpfile));
    }

    @Test
    public void testWriteBeyondFileSize() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"a"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        createTempFile();
        try (RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw")) {
            Assert.assertEquals(0, testfile.length());
            final FileChannel fchannel = testfile.getChannel();
            Assert.assertThrows(IOException.class, () -> decoder.transfer(fchannel, 5, 10));
        }
    }

    @Test
    public void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        Assert.assertThrows(NullPointerException.class, () -> new IdentityDecoder(null, null, null));
        Assert.assertThrows(NullPointerException.class, () -> new IdentityDecoder(channel, null, null));
        Assert.assertThrows(NullPointerException.class, () -> new IdentityDecoder(channel, inbuf, null));
    }

    @Test
    public void testInvalidInput() throws Exception {
        final String s = "stuff";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        Assert.assertThrows(NullPointerException.class, () -> decoder.read(null));
    }

}
