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

import org.apache.http.ConnectionClosedException;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.MessageConstraintException;
import org.apache.http.ReadableByteChannelMock;
import org.apache.http.TruncatedChunkException;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link ChunkDecoder}.
 */
public class TestChunkDecoder {

    @Test
    public void testBasicDecoding() throws Exception {
        final String s = "5\r\n01234\r\n5\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("0123456789abcdef", CodecTestUtils.convert(dst));
        final Header[] footers = decoder.getFooters();
        Assert.assertEquals(0, footers.length);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
        Assert.assertEquals("[chunk-coded; completed: true]", decoder.toString());
    }

    @Test
    public void testComplexDecoding() throws Exception {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\nFooter2: fghij\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assert.assertEquals(26, bytesRead);
        Assert.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));

        final Header[] footers = decoder.getFooters();
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
        final String s1 = "5\r\n01234\r\n5\r\n5678";
        final String s2 = "9\r\n6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s1, s2}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        final ByteBuffer tmp = ByteBuffer.allocate(4);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(tmp);
            if (i > 0) {
                bytesRead += i;
            }
            tmp.flip();
            dst.put(tmp);
            tmp.compact();
        }

        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("0123456789abcdef", CodecTestUtils.convert(dst));
        Assert.assertTrue(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(-1, bytesRead);
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testMalformedChunk() throws Exception {
        final String s = "5\r\n01234----------------------------------------------------------" +
                "-----------------------------------------------------------------------------" +
                "-----------------------------------------------------------------------------";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(32, 32, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        try {
            decoder.read(dst);
            Assert.fail("MalformedChunkCodingException should have been thrown");
        } catch (final MalformedChunkCodingException ex) {
            // expected
        }
    }

    @Test
    public void testIncompleteChunkDecoding() throws Exception {
        final String[] chunks = {
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
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                chunks, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assert.assertEquals(27, bytesRead);
        Assert.assertEquals("123456789012345612345abcdef", CodecTestUtils.convert(dst));
        Assert.assertTrue(decoder.isCompleted());

        final Header[] footers = decoder.getFooters();
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

    @Test(expected=MalformedChunkCodingException.class)
    public void testMalformedChunkSizeDecoding() throws Exception {
        final String s = "5\r\n01234\r\n5zz\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        decoder.read(dst);
    }

    @Test(expected=MalformedChunkCodingException.class)
    public void testMalformedChunkEndingDecoding() throws Exception {
        final String s = "5\r\n01234\r\n5\r\n56789\r\r6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        decoder.read(dst);
    }

    @Test(expected=TruncatedChunkException.class)
    public void testMalformedChunkTruncatedChunk() throws Exception {
        final String s = "3\r\n12";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        Assert.assertEquals(2, decoder.read(dst));
        decoder.read(dst);
    }

    @Test
    public void testFoldedFooters() throws Exception {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\n   \r\n  fghij\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder.read(dst);
        Assert.assertEquals(26, bytesRead);
        Assert.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));

        final Header[] footers = decoder.getFooters();
        Assert.assertEquals(1, footers.length);
        Assert.assertEquals("Footer1", footers[0].getName());
        Assert.assertEquals("abcde  fghij", footers[0].getValue());
    }

    @Test(expected=IOException.class)
    public void testMalformedFooters() throws Exception {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        decoder.read(dst);
    }

    @Test(expected=MalformedChunkCodingException.class)
    public void testMissingLastCRLF() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        while (dst.hasRemaining() && !decoder.isCompleted()) {
            decoder.read(dst);
        }
    }

    @Test(expected=ConnectionClosedException.class)
    public void testMissingClosingChunk() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        long bytesRead = 0;
        try {
            while (dst.hasRemaining() && !decoder.isCompleted()) {
                final int i = decoder.read(dst);
                if (i > 0) {
                    bytesRead += i;
                }
            }
        } catch (final MalformedChunkCodingException ex) {
            Assert.assertEquals(26L, bytesRead);
            Assert.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));
            Assert.assertTrue(decoder.isCompleted());
            throw ex;
        }
    }

    @Test
    public void testReadingWitSmallBuffer() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "40\r\n12345678901234561234567890123456" +
                "12345678901234561234567890123456\r\n0\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        final ByteBuffer tmp = ByteBuffer.allocate(10);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(tmp);
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
                "1234567890123456", CodecTestUtils.convert(dst));
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testEndOfStreamConditionReadingFooters() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assert.assertEquals(26, bytesRead);
        Assert.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));
        Assert.assertTrue(decoder.isCompleted());
    }

    @Test
    public void testTooLongChunkHeader() throws Exception {
        final String s = "5; and some very looooong comment\r\n12345\r\n0\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256,
                MessageConstraints.DEFAULT, null, null);
        final HttpTransportMetricsImpl metrics1 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        while (dst.hasRemaining() && !decoder1.isCompleted()) {
            decoder1.read(dst);
        }
        Assert.assertEquals("12345", CodecTestUtils.convert(dst));
        Assert.assertTrue(decoder1.isCompleted());

        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                MessageConstraints.lineLen(10), null, null);
        final HttpTransportMetricsImpl metrics2 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, metrics2);

        dst.clear();
        try {
            decoder2.read(dst);
            Assert.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test
    public void testTooLongFooter() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "0\r\nFooter1: looooooooooooooooooooooooooooooooooooooooooooooooooooooog\r\n\r\n";
//        final String s = "10\r\n1234567890123456\r\n" +
//                "0\r\nFooter1: looooooooooooooooooooooooooooooooooooooooog\r\n   \r\n  fghij\r\n\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256,
                MessageConstraints.DEFAULT, Consts.ASCII);
        final HttpTransportMetricsImpl metrics1 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder1.read(dst);
        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("1234567890123456", CodecTestUtils.convert(dst));
        final Header[] footers = decoder1.getFooters();
        Assert.assertNotNull(footers);
        Assert.assertEquals(1, footers.length);

        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                MessageConstraints.lineLen(25), Consts.ASCII);
        final HttpTransportMetricsImpl metrics2 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, metrics2);

        dst.clear();
        try {
            decoder2.read(dst);
            Assert.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test
    public void testTooLongFoldedFooter() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "0\r\nFooter1: blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256,
                MessageConstraints.DEFAULT, Consts.ASCII);
        final HttpTransportMetricsImpl metrics1 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder1.read(dst);
        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("1234567890123456", CodecTestUtils.convert(dst));
        final Header[] footers = decoder1.getFooters();
        Assert.assertNotNull(footers);
        Assert.assertEquals(1, footers.length);

        final MessageConstraints constraints = MessageConstraints.lineLen(25);
        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                constraints, Consts.ASCII);
        final HttpTransportMetricsImpl metrics2 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, constraints, metrics2);

        dst.clear();
        try {
            decoder2.read(dst);
            Assert.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test
    public void testTooManyFooters() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "0\r\nFooter1: blah\r\nFooter2: blah\r\nFooter3: blah\r\nFooter4: blah\r\n\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256,
                MessageConstraints.DEFAULT, Consts.ASCII);
        final HttpTransportMetricsImpl metrics1 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder1.read(dst);
        Assert.assertEquals(16, bytesRead);
        Assert.assertEquals("1234567890123456", CodecTestUtils.convert(dst));
        final Header[] footers = decoder1.getFooters();
        Assert.assertNotNull(footers);
        Assert.assertEquals(4, footers.length);

        final MessageConstraints constraints = MessageConstraints.custom()
                .setMaxHeaderCount(3).build();
        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                constraints, Consts.ASCII);
        final HttpTransportMetricsImpl metrics2 = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, constraints, metrics2);

        dst.clear();
        try {
            decoder2.read(dst);
            Assert.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test
    public void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        try {
            new ChunkDecoder(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkDecoder(channel, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new ChunkDecoder(channel, inbuf, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidInput() throws Exception {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);
        decoder.read(null);
    }

    @Test
    public void testHugeChunk() throws Exception {
        final String s = "1234567890abcdef\r\n0123456789abcdef";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, Consts.ASCII);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(4);

        int bytesRead = decoder.read(dst);
        Assert.assertEquals(4, bytesRead);
        Assert.assertEquals("0123", CodecTestUtils.convert(dst));
        dst.clear();
        bytesRead = decoder.read(dst);
        Assert.assertEquals(4, bytesRead);
        Assert.assertEquals("4567", CodecTestUtils.convert(dst));
    }

}
