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
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class TestFrameInOutBuffers {

    @Test
    void testReadWriteFrame() throws Exception {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(16 * 1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[]{1,2,3,4,5}));
        outbuffer.write(frame, outputStream);

        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final byte[] bytes = outputStream.toByteArray();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        Assertions.assertThat(bytes.length).isEqualTo(FrameConsts.HEAD_LEN + 5);

        Assertions.assertThat(outbuffer.getMetrics().getFramesTransferred()).isEqualTo(1);
        Assertions.assertThat(outbuffer.getMetrics().getBytesTransferred()).isEqualTo(bytes.length);

        final RawFrame frame2 = inBuffer.read(inputStream);
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
        Assertions.assertThat(inputStream.read()).isEqualTo(-1);

        Assertions.assertThat(inBuffer.getMetrics().getFramesTransferred()).isEqualTo(1);
        Assertions.assertThat(inBuffer.getMetrics().getBytesTransferred()).isEqualTo(bytes.length);
    }

    @Test
    void testReadFrameMultiple() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,0,1,2,3,4,0,0,0,0,
                        0,0,10,0,9,0,0,0,8,4,5,6,7,8,9,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(inputStream);
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

        final RawFrame frame2 = inBuffer.read(inputStream);
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

        Assertions.assertThat(inputStream.read()).isEqualTo(-1);
    }

    @Test
    void testReadFrameMultipleSmallBuffer() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(new BasicH2TransportMetrics(), 20, 10);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {
                        0,0,10,0,8,0,0,0,8,4,1,1,1,1,1,0,0,0,0,
                        0,0,5,0,0,0,0,0,8,2,2,2,2,2,
                        0,0,10,0,9,0,0,0,8,4,3,3,3,3,3,0,0,0,0
                });

        final RawFrame frame1 = inBuffer.read(inputStream);
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

        final RawFrame frame2 = inBuffer.read(inputStream);
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

        final RawFrame frame3 = inBuffer.read(inputStream);
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

        Assertions.assertThat(inputStream.read()).isEqualTo(-1);
    }

    @Test
    void testReadFramePartialReads() throws Exception {
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
        Assertions.assertThat(inputStream.read()).isEqualTo(-1);
    }

    @Test
    void testReadEmptyFrame() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {0,0,0,0,0,0,0,0,0});

        final RawFrame frame = inBuffer.read(inputStream);
        Assertions.assertThat(FrameType.valueOf(frame.getType())).isEqualTo(FrameType.DATA);
        Assertions.assertThat(frame.getFlags()).isEqualTo(0);
        Assertions.assertThat(frame.getStreamId()).isEqualTo(0);
        final ByteBuffer payload = frame.getPayloadContent();
        Assertions.assertThat(payload).isNull();
    }

    @Test
    void testReadFrameConnectionClosed() {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {});

        Assertions.assertThatThrownBy(() ->
                        inBuffer.read(inputStream))
                .isInstanceOf(ConnectionClosedException.class);
    }

    @Test
    void testReadFrameCorruptFrame() {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {0,0});

        Assertions.assertThatThrownBy(() ->
                        inBuffer.read(inputStream))
                .isInstanceOf(H2CorruptFrameException.class);
    }

    @Test
    void testWriteFrameExceedingLimit() {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final FrameOutputBuffer outbuffer = new FrameOutputBuffer(1024);

        final RawFrame frame = new RawFrame(FrameType.DATA.getValue(), 0, 1,
                ByteBuffer.wrap(new byte[2048]));
        Assertions.assertThatThrownBy(() ->
                        outbuffer.write(frame, outputStream))
                .isInstanceOf(H2ConnectionException.class);
    }

    @Test
    void testReadFrameExceedingLimit() {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {0,-128,-128,0,0,0,0,0,1});

        Assertions.assertThatThrownBy(() ->
                        inBuffer.read(inputStream))
                .isInstanceOf(H2ConnectionException.class);
    }

    @Test
    void testPaddedBitOnUnknownFrameTypeIgnored() throws Exception {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {
                        0, 0, 1, 10, 8, 0, 0, 0, 1, 42 // len=1,type=0x0a(flags=0x08),stream=1,payload=42
                });

        final RawFrame frame = inBuffer.read(inputStream);
        Assertions.assertThat(frame).isNotNull();
        Assertions.assertThat(frame.getType()).isEqualTo(0x0a);
        Assertions.assertThat(frame.getFlags()).isEqualTo(0x08);
        Assertions.assertThat(frame.getStreamId()).isEqualTo(1);
        Assertions.assertThat(frame.getLength()).isEqualTo(1);
        Assertions.assertThat(frame.getPayload()).isNotNull();
        Assertions.assertThat(frame.getPayload().remaining()).isEqualTo(1);
        Assertions.assertThat(frame.getPayload().get() & 0xff).isEqualTo(42);
    }

    @Test
    void testPaddedValidationAppliedForDataFrame() {
        final FrameInputBuffer inBuffer = new FrameInputBuffer(16 * 1024);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                new byte[] {
                        0, 0, 1, 0, 8, 0, 0, 0, 1, 42 // len=1,type=DATA(flags=0x08),stream=1,padLength=42 -> invalid
                });

        final H2ConnectionException ex = Assertions.assertThatThrownBy(() ->
                        inBuffer.read(inputStream))
                .isInstanceOf(H2ConnectionException.class).asInstanceOf(InstanceOfAssertFactories.type(H2ConnectionException.class)).actual();
        Assertions.assertThat(ex.getCode()).isEqualTo(H2Error.PROTOCOL_ERROR.getCode());
    }

}

