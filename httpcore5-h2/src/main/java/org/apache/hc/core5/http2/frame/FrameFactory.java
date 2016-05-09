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

package org.apache.hc.core5.http2.frame;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.util.Args;

public abstract class FrameFactory {

    // PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
    private final static byte[] PREFACE = new byte[] {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50,
            0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d,
            0x0d, 0x0a, 0x0d, 0x0a};

    public byte[] createConnectionPreface() {
        return PREFACE;
    }

    public Frame<ByteBuffer> createSettings(final H2Setting... settings) {
        final ByteBuffer payload = ByteBuffer.allocate(settings.length * 12);
        for (H2Setting setting: settings) {
            payload.putShort((short) setting.getCode());
            payload.putInt(setting.getValue());
        }
        payload.flip();
        return new ByteBufferFrame(FrameType.SETTINGS.getValue(), 0, 0, payload);
    }

    public Frame<ByteBuffer> createSettingsAck() {
        return new ByteBufferFrame(FrameType.SETTINGS.getValue(), FrameFlag.ACK.getValue(), 0, null);
    }

    public Frame<ByteBuffer> createResetStream(final int streamId, final H2Error error) {
        Args.positive(streamId, "Stream id");
        Args.notNull(error, "Error");
        final ByteBuffer payload = ByteBuffer.allocate(4);
        payload.putInt(error.getCode());
        payload.flip();
        return new ByteBufferFrame(FrameType.RST_STREAM.getValue(), 0, streamId, payload);
    }

    public Frame<ByteBuffer> createPing(final ByteBuffer opaqueData) {
        Args.notNull(opaqueData, "Opaque data");
        Args.check(opaqueData.remaining() == 8, "Opaque data length must be equal 8");
        return new ByteBufferFrame(FrameType.PING.getValue(), 0, 0, opaqueData);
    }

    public Frame<ByteBuffer> createPingAck(final ByteBuffer opaqueData) {
        Args.notNull(opaqueData, "Opaque data");
        Args.check(opaqueData.remaining() == 8, "Opaque data length must be equal 8");
        return new ByteBufferFrame(FrameType.PING.getValue(), FrameFlag.ACK.value, 0, opaqueData);
    }

    public Frame<ByteBuffer> createGoAway(final int lastStream, final H2Error error, final String message) {
        Args.positive(lastStream, "Last stream id");
        final byte[] debugData = message != null ? message.getBytes(StandardCharsets.US_ASCII) : null;
        final ByteBuffer payload = ByteBuffer.allocate(8 + (debugData != null ? debugData.length : 0));
        payload.putInt(lastStream);
        payload.putInt(error.getCode());
        payload.put(debugData);
        payload.flip();
        return new ByteBufferFrame(FrameType.GOAWAY.getValue(), 0, 0, payload);
    }

    public abstract Frame<ByteBuffer> createHeaders(int streamId, ByteBuffer payload, boolean endHeaders, boolean endStream);

    public abstract Frame<ByteBuffer> createContinuation(int streamId, ByteBuffer payload, boolean endHeaders);

    public abstract Frame<ByteBuffer> createData(int streamId, ByteBuffer payload, boolean endStream);

}
