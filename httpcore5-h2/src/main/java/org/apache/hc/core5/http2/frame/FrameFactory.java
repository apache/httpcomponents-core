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
import org.apache.hc.core5.http2.config.H2Setting;
import org.apache.hc.core5.util.Args;

/**
 * Abstract {@link RawFrame} factory that supports standard
 * HTTP/2 {@link FrameType}s.
 *
 * @since 5.0
 */
public abstract class FrameFactory {

    public RawFrame createSettings(final H2Setting... settings) {
        final ByteBuffer payload = ByteBuffer.allocate(settings.length * 12);
        for (final H2Setting setting: settings) {
            payload.putShort((short) setting.getCode());
            payload.putInt(setting.getValue());
        }
        payload.flip();
        return new RawFrame(FrameType.SETTINGS.getValue(), 0, 0, payload);
    }

    public RawFrame createSettingsAck() {
        return new RawFrame(FrameType.SETTINGS.getValue(), FrameFlag.ACK.getValue(), 0, null);
    }

    public RawFrame createResetStream(final int streamId, final H2Error error) {
        Args.notNull(error, "Error");
        return createResetStream(streamId, error.getCode());
    }

    public RawFrame createResetStream(final int streamId, final int code) {
        Args.positive(streamId, "Stream id");
        final ByteBuffer payload = ByteBuffer.allocate(4);
        payload.putInt(code);
        payload.flip();
        return new RawFrame(FrameType.RST_STREAM.getValue(), 0, streamId, payload);
    }

    public RawFrame createPing(final ByteBuffer opaqueData) {
        Args.notNull(opaqueData, "Opaque data");
        Args.check(opaqueData.remaining() == 8, "Opaque data length must be equal 8");
        return new RawFrame(FrameType.PING.getValue(), 0, 0, opaqueData);
    }

    public RawFrame createPingAck(final ByteBuffer opaqueData) {
        Args.notNull(opaqueData, "Opaque data");
        Args.check(opaqueData.remaining() == 8, "Opaque data length must be equal 8");
        return new RawFrame(FrameType.PING.getValue(), FrameFlag.ACK.value, 0, opaqueData);
    }

    public RawFrame createGoAway(final int lastStream, final H2Error error, final String message) {
        Args.notNegative(lastStream, "Last stream id");
        final byte[] debugData = message != null ? message.getBytes(StandardCharsets.US_ASCII) : null;
        final ByteBuffer payload = ByteBuffer.allocate(8 + (debugData != null ? debugData.length : 0));
        payload.putInt(lastStream);
        payload.putInt(error.getCode());
        if (debugData != null) {
            payload.put(debugData);
        }
        payload.flip();
        return new RawFrame(FrameType.GOAWAY.getValue(), 0, 0, payload);
    }

    public abstract RawFrame createHeaders(int streamId, ByteBuffer payload, boolean endHeaders, boolean endStream);

    public abstract RawFrame createContinuation(int streamId, ByteBuffer payload, boolean endHeaders);

    public abstract RawFrame createPushPromise(int streamId, ByteBuffer payload, boolean endHeaders);

    public abstract RawFrame createData(int streamId, ByteBuffer payload, boolean endStream);

    public RawFrame createWindowUpdate(final int streamId, final int increment) {
        Args.notNegative(streamId, "Stream id");
        Args.positive(increment, "Increment");
        final ByteBuffer payload = ByteBuffer.allocate(4);
        payload.putInt(increment);
        payload.flip();
        return new RawFrame(FrameType.WINDOW_UPDATE.getValue(), 0, streamId, payload);
    }

}
