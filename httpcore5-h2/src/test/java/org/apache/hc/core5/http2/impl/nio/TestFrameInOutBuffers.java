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

package org.apache.hc.core5.http2.impl.nio;

import java.nio.ByteBuffer;

import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2CorruptFrameException;
import org.apache.hc.core5.http2.ReadableByteChannelMock;
import org.apache.hc.core5.http2.WritableByteChannelMock;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestFrameInOutBuffers {

    @Test
    void testReadWriteFrame() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[]{1,2,3,4,5}));
        outbuffer.write(frame, writableChannel);

        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final byte[] bytes = writableChannel.toByteArray();
        Assertions.assertThat(bytes.length).isEqualTo(FrameConsts.HEAD_LEN + 5);

        Assertions.assertThat(outbuffer.getMetrics().getFramesTransferred()).isEqualTo(1);
        Assertions.assertThat(outbuffer.getMetrics().getBytesTransferred()).isEqualTo(bytes.length);

        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(bytes);
        final RawFrame frame2 = inBuffer.read(readableChannel);
        Assertions.assertThat(frame2.getType()).isEqualTo(FrameType.DATA.getValue());
        Assertions.assertThat(frame2.getFlags()).isEqualTo(0);
        Assertions.assertThat(frame2.getStreamId()).isEqualTo(1L);
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assertions.assertThat(payload2).isNotNull();
        Assertions.assertThat(payload2.remaining()).isEqualTo(5);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 4);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 5);
        Assertions.assertThat(readableChannel.read(ByteBuffer.allocate(1024))).isEqualTo(-1);

        Assertions.assertThat(inBuffer.getMetrics().getFramesTransferred()).isEqualTo(1);
        Assertions.assertThat(inBuffer.getMetrics().getBytesTransferred()).isEqualTo(bytes.length);

        final RawFrame frame3 = inBuffer.read(ByteBuffer.wrap(bytes), readableChannel);
        Assertions.assertThat(frame3.getType()).isEqualTo(FrameType.DATA.getValue());
        Assertions.assertThat(frame3.getFlags()).isEqualTo(0);
        Assertions.assertThat(frame3.getStreamId()).isEqualTo(1L);
        final ByteBuffer payload3 = frame3.getPayloadContent();
        Assertions.assertThat(payload3).isNotNull();
        Assertions.assertThat(payload3.remaining()).isEqualTo(5);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 4);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 5);

        Assertions.assertThat(inBuffer.getMetrics().getFramesTransferred()).isEqualTo(2);
        Assertions.assertThat(inBuffer.getMetrics().getBytesTransferred()).isEqualTo(bytes.length * 2);
    }

    @Test
    void testPartialFrameWrite() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024, FrameConsts.HEAD_LEN + 10);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);
        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), FrameFlag.END_STREAM.getValue(), 5,
                ByteBuffer.wrap(new byte[]{'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'}));

        outbuffer.write(frame, writableChannel);
        Assertions.assertThat(writableChannel.toByteArray())
                .containsExactly(new byte[]{0, 0, 16, 0, 1, 0, 0, 0, 5, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57});

        Assertions.assertThat(outbuffer.isEmpty()).isFalse();
        outbuffer.flush(writableChannel);
        Assertions.assertThat(writableChannel.toByteArray())
                .containsExactly(new byte[]{0, 0, 16, 0, 1, 0, 0, 0, 5, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57});

        writableChannel.flush();
        outbuffer.flush(writableChannel);
        Assertions.assertThat(writableChannel.toByteArray())
                .containsExactly(new byte[]{0, 0, 16, 0, 1, 0, 0, 0, 5, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 97, 98, 99, 100, 101, 102});
    }

    @Test
    void testReadFrameMultiple() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,0,1,2,3,4,0,0,0,0,
                        0,0,10,0,9,0,0,0,8,4,5,6,7,8,9,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(readableChannel);
        Assertions.assertThat(FrameType.valueOf(frame1.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame1.getFlags()).isEqualTo(8);
        Assertions.assertThat(frame1.getStreamId()).isEqualTo(8);
        final ByteBuffer payload1 = frame1.getPayloadContent();
        Assertions.assertThat(payload1).isNotNull();
        Assertions.assertThat(payload1.remaining()).isEqualTo(5);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 0);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 4);

        final RawFrame frame2 = inBuffer.read(readableChannel);
        Assertions.assertThat(FrameType.valueOf(frame2.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame2.getFlags()).isEqualTo(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED));
        Assertions.assertThat(frame2.getStreamId()).isEqualTo(8);
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assertions.assertThat(payload2).isNotNull();
        Assertions.assertThat(payload2.remaining()).isEqualTo(5);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 5);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 6);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 7);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 8);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 9);

        Assertions.assertThat(readableChannel.read(ByteBuffer.allocate(1024))).isEqualTo(-1);
    }

    @Test
    void testReadFrameMultipleSmallBuffer() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(new BasicH2TransportMetrics(), 20, 10);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,1,1,1,1,1,0,0,0,0,
                        0,0,5,0,0,0,0,0,8,2,2,2,2,2,
                        0,0,10,0,9,0,0,0,8,4,3,3,3,3,3,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(readableChannel);
        Assertions.assertThat(FrameType.valueOf(frame1.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame1.getFlags()).isEqualTo(8);
        Assertions.assertThat(frame1.getStreamId()).isEqualTo(8);
        final ByteBuffer payload1 = frame1.getPayloadContent();
        Assertions.assertThat(payload1).isNotNull();
        Assertions.assertThat(payload1.remaining()).isEqualTo(5);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload1.get()).isEqualTo((byte) 1);

        final RawFrame frame2 = inBuffer.read(readableChannel);
        Assertions.assertThat(FrameType.valueOf(frame2.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame2.getFlags()).isEqualTo(0);
        Assertions.assertThat(frame2.getStreamId()).isEqualTo(8);
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assertions.assertThat(payload2).isNotNull();
        Assertions.assertThat(payload2.remaining()).isEqualTo(5);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload2.get()).isEqualTo((byte) 2);

        final RawFrame frame3 = inBuffer.read(readableChannel);
        Assertions.assertThat(FrameType.valueOf(frame3.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame3.getFlags()).isEqualTo(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED));
        Assertions.assertThat(frame3.getStreamId()).isEqualTo(8);
        final ByteBuffer payload3 = frame3.getPayloadContent();
        Assertions.assertThat(payload3).isNotNull();
        Assertions.assertThat(payload3.remaining()).isEqualTo(5);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload3.get()).isEqualTo((byte) 3);

        Assertions.assertThat(readableChannel.read(ByteBuffer.allocate(1024))).isEqualTo(-1);
    }

    @Test
    void testReadFramePartialReads() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {0,0},
                new byte[] {10,0,9,0},
                new byte[] {0,0,8},
                new byte[] {4},
                new byte[] {1,2,3,4},
                new byte[] {5,0},
                new byte[] {0,0,0});

        final RawFrame frame = inBuffer.read(readableChannel);
        Assertions.assertThat(FrameType.valueOf(frame.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame.getFlags()).isEqualTo(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED));
        Assertions.assertThat(frame.getStreamId()).isEqualTo(8);
        final ByteBuffer payload = frame.getPayloadContent();
        Assertions.assertThat(payload).isNotNull();
        Assertions.assertThat(payload.remaining()).isEqualTo(5);
        Assertions.assertThat(payload.get()).isEqualTo((byte) 1);
        Assertions.assertThat(payload.get()).isEqualTo((byte) 2);
        Assertions.assertThat(payload.get()).isEqualTo((byte) 3);
        Assertions.assertThat(payload.get()).isEqualTo((byte) 4);
        Assertions.assertThat(payload.get()).isEqualTo((byte) 5);

        Assertions.assertThat(readableChannel.read(ByteBuffer.allocate(1024))).isEqualTo(-1);
    }

    @Test
    void testReadEmptyFrame() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {0,0,0,0,0,0,0,0,0});

        final RawFrame frame = inBuffer.read(readableChannel);
        Assertions.assertThat(FrameType.valueOf(frame.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame.getFlags()).isEqualTo(0);
        Assertions.assertThat(frame.getStreamId()).isEqualTo(0);
        final ByteBuffer payload = frame.getPayloadContent();
        Assertions.assertThat(payload).isNull();
    }

    @Test
    void testReadFrameConnectionClosed() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(new byte[] {});

        Assertions.assertThat(inBuffer.read(readableChannel)).isNull();
        Assertions.assertThat(inBuffer.isEndOfStream()).isFalse();
        Assertions.assertThat(inBuffer.read(readableChannel)).isNull();
        Assertions.assertThat(inBuffer.isEndOfStream()).isTrue();
        Assertions.assertThat(inBuffer.read(readableChannel)).isNull();
        Assertions.assertThat(inBuffer.isEndOfStream()).isTrue();
    }

    @Test
    void testReadFrameCorruptFrame() {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(new byte[] {0,0});

        Assertions.assertThatThrownBy(() ->
                inBuffer.read(readableChannel)).isInstanceOf(H2CorruptFrameException.class);
    }

    @Test
    void testWriteFrameExceedingLimit() {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[2048]));
        Assertions.assertThatThrownBy(() ->
                outbuffer.write(frame, writableChannel)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReadFrameExceedingLimit() {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {0,-128,-128,0,0,0,0,0,1});

        Assertions.assertThatThrownBy(() ->
                inBuffer.read(readableChannel)).isInstanceOf(H2ConnectionException.class);
    }

    @Test
    void testOutputBufferResize() {
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);
        Assertions.assertThat(outBuffer.getMaxFramePayloadSize()).isEqualTo(16 * 1024);
        outBuffer.resize(1024);
        Assertions.assertThat(outBuffer.getMaxFramePayloadSize()).isEqualTo(1024);
    }

}

