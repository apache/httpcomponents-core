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
import org.assertj.core.api.Assertions;
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
        Assertions.assertThat(bytesRead).isEqualTo(16);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("0123456789abcdef");
        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertThat(trailers).isNull();

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
        Assertions.assertThat(decoder.toString()).isEqualTo("[chunk-coded; completed: true]");
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

        Assertions.assertThat(bytesRead).isEqualTo(26);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("12345678901234561234512345");

        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertThat(trailers.size()).isEqualTo(2);
        Assertions.assertThat(trailers.get(0).getName()).isEqualTo("Footer1");
        Assertions.assertThat(trailers.get(0).getValue()).isEqualTo("abcde");
        Assertions.assertThat(trailers.get(1).getName()).isEqualTo("Footer2");
        Assertions.assertThat(trailers.get(1).getValue()).isEqualTo("fghij");

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
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

        Assertions.assertThat(bytesRead).isEqualTo(16);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("0123456789abcdef");
        Assertions.assertThat(decoder.isCompleted()).isTrue();

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
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

        Assertions.assertThatThrownBy(() ->
                        decoder.read(dst))
                .isInstanceOf(MalformedChunkCodingException.class);
    }

    private static void decode(final String s) throws Exception {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        final ChunkDecoder decoder = new ChunkDecoder(channel, inbuf, new BasicHttpTransportMetrics());
        final ByteBuffer dst = ByteBuffer.allocate(1024);
        for (int i = 0; i < 10 && !decoder.isCompleted(); i++) {
            dst.clear();
            if (decoder.read(dst) == -1) {
                break;
            }
        }
    }

    @Test
    void testChunkSizeMinusOneRejected() {
        Assertions.assertThatThrownBy(() -> decode("-1\r\n5\r\nhello\r\n0\r\n\r\n"))
                .isInstanceOf(MalformedChunkCodingException.class);
    }

    @Test
    void testChunkSizeNegativeRejected() {
        Assertions.assertThatThrownBy(() -> decode("-5\r\nhello\r\n0\r\n\r\n"))
                .isInstanceOf(MalformedChunkCodingException.class);
    }

    @Test
    void testChunkSizePlusSignRejected() {
        Assertions.assertThatThrownBy(() -> decode("+5\r\nhello\r\n0\r\n\r\n"))
                .isInstanceOf(MalformedChunkCodingException.class);
    }

    @Test
    void testChunkSizeMinusZeroRejected() {
        Assertions.assertThatThrownBy(() -> decode("-0\r\n\r\n"))
                .isInstanceOf(MalformedChunkCodingException.class);
    }

    @Test
    void testChunkSizeBlankRejected() {
        Assertions.assertThatThrownBy(() -> decode("  \r\nhello\r\n0\r\n\r\n"))
                .isInstanceOf(MalformedChunkCodingException.class);
    }

    @Test
    void testChunkSizeOverflowRejected() {
        Assertions.assertThatThrownBy(() ->
                        decode("FFFFFFFFFFFFFFFFF\r\nhello\r\n0\r\n\r\n"))
                .isInstanceOf(MalformedChunkCodingException.class);
    }

    @Test
    void testChunkSizeSurroundingWhitespaceAccepted() throws Exception {
        decode(" 5 \r\nhello\r\n0\r\n\r\n");
    }

    @Test
    void testChunkSizeLeadingZerosAccepted() throws Exception {
        decode("0005\r\nhello\r\n0\r\n\r\n");
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

        Assertions.assertThat(bytesRead).isEqualTo(27);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("123456789012345612345abcdef");
        Assertions.assertThat(decoder.isCompleted()).isTrue();

        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertThat(trailers.size()).isEqualTo(2);
        Assertions.assertThat(trailers.get(0).getName()).isEqualTo("Footer1");
        Assertions.assertThat(trailers.get(0).getValue()).isEqualTo("abcde");
        Assertions.assertThat(trailers.get(1).getName()).isEqualTo("Footer2");
        Assertions.assertThat(trailers.get(1).getValue()).isEqualTo("fghij");

        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(-1);
        Assertions.assertThat(decoder.isCompleted()).isTrue();
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
        Assertions.assertThatThrownBy(() ->
                decoder.read(dst)).isInstanceOf(MalformedChunkCodingException.class);
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
        Assertions.assertThatThrownBy(() ->
                decoder.read(dst)).isInstanceOf(MalformedChunkCodingException.class);
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
        Assertions.assertThat(decoder.read(dst)).isEqualTo(2);
        Assertions.assertThatThrownBy(() ->
                decoder.read(dst)).isInstanceOf(TruncatedChunkException.class);
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
        Assertions.assertThat(bytesRead).isEqualTo(26);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("12345678901234561234512345");

        final List<? extends Header> trailers = decoder.getTrailers();
        Assertions.assertThat(trailers.size()).isEqualTo(1);
        Assertions.assertThat(trailers.get(0).getName()).isEqualTo("Footer1");
        Assertions.assertThat(trailers.get(0).getValue()).isEqualTo("abcde  fghij");
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
        Assertions.assertThatThrownBy(() ->
                decoder.read(dst)).isInstanceOf(IOException.class);
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

        Assertions.assertThatThrownBy(() -> {
            while (dst.hasRemaining() && !decoder.isCompleted()) {
                decoder.read(dst);
            }
        }).isInstanceOf(MalformedChunkCodingException.class);
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

        Assertions.assertThatThrownBy(() -> {
            long bytesRead = 0;
            try {
                while (dst.hasRemaining() && !decoder.isCompleted()) {
                    final int i = decoder.read(dst);
                    if (i > 0) {
                        bytesRead += i;
                    }
                }
            } catch (final MalformedChunkCodingException ex) {
                Assertions.assertThat(bytesRead).isEqualTo(26L);
                Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("12345678901234561234512345");
                Assertions.assertThat(decoder.isCompleted()).isTrue();
                throw ex;
            }
        }).isInstanceOf(ConnectionClosedException.class);
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

        Assertions.assertThat(bytesRead).isEqualTo(80);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("12345678901234561234567890123456" +
                "12345678901234561234567890123456" +
                "1234567890123456");
        Assertions.assertThat(decoder.isCompleted()).isTrue();
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

        Assertions.assertThat(bytesRead).isEqualTo(26);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("12345678901234561234512345");
        Assertions.assertThat(decoder.isCompleted()).isTrue();
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
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("12345");
        Assertions.assertThat(decoder1.isCompleted()).isTrue();

        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256, 10);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, metrics2);

        dst.clear();
        Assertions.assertThatThrownBy(() ->
                        decoder2.read(dst))
                .isInstanceOf(MessageConstraintException.class);
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
        Assertions.assertThat(bytesRead).isEqualTo(16);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("1234567890123456");
        final List<? extends Header> trailers = decoder1.getTrailers();
        Assertions.assertThat(trailers).isNotNull();
        Assertions.assertThat(trailers.size()).isEqualTo(1);

        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                25, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, metrics2);

        dst.clear();
        Assertions.assertThatThrownBy(() ->
                        decoder2.read(dst))
                .isInstanceOf(MessageConstraintException.class);
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
        Assertions.assertThat(bytesRead).isEqualTo(16);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("1234567890123456");
        final List<? extends Header> trailers = decoder1.getTrailers();
        Assertions.assertThat(trailers).isNotNull();
        Assertions.assertThat(trailers.size()).isEqualTo(1);

        final Http1Config http1Config = Http1Config.custom()
                .setMaxLineLength(25)
                .build();
        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, http1Config, metrics2);

        dst.clear();
        Assertions.assertThatThrownBy(() ->
                        decoder2.read(dst))
                .isInstanceOf(MessageConstraintException.class);
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
        Assertions.assertThat(bytesRead).isEqualTo(16);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("1234567890123456");
        final List<? extends Header> trailers = decoder1.getTrailers();
        Assertions.assertThat(trailers).isNotNull();
        Assertions.assertThat(trailers.size()).isEqualTo(4);

        final Http1Config http1Config = Http1Config.custom()
                .setMaxHeaderCount(3).build();
        final ReadableByteChannel channel2 = new ReadableByteChannelMock(
                new String[] {s}, StandardCharsets.US_ASCII);
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 256,
                0, StandardCharsets.US_ASCII);
        final BasicHttpTransportMetrics metrics2 = new BasicHttpTransportMetrics();
        final ChunkDecoder decoder2 = new ChunkDecoder(channel2, inbuf2, http1Config, metrics2);

        dst.clear();
        Assertions.assertThatThrownBy(() ->
                        decoder2.read(dst))
                .isInstanceOf(MessageConstraintException.class);
    }

    @Test
    void testInvalidConstructor() {
        final ReadableByteChannel channel = new ReadableByteChannelMock(
                new String[] {"stuff;", "more stuff"}, StandardCharsets.US_ASCII);

        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 256, 0, StandardCharsets.US_ASCII);
        Assertions.assertThatThrownBy(() ->
                        new ChunkDecoder(null, null, null))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new ChunkDecoder(channel, inbuf, null))
                .isInstanceOf(NullPointerException.class);
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
        Assertions.assertThatThrownBy(() ->
                decoder.read(null)).isInstanceOf(NullPointerException.class);
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
        Assertions.assertThat(bytesRead).isEqualTo(4);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("0123");
        dst.clear();
        bytesRead = decoder.read(dst);
        Assertions.assertThat(bytesRead).isEqualTo(4);
        Assertions.assertThat(CodecTestUtils.convert(dst)).isEqualTo("4567");
    }

}
