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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Simple tests for {@link ChunkEncoder}.
 */
class TestChunkEncoder {

    @Test
    void testBasicCoding() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(CodecTestUtils.wrap("12345"));
        encoder.write(CodecTestUtils.wrap("678"));
        encoder.write(CodecTestUtils.wrap("90"));
        encoder.complete();

        outbuf.flush(channel);

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n");
        Assertions.assertThat(encoder.toString()).isEqualTo("[chunk-coded; completed: true]");
    }

    @Test
    void testChunkNoExceed() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);
        encoder.write(CodecTestUtils.wrap("1234"));
        encoder.complete();

        outbuf.flush(channel);

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("4\r\n1234\r\n0\r\n\r\n");
    }

    @Test // See HTTPCORE-239
    void testLimitedChannel() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(16, 16);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        // fill up the channel
        channel.write(CodecTestUtils.wrap("0123456789ABCDEF"));
        // fill up the out buffer
        outbuf.write(CodecTestUtils.wrap("0123456789ABCDEF"));

        final ByteBuffer src = CodecTestUtils.wrap("0123456789ABCDEF");
        Assertions.assertThat(encoder.write(src)).isEqualTo(0);
        Assertions.assertThat(encoder.write(src)).isEqualTo(0);
        Assertions.assertThat(encoder.write(src)).isEqualTo(0);

        // should not be able to copy any bytes, until we flush the channel and buffer
        channel.reset();
        outbuf.flush(channel);
        channel.reset();

        Assertions.assertThat(encoder.write(src)).isEqualTo(10);
        channel.flush();
        Assertions.assertThat(encoder.write(src)).isEqualTo(6);
        channel.flush();
        Assertions.assertThat(encoder.write(src)).isEqualTo(0);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("4\r\n0123\r\n4\r\n4567\r\n2\r\n89\r\n4\r\nABCD\r\n2\r\nEF\r\n");
    }

    @Test
    void testBufferFragments() throws Exception {
        final WritableByteChannelMock channel = Mockito.spy(new WritableByteChannelMock(1024));
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 1024);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics, 1024);

        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("0123456789ABCDEF"))).isEqualTo(16);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("0123456789ABCDEF"))).isEqualTo(16);
        Assertions.assertThat(encoder.write(CodecTestUtils.wrap("0123456789ABCDEF"))).isEqualTo(16);

        Mockito.verify(channel, Mockito.never()).write(ArgumentMatchers.any());

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("10\r\n0123456789ABCDEF\r\n10\r\n0123456789ABCDEF\r\n" +
                "10\r\n0123456789ABCDEF\r\n");
    }

    @Test
    void testChunkExceed() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        final ByteBuffer src = CodecTestUtils.wrap("0123456789ABCDEF");

        Assertions.assertThat(encoder.write(src)).isEqualTo(16);
        Assertions.assertThat(src.remaining()).isEqualTo(0);

        outbuf.flush(channel);
        final String s = channel.dump(StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("4\r\n0123\r\n4\r\n4567\r\n4\r\n89AB\r\n4\r\nCDEF\r\n");

    }

    @Test
    void testCodingEmptyBuffer() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(CodecTestUtils.wrap("12345"));
        encoder.write(CodecTestUtils.wrap("678"));
        encoder.write(CodecTestUtils.wrap("90"));

        final ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);

        encoder.complete();

        outbuf.flush(channel);

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("5\r\n12345\r\n3\r\n678\r\n2\r\n90\r\n0\r\n\r\n");
    }

    @Test
    void testCodingCompleted() throws Exception {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics);

        encoder.write(CodecTestUtils.wrap("12345"));
        encoder.write(CodecTestUtils.wrap("678"));
        encoder.write(CodecTestUtils.wrap("90"));
        encoder.complete();

        final ByteBuffer wrapped = CodecTestUtils.wrap("more stuff");

        Assertions.assertThatThrownBy(() ->
                        encoder.write(wrapped))
                .isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() ->
                        encoder.complete())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testInvalidConstructor() {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);

        Assertions.assertThatThrownBy(() ->
                        new ChunkEncoder(null, null, null))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new ChunkEncoder(channel, null, null))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new ChunkEncoder(channel, outbuf, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testTrailers() throws IOException {
        final WritableByteChannelMock channel = new WritableByteChannelMock(64);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128);
        final BasicHttpTransportMetrics metrics = new BasicHttpTransportMetrics();
        final ChunkEncoder encoder = new ChunkEncoder(channel, outbuf, metrics, 0);
        encoder.write(CodecTestUtils.wrap("1"));
        encoder.write(CodecTestUtils.wrap("23"));
        encoder.complete(Arrays.asList(new BasicHeader("E", ""), new BasicHeader("Y", "Z")));

        outbuf.flush(channel);

        final String s = channel.dump(StandardCharsets.US_ASCII);

        Assertions.assertThat(encoder.isCompleted()).isTrue();
        Assertions.assertThat(s).isEqualTo("1\r\n1\r\n2\r\n23\r\n0\r\nE: \r\nY: Z\r\n\r\n");
        Assertions.assertThat(encoder.toString()).isEqualTo("[chunk-coded; completed: true]");
    }
}
