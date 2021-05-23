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

package org.apache.hc.core5.http2.impl.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2CorruptFrameException;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.junit.Assert;
import org.junit.Test;

public class TestFrameInOutBuffers {

    @Test
    public void testReadWriteFrame() throws Exception {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[]{1,2,3,4,5}));
        outbuffer.write(frame, outputStream);

        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final byte[] bytes = outputStream.toByteArray();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        Assert.assertEquals(FrameConsts.HEAD_LEN + 5, bytes.length);

        Assert.assertEquals(1, outbuffer.getMetrics().getFramesTransferred());
        Assert.assertEquals(bytes.length, outbuffer.getMetrics().getBytesTransferred());

        final RawFrame frame2 = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA.getValue(), frame2.getType());
        Assert.assertEquals(0, frame2.getFlags());
        Assert.assertEquals(1L, frame2.getStreamId());
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assert.assertNotNull(payload2);
        Assert.assertEquals(5, payload2.remaining());
        Assert.assertEquals(1, payload2.get());
        Assert.assertEquals(2, payload2.get());
        Assert.assertEquals(3, payload2.get());
        Assert.assertEquals(4, payload2.get());
        Assert.assertEquals(5, payload2.get());
        Assert.assertEquals(-1, inputStream.read());

        Assert.assertEquals(1, inBuffer.getMetrics().getFramesTransferred());
        Assert.assertEquals(bytes.length, inBuffer.getMetrics().getBytesTransferred());
    }

    @Test
    public void testReadFrameMultiple() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,0,1,2,3,4,0,0,0,0,
                        0,0,10,0,9,0,0,0,8,4,5,6,7,8,9,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA, FrameType.valueOf(frame1.getType()));
        Assert.assertEquals(8, frame1.getFlags());
        Assert.assertEquals(8, frame1.getStreamId());
        final ByteBuffer payload1 = frame1.getPayloadContent();
        Assert.assertNotNull(payload1);
        Assert.assertEquals(5, payload1.remaining());
        Assert.assertEquals(0, payload1.get());
        Assert.assertEquals(1, payload1.get());
        Assert.assertEquals(2, payload1.get());
        Assert.assertEquals(3, payload1.get());
        Assert.assertEquals(4, payload1.get());

        final RawFrame frame2 = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA, FrameType.valueOf(frame2.getType()));
        Assert.assertEquals(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED), frame2.getFlags());
        Assert.assertEquals(8, frame2.getStreamId());
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assert.assertNotNull(payload2);
        Assert.assertEquals(5, payload2.remaining());
        Assert.assertEquals(5, payload2.get());
        Assert.assertEquals(6, payload2.get());
        Assert.assertEquals(7, payload2.get());
        Assert.assertEquals(8, payload2.get());
        Assert.assertEquals(9, payload2.get());

        Assert.assertEquals(-1, inputStream.read());
    }

    @Test
    public void testReadFrameMultipleSmallBuffer() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(new BasicH2TransportMetrics(), 20, 10);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,1,1,1,1,1,0,0,0,0,
                        0,0,5,0,0,0,0,0,8,2,2,2,2,2,
                        0,0,10,0,9,0,0,0,8,4,3,3,3,3,3,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA, FrameType.valueOf(frame1.getType()));
        Assert.assertEquals(8, frame1.getFlags());
        Assert.assertEquals(8, frame1.getStreamId());
        final ByteBuffer payload1 = frame1.getPayloadContent();
        Assert.assertNotNull(payload1);
        Assert.assertEquals(5, payload1.remaining());
        Assert.assertEquals(1, payload1.get());
        Assert.assertEquals(1, payload1.get());
        Assert.assertEquals(1, payload1.get());
        Assert.assertEquals(1, payload1.get());
        Assert.assertEquals(1, payload1.get());

        final RawFrame frame2 = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA, FrameType.valueOf(frame2.getType()));
        Assert.assertEquals(0, frame2.getFlags());
        Assert.assertEquals(8, frame2.getStreamId());
        final ByteBuffer payload2 = frame2.getPayloadContent();
        Assert.assertNotNull(payload2);
        Assert.assertEquals(5, payload2.remaining());
        Assert.assertEquals(2, payload2.get());
        Assert.assertEquals(2, payload2.get());
        Assert.assertEquals(2, payload2.get());
        Assert.assertEquals(2, payload2.get());
        Assert.assertEquals(2, payload2.get());

        final RawFrame frame3 = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA, FrameType.valueOf(frame3.getType()));
        Assert.assertEquals(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED), frame3.getFlags());
        Assert.assertEquals(8, frame3.getStreamId());
        final ByteBuffer payload3 = frame3.getPayloadContent();
        Assert.assertNotNull(payload3);
        Assert.assertEquals(5, payload3.remaining());
        Assert.assertEquals(3, payload3.get());
        Assert.assertEquals(3, payload3.get());
        Assert.assertEquals(3, payload3.get());
        Assert.assertEquals(3, payload3.get());
        Assert.assertEquals(3, payload3.get());

        Assert.assertEquals(-1, inputStream.read());
    }

    @Test
    public void testReadFramePartialReads() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final MultiByteArrayInputStream inputStream = new MultiByteArrayInputStream(
                new byte[] {0,0},
                new byte[] {10,0,9,0},
                new byte[] {0,0,8},
                new byte[] {4},
                new byte[] {1,2,3,4},
                new byte[] {5,0},
                new byte[] {0,0,0});

        final RawFrame frame = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA, FrameType.valueOf(frame.getType()));
        Assert.assertEquals(FrameFlag.of(FrameFlag.END_STREAM, FrameFlag.PADDED), frame.getFlags());
        Assert.assertEquals(8, frame.getStreamId());
        final ByteBuffer payload = frame.getPayloadContent();
        Assert.assertNotNull(payload);
        Assert.assertEquals(5, payload.remaining());
        Assert.assertEquals(1, payload.get());
        Assert.assertEquals(2, payload.get());
        Assert.assertEquals(3, payload.get());
        Assert.assertEquals(4, payload.get());
        Assert.assertEquals(5, payload.get());
        Assert.assertEquals(-1, inputStream.read());
    }

    @Test
    public void testReadEmptyFrame() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {0,0,0,0,0,0,0,0,0});

        final RawFrame frame = inBuffer.read(inputStream);
        Assert.assertEquals(FrameType.DATA, FrameType.valueOf(frame.getType()));
        Assert.assertEquals(0, frame.getFlags());
        Assert.assertEquals(0, frame.getStreamId());
        final ByteBuffer payload = frame.getPayloadContent();
        Assert.assertNull(payload);
    }

    @Test
    public void testReadFrameConnectionClosed() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {});

        Assert.assertThrows(ConnectionClosedException.class, () -> inBuffer.read(inputStream));
    }

    @Test
    public void testReadFrameCorruptFrame() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {0,0});

        Assert.assertThrows(H2CorruptFrameException.class, () -> inBuffer.read(inputStream));
    }

    @Test
    public void testWriteFrameExceedingLimit() throws Exception {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[2048]));
        Assert.assertThrows(H2ConnectionException.class, () -> outbuffer.write(frame, outputStream));
    }

    @Test
    public void testReadFrameExceedingLimit() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {0,-128,-128,0,0,0,0,0,1});

        Assert.assertThrows(H2ConnectionException.class, () -> inBuffer.read(inputStream));
    }

}

