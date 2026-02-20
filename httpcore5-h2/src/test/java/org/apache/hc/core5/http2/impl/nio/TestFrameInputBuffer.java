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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.junit.jupiter.api.Test;

class TestFrameInputBuffer {

    @Test
    void payloadLengthWithHighBitMustTriggerFrameSizeError() throws Exception {
        // length = 0x800000 (top bit set in the 24-bit length)
        // type = 0x00 (DATA), flags = 0x00, streamId = 0
        //
        // With the OLD code:
        //   payloadLen = (lengthAndType >> 8) becomes NEGATIVE -> bypasses "payloadLen > max"
        //   then PAYLOAD_EXPECTED sees remaining >= negative -> attempts wrap with negative length -> runtime failure
        //
        // With the FIX:
        //   payloadLen = (lengthAndType >>> 8) & 0x00ffffff == 0x800000 -> throws FRAME_SIZE_ERROR

        final byte[] frame = new byte[]{
                (byte) 0x80, 0x00, 0x00, 0x00, // 24-bit length (0x800000) + type (0x00)
                0x00,                          // flags
                0x00, 0x00, 0x00, 0x00         // streamId
                // no payload
        };

        final FrameInputBuffer inBuf = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannel ch = Channels.newChannel(new ByteArrayInputStream(frame));

        final H2ConnectionException ex = assertThrows(H2ConnectionException.class, () -> inBuf.read(ch));
        assertEquals(H2Error.FRAME_SIZE_ERROR.getCode(), ex.getCode());
    }

    @Test
    void flagsMustBeTreatedAsUnsignedByte() throws Exception {
        // flags on the wire are 1 byte (0..255). If you read into a signed byte and store as int,
        // values >= 0x80 become negative without & 0xff.
        final byte[] frame = new byte[]{
                0x00, 0x00, 0x00, 0x00, // length=0 + type=0
                (byte) 0x80,            // flags = 0x80
                0x00, 0x00, 0x00, 0x00  // streamId = 0
        };

        final FrameInputBuffer inBuf = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannel ch = Channels.newChannel(new ByteArrayInputStream(frame));

        final RawFrame rawFrame = inBuf.read(ch);
        assertNotNull(rawFrame);
        assertEquals(0x80, rawFrame.getFlags());
    }

    @Test
    void streamIdReservedBitMustBeIgnored() throws Exception {
        // On the wire stream-id is 32 bits; top bit is reserved and MUST be ignored.
        // streamId = 0x80000001 should be treated as 0x00000001.
        //
        // OLD code: Math.abs(0x80000001 as int) = 2147483647 (wrong)
        // FIX: streamId = getInt() & 0x7fffffff = 1

        final byte[] frame = new byte[]{
                0x00, 0x00, 0x00, 0x00, // length=0 + type=0
                0x00,                   // flags
                (byte) 0x80, 0x00, 0x00, 0x01 // streamId = 0x80000001
        };

        final FrameInputBuffer inBuf = new FrameInputBuffer(16 * 1024);
        final ReadableByteChannel ch = Channels.newChannel(new ByteArrayInputStream(frame));

        final RawFrame rawFrame = inBuf.read(ch);
        assertNotNull(rawFrame);
        assertEquals(1, rawFrame.getStreamId());
    }
}
