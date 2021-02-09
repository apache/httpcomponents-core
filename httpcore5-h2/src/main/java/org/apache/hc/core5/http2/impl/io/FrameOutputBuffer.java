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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2TransportMetrics;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.apache.hc.core5.util.Args;

/**
 * Frame output buffer for HTTP/2 blocking connections.
 *
 * @since 5.0
 */
public final class FrameOutputBuffer {

    private final BasicH2TransportMetrics metrics;
    private final int maxFramePayloadSize;
    private final byte[] buffer;

    public FrameOutputBuffer(final BasicH2TransportMetrics metrics, final int maxFramePayloadSize) {
        super();
        Args.notNull(metrics, "HTTP2 transport metrics");
        Args.positive(maxFramePayloadSize, "Maximum payload size");
        this.metrics = metrics;
        this.maxFramePayloadSize = maxFramePayloadSize;
        this.buffer = new byte[FrameConsts.HEAD_LEN + maxFramePayloadSize + FrameConsts.MAX_PADDING + 1];
    }

    public FrameOutputBuffer(final int maxFramePayloadSize) {
        this(new BasicH2TransportMetrics(), maxFramePayloadSize);
    }

    public void write(final RawFrame frame, final OutputStream outStream) throws IOException {
        if (frame == null) {
            return;
        }
        final int type = frame.getType();
        final long streamId = frame.getStreamId();
        final int flags = frame.getFlags();
        final ByteBuffer payload = frame.getPayload();
        final int payloadLen = payload != null ? payload.remaining() : 0;
        if (payload != null && payload.remaining() > maxFramePayloadSize) {
            throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Frame size exceeds maximum");
        }
        buffer[0] = (byte) (payloadLen >> 16 & 0xff);
        buffer[1] = (byte) (payloadLen >> 8 & 0xff);
        buffer[2] = (byte) (payloadLen & 0xff);

        buffer[3] = (byte) (type & 0xff);
        buffer[4] = (byte) (flags & 0xff);

        buffer[5] = (byte) (streamId >> 24 & 0xff);
        buffer[6] = (byte) (streamId >> 16 & 0xff);
        buffer[7] = (byte) (streamId >> 8 & 0xff);
        buffer[8] = (byte) (streamId & 0xff);

        int frameLen = FrameConsts.HEAD_LEN;
        int padding = 0;
        if ((flags & FrameFlag.PADDED.getValue()) > 0) {
            padding = 16;
            buffer[9] = (byte) (padding & 0xff);
            frameLen++;
        }
        if (payload != null) {
            payload.get(buffer, frameLen, payload.remaining());
            frameLen += payloadLen;
        }
        for (int i = 0; i < padding; i++) {
            buffer[frameLen++] = 0;
        }
        outStream.write(buffer, 0, frameLen);

        metrics.incrementFramesTransferred();
        metrics.incrementBytesTransferred(frameLen);
    }

    public H2TransportMetrics getMetrics() {
        return metrics;
    }

}
