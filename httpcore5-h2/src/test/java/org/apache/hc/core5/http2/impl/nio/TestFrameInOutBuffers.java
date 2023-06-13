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

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2CorruptFrameException;
import org.apache.hc.core5.http2.ReadableByteChannelMock;
import org.apache.hc.core5.http2.WritableByteChannelMock;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestFrameInOutBuffers {

    @Test
    public void testReadWriteFrame() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[]{1,2,3,4,5}));
        outbuffer.write(frame, writableChannel);

        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final byte[] bytes = writableChannel.toByteArray();
        Assertions.assertEquals(FrameConsts.HEAD_LEN + 5, bytes.length);

        Assertions.assertEquals(1, outbuffer.getMetrics().getFramesTransferred());
        Assertions.assertEquals(bytes.length, outbuffer.getMetrics().getBytesTransferred());

        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(bytes);
        final RawFrame frame2 = inBuffer.read(readableChannel);
        Assertions.assertEquals(FrameType.DATA.getValue(), frame2.getType());
        Assertions.assertEquals(0, frame2.getFlags());
        Assertions.assertEquals(1L, frame2.getStreamId());
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assertions.assertNotNull(payload2);
        Assertions.assertEquals(5, payload2.remaining());
        Assertions.assertEquals(1, payload2.get());
        Assertions.assertEquals(2, payload2.get());
        Assertions.assertEquals(3, payload2.get());
        Assertions.assertEquals(4, payload2.get());
        Assertions.assertEquals(5, payload2.get());
        Assertions.assertEquals(-1, readableChannel.read(ByteBuffer.allocate(1024)));

        Assertions.assertEquals(1, inBuffer.getMetrics().getFramesTransferred());
        Assertions.assertEquals(bytes.length, inBuffer.getMetrics().getBytesTransferred());

        final RawFrame frame3 = inBuffer.read(ByteBuffer.wrap(bytes), readableChannel);
        Assertions.assertEquals(FrameType.DATA.getValue(), frame3.getType());
        Assertions.assertEquals(0, frame3.getFlags());
        Assertions.assertEquals(1L, frame3.getStreamId());
        final ByteBuffer payload3 = frame3.getPayloadContent();
        Assertions.assertNotNull(payload3);
        Assertions.assertEquals(5, payload3.remaining());
        Assertions.assertEquals(1, payload3.get());
        Assertions.assertEquals(2, payload3.get());
        Assertions.assertEquals(3, payload3.get());
        Assertions.assertEquals(4, payload3.get());
        Assertions.assertEquals(5, payload3.get());

        Assertions.assertEquals(2, inBuffer.getMetrics().getFramesTransferred());
        Assertions.assertEquals(bytes.length * 2, inBuffer.getMetrics().getBytesTransferred());
    }

    @Test
    public void testPartialFrameWrite() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024, FrameConsts.HEAD_LEN + 10);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);
        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), FrameFlag.END_STREAM.getValue(), 5,
                ByteBuffer.wrap(new byte[]{'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'}));

        outbuffer.write(frame, writableChannel);
        Assertions.assertArrayEquals(new byte[] {0,0,16,0,1,0,0,0,5,48,49,50,51,52,53,54,55,56,57},
                writableChannel.toByteArray());

        Assertions.assertFalse(outbuffer.isEmpty());
        outbuffer.flush(writableChannel);
        Assertions.assertArrayEquals(new byte[] {0,0,16,0,1,0,0,0,5,48,49,50,51,52,53,54,55,56,57},
                writableChannel.toByteArray());

        writableChannel.flush();
        outbuffer.flush(writableChannel);
        Assertions.assertArrayEquals(new byte[] {0,0,16,0,1,0,0,0,5,48,49,50,51,52,53,54,55,56,57,97,98,99,100,101,102},
                writableChannel.toByteArray());
    }

    @Test
    public void testReadFrameMultiple() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,0,1,2,3,4,0,0,0,0,
                        0,0,10,0,9,0,0,0,8,4,5,6,7,8,9,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(readableChannel);
        Assertions.assertEquals(FrameType.DATA, FrameType.valueOf(frame1.getType()));
        Assertions.assertEquals(8, frame1.getFlags());
        Assertions.assertEquals(8, frame1.getStreamId());
        final ByteBuffer payload1 = frame1.getPayloadContent();
        Assertions.assertNotNull(payload1);
        Assertions.assertEquals(5, payload1.remaining());
        Assertions.assertEquals(0, payload1.get());
        Assertions.assertEquals(1, payload1.get());
        Assertions.assertEquals(2, payload1.get());
        Assertions.assertEquals(3, payload1.get());
        Assertions.assertEquals(4, payload1.get());

        final RawFrame frame2 = inBuffer.read(readableChannel);
        Assertions.assertEquals(FrameType.DATA, FrameType.valueOf(frame2.getType()));
        Assertions.assertEquals(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED), frame2.getFlags());
        Assertions.assertEquals(8, frame2.getStreamId());
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assertions.assertNotNull(payload2);
        Assertions.assertEquals(5, payload2.remaining());
        Assertions.assertEquals(5, payload2.get());
        Assertions.assertEquals(6, payload2.get());
        Assertions.assertEquals(7, payload2.get());
        Assertions.assertEquals(8, payload2.get());
        Assertions.assertEquals(9, payload2.get());

        Assertions.assertEquals(-1, readableChannel.read(ByteBuffer.allocate(1024)));
    }

    @Test
    public void testReadFrameMultipleSmallBuffer() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(new BasicH2TransportMetrics(), 20, 10);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,1,1,1,1,1,0,0,0,0,
                        0,0,5,0,0,0,0,0,8,2,2,2,2,2,
                        0,0,10,0,9,0,0,0,8,4,3,3,3,3,3,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(readableChannel);
        Assertions.assertEquals(FrameType.DATA, FrameType.valueOf(frame1.getType()));
        Assertions.assertEquals(8, frame1.getFlags());
        Assertions.assertEquals(8, frame1.getStreamId());
        final ByteBuffer payload1 = frame1.getPayloadContent();
        Assertions.assertNotNull(payload1);
        Assertions.assertEquals(5, payload1.remaining());
        Assertions.assertEquals(1, payload1.get());
        Assertions.assertEquals(1, payload1.get());
        Assertions.assertEquals(1, payload1.get());
        Assertions.assertEquals(1, payload1.get());
        Assertions.assertEquals(1, payload1.get());

        final RawFrame frame2 = inBuffer.read(readableChannel);
        Assertions.assertEquals(FrameType.DATA, FrameType.valueOf(frame2.getType()));
        Assertions.assertEquals(0, frame2.getFlags());
        Assertions.assertEquals(8, frame2.getStreamId());
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assertions.assertNotNull(payload2);
        Assertions.assertEquals(5, payload2.remaining());
        Assertions.assertEquals(2, payload2.get());
        Assertions.assertEquals(2, payload2.get());
        Assertions.assertEquals(2, payload2.get());
        Assertions.assertEquals(2, payload2.get());
        Assertions.assertEquals(2, payload2.get());

        final RawFrame frame3 = inBuffer.read(readableChannel);
        Assertions.assertEquals(FrameType.DATA, FrameType.valueOf(frame3.getType()));
        Assertions.assertEquals(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED), frame3.getFlags());
        Assertions.assertEquals(8, frame3.getStreamId());
        final ByteBuffer payload3 = frame3.getPayloadContent();
        Assertions.assertNotNull(payload3);
        Assertions.assertEquals(5, payload3.remaining());
        Assertions.assertEquals(3, payload3.get());
        Assertions.assertEquals(3, payload3.get());
        Assertions.assertEquals(3, payload3.get());
        Assertions.assertEquals(3, payload3.get());
        Assertions.assertEquals(3, payload3.get());

        Assertions.assertEquals(-1, readableChannel.read(ByteBuffer.allocate(1024)));
    }

    @Test
    public void testReadFramePartialReads() throws Exception {
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
        Assertions.assertEquals(FrameType.DATA, FrameType.valueOf(frame.getType()));
        Assertions.assertEquals(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED), frame.getFlags());
        Assertions.assertEquals(8, frame.getStreamId());
        final ByteBuffer payload = frame.getPayloadContent();
        Assertions.assertNotNull(payload);
        Assertions.assertEquals(5, payload.remaining());
        Assertions.assertEquals(1, payload.get());
        Assertions.assertEquals(2, payload.get());
        Assertions.assertEquals(3, payload.get());
        Assertions.assertEquals(4, payload.get());
        Assertions.assertEquals(5, payload.get());

        Assertions.assertEquals(-1, readableChannel.read(ByteBuffer.allocate(1024)));
    }

    @Test
    public void testReadEmptyFrame() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {0,0,0,0,0,0,0,0,0});

        final RawFrame frame = inBuffer.read(readableChannel);
        Assertions.assertEquals(FrameType.DATA, FrameType.valueOf(frame.getType()));
        Assertions.assertEquals(0, frame.getFlags());
        Assertions.assertEquals(0, frame.getStreamId());
        final ByteBuffer payload = frame.getPayloadContent();
        Assertions.assertNull(payload);
    }

    @Test
    public void testReadFrameConnectionClosed() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(new byte[] {});

        Assertions.assertNull(inBuffer.read(readableChannel));
        Assertions.assertThrows(ConnectionClosedException.class, () ->
                inBuffer.read(readableChannel));
    }

    @Test
    public void testReadFrameCorruptFrame() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(new byte[] {0,0});

        Assertions.assertThrows(H2CorruptFrameException.class, () ->
                inBuffer.read(readableChannel));
    }

    @Test
    public void testWriteFrameExceedingLimit() throws Exception {
        final WritableByteChannelMock writableChannel = new WritableByteChannelMock(1024);
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[2048]));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                outbuffer.write(frame, writableChannel));
    }

    @Test
    public void testReadFrameExceedingLimit() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannelMock readableChannel = new ReadableByteChannelMock(
                new byte[] {0,-128,-128,0,0,0,0,0,1});

        Assertions.assertThrows(H2ConnectionException.class, () ->
                inBuffer.read(readableChannel));
    }

    @Test
    public void testOutputBufferResize() throws Exception {
        final FrameOutputBuffer outBuffer = new FrameOutputBuffer(16 * 1024);
        Assertions.assertEquals(16 * 1024, outBuffer.getMaxFramePayloadSize());
        outBuffer.resize(1024);
        Assertions.assertEquals(1024, outBuffer.getMaxFramePayloadSize());
    }

}

