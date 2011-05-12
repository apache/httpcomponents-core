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

import org.apache.http.ConnectionClosedException;
import org.apache.http.ReadableByteChannelMock;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link LengthDelimitedDecoder}.
 */
public class TestLengthDelimitedDecoder {

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

    private static String convert(final ByteBuffer src) {
        src.flip();
        StringBuilder buffer = new StringBuilder(src.remaining());
        while (src.hasRemaining()) {
            buffer.append((char)(src.get() & 0xff));
        }
        return buffer.toString();
    }

    private static String readFromFile(final File file) throws Exception {
        FileInputStream filestream = new FileInputStream(file);
        InputStreamReader reader = new InputStreamReader(filestream);
        try {
            StringBuilder buffer = new StringBuilder();
            char[] tmp = new char[2048];
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
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(6, bytesRead);
        Assert.assertEquals("stuff;", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        Assert.assertEquals("more stuff", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());
    }

    @Test
    public void testCodingBeyondContentLimit() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {
                        "stuff;",
                        "more stuff; and a lot more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(6, bytesRead);
        Assert.assertEquals("stuff;", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        Assert.assertEquals("more stuff", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());
    }

    @Test
    public void testBasicDecodingSmallBuffer() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        ByteBuffer dst = ByteBuffer.allocate(4);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(4, bytesRead);
        Assert.assertEquals("stuf", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(4, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(2, bytesRead);
        Assert.assertEquals("f;", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(6, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(4, bytesRead);
        Assert.assertEquals("more", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(10, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(4, bytesRead);
        Assert.assertEquals(" stu", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(14, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(2, bytesRead);
        Assert.assertEquals("ff", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(16, metrics.getBytesTransferred());
    }

    @Test
    public void testDecodingFromSessionBuffer1() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        inbuf.fill(channel);

        Assert.assertEquals(6, inbuf.length());

        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(6, bytesRead);
        Assert.assertEquals("stuff;", convert(dst));
        Assert.assertFalse(decoder.isCompleted());
        Assert.assertEquals(0, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        Assert.assertEquals("more stuff", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(10, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(10, metrics.getBytesTransferred());
    }

    @Test
    public void testDecodingFromSessionBuffer2() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {
                        "stuff;",
                        "more stuff; and a lot more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        inbuf.fill(channel);
        inbuf.fill(channel);

        Assert.assertEquals(38, inbuf.length());

        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("stuff;more stuff", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(0, metrics.getBytesTransferred());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(0, metrics.getBytesTransferred());
    }

    @Test
    public void testBasicDecodingFile() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!!!"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            FileChannel fchannel = testfile.getChannel();
            long pos = 0;
            while (!decoder.isCompleted()) {
                long bytesRead = decoder.transfer(fchannel, pos, 10);
                if (bytesRead > 0) {
                    pos += bytesRead;
                }
            }
        } finally {
            testfile.close();
        }
        Assert.assertEquals(this.tmpfile.length(), metrics.getBytesTransferred());
        Assert.assertEquals("stuff; more stuff; a lot more stuff!", readFromFile(this.tmpfile));
    }

    @Test
    public void testDecodingFileWithBufferedSessionData() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!!!"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36);

        int i = inbuf.fill(channel);
        Assert.assertEquals(7, i);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            FileChannel fchannel = testfile.getChannel();
            long pos = 0;
            while (!decoder.isCompleted()) {
                long bytesRead = decoder.transfer(fchannel, pos, 10);
                if (bytesRead > 0) {
                    pos += bytesRead;
                }
            }
        } finally {
            testfile.close();
        }
        Assert.assertEquals(this.tmpfile.length() - 7, metrics.getBytesTransferred());
        Assert.assertEquals("stuff; more stuff; a lot more stuff!", readFromFile(this.tmpfile));
    }

    @Test
    public void testDecodingFileWithOffsetAndBufferedSessionData() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff; ", "more stuff; ", "a lot more stuff!"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 36);

        int i = inbuf.fill(channel);
        Assert.assertEquals(7, i);

        byte[] beginning = "beginning; ".getBytes("US-ASCII");

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            testfile.write(beginning);
        } finally {
            testfile.close();
        }

        testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            FileChannel fchannel = testfile.getChannel();

            long pos = beginning.length;
            while (!decoder.isCompleted()) {
                if(testfile.length() < pos)
                    testfile.setLength(pos);
                long bytesRead = decoder.transfer(fchannel, pos, 10);
                if (bytesRead > 0) {
                    pos += bytesRead;
                }
            }
        } finally {
            testfile.close();
        }

        // count everything except the initial 7 bytes that went to the session buffer
        Assert.assertEquals(this.tmpfile.length() - 7 - beginning.length, metrics.getBytesTransferred());
        Assert.assertEquals("beginning; stuff; more stuff; a lot more stuff!", readFromFile(this.tmpfile));
    }

    @Test
    public void testWriteBeyondFileSize() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"a"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 1);

        createTempFile();
        RandomAccessFile testfile = new RandomAccessFile(this.tmpfile, "rw");
        try {
            FileChannel fchannel = testfile.getChannel();
            Assert.assertEquals(0, testfile.length());
            try {
                decoder.transfer(fchannel, 5, 10);
                Assert.fail("IOException should have been thrown");
            } catch(IOException expected) {
            }
        } finally {
            testfile.close();
        }
    }

    @Test
    public void testCodingBeyondContentLimitFile() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {
                        "stuff;",
                        "more stuff; and a lot more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 16);

        createTempFile();
        RandomAccessFile testfile  = new RandomAccessFile(this.tmpfile, "rw");
        try {
            FileChannel fchannel = testfile.getChannel();

            long bytesRead = decoder.transfer(fchannel, 0, 6);
            Assert.assertEquals(6, bytesRead);
            Assert.assertFalse(decoder.isCompleted());
            Assert.assertEquals(6, metrics.getBytesTransferred());

            bytesRead = decoder.transfer(fchannel,0 , 10);
            Assert.assertEquals(10, bytesRead);
            Assert.assertTrue(decoder.isCompleted());
            Assert.assertEquals(16, metrics.getBytesTransferred());

            bytesRead = decoder.transfer(fchannel, 0, 1);
            Assert.assertEquals(-1, bytesRead);
            Assert.assertTrue(decoder.isCompleted());
            Assert.assertEquals(16, metrics.getBytesTransferred());
        } finally {
            testfile.close();
        }
    }

    @Test
    public void testInvalidConstructor() {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        try {
            new LengthDelimitedDecoder(null, null, null, 10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedDecoder(channel, null, null, 10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedDecoder(channel, inbuf, null, 10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedDecoder(channel, inbuf, metrics, -10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }

    @Test
    public void testInvalidInput() throws Exception {
        String s = "stuff";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 3);

        try {
            decoder.read(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testZeroLengthDecoding() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 0);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals(0, metrics.getBytesTransferred());
    }

    @Test(expected=ConnectionClosedException.class)
    public void testTruncatedContent() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"1234567890"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 20);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(10, bytesRead);
        decoder.read(dst);
    }

    @Test(expected=ConnectionClosedException.class)
    public void testTruncatedContentWithFile() throws Exception {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"1234567890"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        LengthDelimitedDecoder decoder = new LengthDelimitedDecoder(
                channel, inbuf, metrics, 20);

        createTempFile();
        RandomAccessFile testfile  = new RandomAccessFile(this.tmpfile, "rw");
        try {
            FileChannel fchannel = testfile.getChannel();
            long bytesRead = decoder.transfer(fchannel, 0, Integer.MAX_VALUE);
            Assert.assertEquals(10, bytesRead);
            decoder.transfer(fchannel, 0, Integer.MAX_VALUE);
        } finally {
            testfile.close();
        }
    }

}
