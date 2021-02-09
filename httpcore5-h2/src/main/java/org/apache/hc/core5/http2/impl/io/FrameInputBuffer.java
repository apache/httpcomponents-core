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
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2CorruptFrameException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2TransportMetrics;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.apache.hc.core5.util.Args;

/**
 * Frame input buffer for HTTP/2 blocking connections.
 *
 * @since 5.0
 */
public final class FrameInputBuffer {

    private final BasicH2TransportMetrics metrics;
    private final int maxFramePayloadSize;
    private final byte[] buffer;

    private int off;
    private int dataLen;

    FrameInputBuffer(final BasicH2TransportMetrics metrics, final int bufferLen, final int maxFramePayloadSize) {
        Args.notNull(metrics, "HTTP2 transport metrics");
        Args.positive(maxFramePayloadSize, "Maximum payload size");
        this.metrics = metrics;
        this.maxFramePayloadSize = maxFramePayloadSize;
        this.buffer = new byte[bufferLen];
        this.dataLen = 0;
    }

    public FrameInputBuffer(final BasicH2TransportMetrics metrics, final int maxFramePayloadSize) {
        this(metrics, FrameConsts.HEAD_LEN + maxFramePayloadSize, maxFramePayloadSize);
    }

    public FrameInputBuffer(final int maxFramePayloadSize) {
        this(new BasicH2TransportMetrics(), maxFramePayloadSize);
    }

    boolean hasData() {
        return this.dataLen > 0;
    }

    void fillBuffer(final InputStream inStream, final int requiredLen) throws IOException {
        while (dataLen < requiredLen) {
            if (off > 0) {
                System.arraycopy(buffer, off, buffer, 0, dataLen);
                off = 0;
            }
            final int bytesRead = inStream.read(buffer, off + dataLen, buffer.length - dataLen);
            if (bytesRead == -1) {
                if (dataLen > 0) {
                    throw new H2CorruptFrameException("Corrupt or incomplete HTTP2 frame");
                }
                throw new ConnectionClosedException();
            }
            dataLen += bytesRead;
            this.metrics.incrementBytesTransferred(bytesRead);
        }
    }

    public RawFrame read(final InputStream inStream) throws IOException {

        fillBuffer(inStream, FrameConsts.HEAD_LEN);
        final int payloadOff = FrameConsts.HEAD_LEN;

        final int payloadLen = (buffer[off] & 0xff) << 16 | (buffer[off + 1] & 0xff) << 8 | (buffer[off + 2] & 0xff);
        final int type = buffer[off + 3] & 0xff;
        final int flags = buffer[off + 4] & 0xff;
        final int streamId = Math.abs(buffer[off + 5] & 0xff) << 24 | (buffer[off + 6] & 0xff << 16) | (buffer[off + 7] & 0xff) << 8 | (buffer[off + 8] & 0xff);
        if (payloadLen > maxFramePayloadSize) {
            throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Frame size exceeds maximum");
        }

        final int frameLen = payloadOff + payloadLen;
        fillBuffer(inStream, frameLen);

        if ((flags & FrameFlag.PADDED.getValue()) > 0) {
            if (payloadLen == 0) {
                throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Inconsistent padding");
            }
            final int padding = buffer[off + FrameConsts.HEAD_LEN] & 0xff;
            if (payloadLen < padding + 1) {
                throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Inconsistent padding");
            }
        }

        final ByteBuffer payload = payloadLen > 0 ? ByteBuffer.wrap(buffer, off + payloadOff, payloadLen) : null;
        final RawFrame frame = new RawFrame(type, flags, streamId, payload);

        off += frameLen;
        dataLen -= frameLen;

        this.metrics.incrementFramesTransferred();

        return frame;
    }

    public H2TransportMetrics getMetrics() {
        return metrics;
    }

}
