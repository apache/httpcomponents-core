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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.Consts;
import org.apache.http.ReadableByteChannelMock;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link LengthDelimitedDecoder}.
 */
public class TestIdentityDecoder {

    private static String convert(final ByteBuffer src) {
        src.flip();
        final StringBuilder buffer = new StringBuilder(src.remaining());
        while (src.hasRemaining()) {
            buffer.append((char)(src.get() & 0xff));
        }
        return buffer.toString();
    }

    private static String readFromFile(final File file) throws Exception {
        final FileInputStream filestream = new FileInputStream(file);
        final InputStreamReader reader = new InputStreamReader(filestream);
        try {
            final StringBuilder buffer = new StringBuilder();
            final char[] tmp = new char[2048];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buffer.append(tmp, 0, l);
            }
            return buffer.toString();
        } finally {
            reader.close();
        }
    }

    @Test
    public void testBasicDecoding() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(6, bytesRead);
        Assert.assertEquals("stuff;", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        Assert.assertEquals("more stuff", convert(dst));
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
    }

    @Test
    public void testDecodingFromSessionBuffer() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        inbuf.fill(channel);

        Assert.assertEquals(6, inbuf.length());

        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(6, bytesRead);
        Assert.assertEquals("stuff;", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(0, metrics.getBytesTransferred()); // doesn't count if from session buffer

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        Assert.assertEquals("more stuff", convert(dst));
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
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        final File fileHandle = File.createTempFile("testFile", ".txt");

        final RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw");
        final FileChannel fchannel = testfile.getChannel();

        long pos = 0;
        while (!decoder.isCompleted()) {
            final long bytesRead = decoder.transfer(fchannel, pos, 10);
            if (bytesRead > 0) {
                pos += bytesRead;
            }
        }

        Assert.assertEquals(testfile.length(), metrics.getBytesTransferred());
        fchannel.close();

        Assert.assertEquals("stuff; more stuff; a lot more stuff!", readFromFile(fileHandle));

        deleteWithCheck(fileHandle);
    }

    private void deleteWithCheck(final File handle){
        if (!handle.delete() && handle.exists()){
            System.err.println("Failed to delete: "+handle.getPath());
        }
    }

    @Test
    public void testDecodingFileWithBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        final int i = inbuf.fill(channel);
        Assert.assertEquals(7, i);

        final File fileHandle = File.createTempFile("testFile", ".txt");

        final RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw");
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
        fchannel.close();

        Assert.assertEquals("stuff; more stuff; a lot more stuff!", readFromFile(fileHandle));

        deleteWithCheck(fileHandle);
    }

    @Test
    public void testDecodingFileWithOffsetAndBufferedSessionData() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        final int i = inbuf.fill(channel);
        Assert.assertEquals(7, i);

        final File fileHandle = File.createTempFile("testFile", ".txt");

        RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw");
        final byte[] beginning = "beginning; ".getBytes("US-ASCII");
        testfile.write(beginning);
        testfile.close();

        testfile = new RandomAccessFile(fileHandle, "rw");
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
        fchannel.close();

        Assert.assertEquals("beginning; stuff; more stuff; a lot more stuff!", readFromFile(fileHandle));

        deleteWithCheck(fileHandle);
    }

    @Test
    public void testWriteBeyondFileSize() throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"a"}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final IdentityDecoder decoder = new IdentityDecoder(
                channel, inbuf, metrics);

        final File fileHandle = File.createTempFile("testFile", ".txt");

        final RandomAccessFile testfile = new RandomAccessFile(fileHandle, "rw");
        final FileChannel fchannel = testfile.getChannel();
        Assert.assertEquals(0, testfile.length());

        try {
            decoder.transfer(fchannel, 5, 10);
            Assert.fail("expected IOException");
        } catch(final IOException iox) {}

        testfile.close();
        deleteWithCheck(fileHandle);
    }

    @Test
    public void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        try {
            new IdentityDecoder(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new IdentityDecoder(channel, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new IdentityDecoder(channel, inbuf, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
    }

    @Test
    public void testInvalidInput() throws Exception {
        final String s = "stuff";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final IdentityDecoder decoder = new IdentityDecoder(channel, inbuf, metrics);

        try {
            decoder.read(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

}
