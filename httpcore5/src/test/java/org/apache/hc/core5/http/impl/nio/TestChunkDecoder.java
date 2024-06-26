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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.ReadableByteChannelMock;
import org.apache.hc.core5.http.TruncatedChunkException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link ChunkDecoder}.
 */
class TestChunkDecoder {

    @Test
    void testBasicDecoding() throws Exception {
        final String s = "5\r\n01234\r\n5\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = decoder.read(dst);
        Assertions.assertEquals(16, bytesRead);
        Assertions.assertEquals("0123456789abcdef", CodecTestUtils.convert(dst));
        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertNull(trailers);

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
        Assertions.assertEquals("[chunk-coded; completed: true]", decoder.toString());
    }

    @Test
    void testComplexDecoding() throws Exception {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\nFooter2: fghij\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assertions.assertEquals(26, bytesRead);
        Assertions.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));

        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertEquals(2, trailers.size());
        Assertions.assertEquals("Footer1", trailers.get(0).getName());
        Assertions.assertEquals("abcde", trailers.get(0).getValue());
        Assertions.assertEquals("Footer2", trailers.get(1).getName());
        Assertions.assertEquals("fghij", trailers.get(1).getValue());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
    }

    @Test
    void testDecodingWithSmallBuffer() throws Exception {
        final String s1 = "5\r\n01234\r\n5\r\n5678";
        final String s2 = "9\r\n6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s1, s2}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
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

        Assertions.assertEquals(16, bytesRead);
        Assertions.assertEquals("0123456789abcdef", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
    }

    @Test
    void testMalformedChunk() {
        final String s = "5\r\n01234----------------------------------------------------------" +
                "-----------------------------------------------------------------------------" +
                "-----------------------------------------------------------------------------";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(32, 32, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        Assertions.assertThrows(MalformedChunkCodingException.class, () -> decoder.read(dst));
    }

    @Test
    void testIncompleteChunkDecoding() throws Exception {
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
                chunks, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assertions.assertEquals(27, bytesRead);
        Assertions.assertEquals("123456789012345612345abcdef", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());

        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertEquals(2, trailers.size());
        Assertions.assertEquals("Footer1", trailers.get(0).getName());
        Assertions.assertEquals("abcde", trailers.get(0).getValue());
        Assertions.assertEquals("Footer2", trailers.get(1).getName());
        Assertions.assertEquals("fghij", trailers.get(1).getValue());

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(-1, bytesRead);
        Assertions.assertTrue(decoder.isCompleted());
    }

    @Test
    void testMalformedChunkSizeDecoding() {
        final String s = "5\r\n01234\r\n5zz\r\n56789\r\n6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        Assertions.assertThrows(MalformedChunkCodingException.class, () ->
                decoder.read(dst));
    }

    @Test
    void testMalformedChunkEndingDecoding() {
        final String s = "5\r\n01234\r\n5\r\n56789\r\r6\r\nabcdef\r\n0\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        Assertions.assertThrows(MalformedChunkCodingException.class, () ->
                decoder.read(dst));
    }

    @Test
    void testMalformedChunkTruncatedChunk() throws Exception {
        final String s = "3\r\n12";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        Assertions.assertEquals(2, decoder.read(dst));
        Assertions.assertThrows(TruncatedChunkException.class, () ->
                decoder.read(dst));
    }

    @Test
    void testFoldedFooters() throws Exception {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1: abcde\r\n   \r\n  fghij\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder.read(dst);
        Assertions.assertEquals(26, bytesRead);
        Assertions.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));

        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertEquals(1, trailers.size());
        Assertions.assertEquals("Footer1", trailers.get(0).getName());
        Assertions.assertEquals("abcde  fghij", trailers.get(0).getValue());
    }

    @Test
    void testMalformedFooters() {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);
        Assertions.assertThrows(IOException.class, () ->
                decoder.read(dst));
    }

    @Test
    void testMissingLastCRLF() {
        final String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        Assertions.assertThrows(MalformedChunkCodingException.class, () -> {
            while (dst.hasRemaining() && !decoder.isCompleted()) {
                decoder.read(dst);
            }
        });
    }

    @Test
    void testMissingClosingChunk() {
        final String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        Assertions.assertThrows(ConnectionClosedException.class, () -> {
            long bytesRead = 0;
            try {
                while (dst.hasRemaining() && !decoder.isCompleted()) {
                    final int i = decoder.read(dst);
                    if (i > 0) {
                        bytesRead += i;
                    }
                }
            } catch (final MalformedChunkCodingException ex) {
                Assertions.assertEquals(26L, bytesRead);
                Assertions.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));
                Assertions.assertTrue(decoder.isCompleted());
                throw ex;
            }
        });
    }

    @Test
    void testReadingWitSmallBuffer() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "40\r\n12345678901234561234567890123456" +
                "12345678901234561234567890123456\r\n0\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
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

        Assertions.assertEquals(80, bytesRead);
        Assertions.assertEquals("12345678901234561234567890123456" +
                "12345678901234561234567890123456" +
                "1234567890123456", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());
    }

    @Test
    void testEndOfStreamConditionReadingFooters() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        int bytesRead = 0;
        while (dst.hasRemaining() && !decoder.isCompleted()) {
            final int i = decoder.read(dst);
            if (i > 0) {
                bytesRead += i;
            }
        }

        Assertions.assertEquals(26, bytesRead);
        Assertions.assertEquals("12345678901234561234512345", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder.isCompleted());
    }

    @Test
    void testTooLongChunkHeader() throws Exception {
        final String s = "5; and some very looooong comment\r\n12345\r\n0\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final BasicHttpTransportMetrics metrics1 = new BasicHttpTransportMetrics();
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256);
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        while (dst.hasRemaining() && !decoder1.isCompleted()) {
            decoder1.read(dst);
        }
        Assertions.assertEquals("12345", CodecTestUtils.convert(dst));
        Assertions.assertTrue(decoder1.isCompleted());

        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256, 10);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, metrics2);

        dst.clear();
        Assertions.assertThrows(MessageConstraintException.class, () -> decoder2.read(dst));
    }

    @Test
    void testTooLongFooter() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "0\r\nFooter1: looooooooooooooooooooooooooooooooooooooooooooooooooooooog\r\n\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256, 0);
        final BasicHttpTransportMetrics metrics1 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder1.read(dst);
        Assertions.assertEquals(16, bytesRead);
        Assertions.assertEquals("1234567890123456", CodecTestUtils.convert(dst));
        final List<? extends Header> trailers = decoder1.getTrailers();
        Assertions.assertNotNull(trailers);
        Assertions.assertEquals(1, trailers.size());

        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                25, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, metrics2);

        dst.clear();
        Assertions.assertThrows(MessageConstraintException.class, () -> decoder2.read(dst));
    }

    @Test
    void testTooLongFoldedFooter() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "0\r\nFooter1: blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n  blah\r\n\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256,
                0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics1 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder1.read(dst);
        Assertions.assertEquals(16, bytesRead);
        Assertions.assertEquals("1234567890123456", CodecTestUtils.convert(dst));
        final List<? extends Header> trailers = decoder1.getTrailers();
        Assertions.assertNotNull(trailers);
        Assertions.assertEquals(1, trailers.size());

        final Http1Config http1Config = Http1Config.custom()
                .setMaxLineLength(25)
                .build();
        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, http1Config, metrics2);

        dst.clear();
        Assertions.assertThrows(MessageConstraintException.class, () -> decoder2.read(dst));
    }

    @Test
    void testTooManyFooters() throws Exception {
        final String s = "10\r\n1234567890123456\r\n" +
                "0\r\nFooter1: blah\r\nFooter2: blah\r\nFooter3: blah\r\nFooter4: blah\r\n\r\n";
        final ReadableByteChannel channel1 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 256,0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics1 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder1 = new ChunkDecoder(channel1, inbuf1, metrics1);

        final ByteBuffer dst = ByteBuffer.allocate(1024);

        final int bytesRead = decoder1.read(dst);
        Assertions.assertEquals(16, bytesRead);
        Assertions.assertEquals("1234567890123456", CodecTestUtils.convert(dst));
        final List<? extends Header> trailers = decoder1.getTrailers();
        Assertions.assertNotNull(trailers);
        Assertions.assertEquals(4, trailers.size());

        final Http1Config http1Config = Http1Config.custom()
                .setMaxHeaderCount(3).build();
        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, http1Config, metrics2);

        dst.clear();
        Assertions.assertThrows(MessageConstraintException.class, () -> decoder2.read(dst));
    }

    @Test
    void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        Assertions.assertThrows(NullPointerException.class, () -> new ChunkDecoder(null, null, null));
        Assertions.assertThrows(NullPointerException.class, () -> new ChunkDecoder(channel, inbuf, null));
    }

    @Test
    void testInvalidInput() {
        final String s = "10;key=\"value\"\r\n1234567890123456\r\n" +
                "5\r\n12345\r\n5\r\n12345\r\n0\r\nFooter1 abcde\r\n\r\n";
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);
        Assertions.assertThrows(NullPointerException.class, () ->
                decoder.read(null));
    }

    @Test
    void testHugeChunk() throws Exception {
        final String s = "1234567890abcdef\r\n0123456789abcdef";
        final ReadableByteChannel channel = new ReadableByteChannelMock(new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, metrics);

        final ByteBuffer dst = ByteBuffer.allocate(4);

        int bytesRead = decoder.read(dst);
        Assertions.assertEquals(4, bytesRead);
        Assertions.assertEquals("0123", CodecTestUtils.convert(dst));
        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertEquals(4, bytesRead);
        Assertions.assertEquals("4567", CodecTestUtils.convert(dst));
    }

}
