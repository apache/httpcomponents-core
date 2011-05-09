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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.Header;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.ReadableByteChannelMock;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link ChunkDecoder}.
 */
public class TestChunkDecoder {

    private static String convert(final ByteBuffer src) {
        src.flip();
        StringBuilder buffer = new StringBuilder(src.remaining());
        while (src.hasRemaining()) {
            buffer.append((char)(src.get() & 0xff));
        }
        return buffer.toString();
    }

    @Test
    public void testBasicDecoding() throws Exception {
        String s = "5\r\n01234\r\n5\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("0123456789abcdef", convert(dst));
        Header[] footers = decoder.getFooters();
        Assert.assertEquals(0, footers.length);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testComplexDecoding() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\nFooter2: fghij\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assert.assertEquals(26, bytesRead);
        Assert.assertEquals("12345678901234561234512345", convert(dst));

        Header[] footers = decoder.getFooters();
        Assert.assertEquals(2, footers.length);
        Assert.assertEquals("Footer1", footers[0].getName());
        Assert.assertEquals("abcde", footers[0].getValue());
        Assert.assertEquals("Footer2", footers[1].getName());
        Assert.assertEquals("fghij", footers[1].getValue());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testDecodingWithSmallBuffer() throws Exception {
        String s1 = "5\r\n01234\r\n5\r\n5678";
        String s2 = "9\r\n6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s1, s2}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);
        ByteBuffer tmp = ByteBuffer.allocate(4);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            int i = decoder.read(tmp);
            if (i > 0) {
                bytesRead += i;
            }
            tmp.flip();
            dst.put(tmp);
            tmp.compact();
        }

        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("0123456789abcdef", convert(dst));
        Assert.assertTrue(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testIncompleteChunkDecoding() throws Exception {
        String[] chunks = {
                "10;",
                "key=\"value\"\r",
                "\n123456789012345",
                "6\r\n5\r\n12",
                "345\r\n6\r",
                "\nabcdef\r",
                "\n0\r\nFoot",
                "er1: abcde\r\nFooter2: f",
                "ghij\r\n\r\n"
        };
        ReadableByteChannel channel = new ReadableByteChannelMock(
                chunks, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ByteBuffer dst = ByteBuffer.allocate(1024);

        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assert.assertEquals(27, bytesRead);
        Assert.assertEquals("123456789012345612345abcdef", convert(dst));
        Assert.assertTrue(decoder.isCompleted());

        Header[] footers = decoder.getFooters();
        Assert.assertEquals(2, footers.length);
        Assert.assertEquals("Footer1", footers[0].getName());
        Assert.assertEquals("abcde", footers[0].getValue());
        Assert.assertEquals("Footer2", footers[1].getName());
        Assert.assertEquals("fghij", footers[1].getValue());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testMalformedChunkSizeDecoding() throws Exception {
        String s = "5\r\n01234\r\n5zz\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        try {
            decoder.read(dst);
            Assert.fail("MalformedChunkCodingException should have been thrown");
        } catch (MalformedChunkCodingException ex) {
            // expected
        }
    }

    @Test
    public void testMalformedChunkEndingDecoding() throws Exception {
        String s = "5\r\n01234\r\n5\r\n56789\n\r6\r\nabcdef\r\n0\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        try {
            decoder.read(dst);
            Assert.fail("MalformedChunkCodingException should have been thrown");
        } catch (MalformedChunkCodingException ex) {
            // expected
        }
    }

    @Test
    public void testMalformedChunkTruncatedChunk() throws Exception {
        String s = "3\r\n12";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);
        Assert.assertEquals(2, decoder.read(dst));
        try {
            decoder.read(dst);
            Assert.fail("MalformedChunkCodingException should have been thrown");
        } catch (MalformedChunkCodingException ex) {
            // expected
        }
    }

    @Test
    public void testFoldedFooters() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\n   \r\n  fghij\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(26, bytesRead);
        Assert.assertEquals("12345678901234561234512345", convert(dst));

        Header[] footers = decoder.getFooters();
        Assert.assertEquals(1, footers.length);
        Assert.assertEquals("Footer1", footers[0].getName());
        Assert.assertEquals("abcde  fghij", footers[0].getValue());
    }

    @Test
    public void testMalformedFooters() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        try {
            decoder.read(dst);
            Assert.fail("MalformedChunkCodingException should have been thrown");
        } catch (IOException ex) {
            // expected
        }
    }

    @Test
    public void testEndOfStreamConditionReadingLastChunk() throws Exception {
        String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assert.assertEquals(26, bytesRead);
        Assert.assertEquals("12345678901234561234512345", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testReadingWitSmallBuffer() throws Exception {
        String s = "10\r\n1234567890123456\r\n" +
                "40\r\n12345678901234561234567890123456" +
                "12345678901234561234567890123456\r\n0\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);
        ByteBuffer tmp = ByteBuffer.allocate(10);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            int i = decoder.read(tmp);
            if (i > 0) {
                bytesRead += i;
                tmp.flip();
                dst.put(tmp);
                tmp.compact();
            }
        }

        Assert.assertEquals(80, bytesRead);
        Assert.assertEquals("12345678901234561234567890123456" +
                "12345678901234561234567890123456" +
                "1234567890123456", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testEndOfStreamConditionReadingFooters() throws Exception {
        String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assert.assertEquals(26, bytesRead);
        Assert.assertEquals("12345678901234561234512345", convert(dst));
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testInvalidConstructor() {
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        try {
            new ChunkDecoder(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkDecoder(channel, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkDecoder(channel, inbuf, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }

    @Test
    public void testInvalidInput() throws Exception {
        String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, "US-ASCII");
        HttpParams params = new BasicHttpParams();

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        try {
            decoder.read(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
