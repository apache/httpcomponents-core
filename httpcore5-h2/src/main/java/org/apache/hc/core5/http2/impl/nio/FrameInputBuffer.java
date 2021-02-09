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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

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
 * Frame input buffer for HTTP/2 non-blocking connections.
 *
 * @since 5.0
 */
public final class FrameInputBuffer {

    enum State { HEAD_EXPECTED, PAYLOAD_EXPECTED }

    private final BasicH2TransportMetrics metrics;
    private final int maxFramePayloadSize;
    private final byte[] bytes;
    private final ByteBuffer buffer;

    private State state;
    private int payloadLen;
    private int type;
    private int flags;
    private int streamId;

    FrameInputBuffer(final BasicH2TransportMetrics metrics, final int bufferLen, final int maxFramePayloadSize) {
        Args.notNull(metrics, "HTTP2 transport metrics");
        Args.positive(maxFramePayloadSize, "Maximum payload size");
        this.metrics = metrics;
        this.maxFramePayloadSize = Math.max(maxFramePayloadSize, FrameConsts.MIN_FRAME_SIZE);
        this.bytes = new byte[bufferLen];
        this.buffer = ByteBuffer.wrap(bytes);
        this.buffer.flip();
        this.state = State.HEAD_EXPECTED;
    }

    public FrameInputBuffer(final BasicH2TransportMetrics metrics, final int maxFramePayloadSize) {
        this(metrics, FrameConsts.HEAD_LEN + maxFramePayloadSize, maxFramePayloadSize);
    }

    public FrameInputBuffer(final int maxFramePayloadSize) {
        this(new BasicH2TransportMetrics(), maxFramePayloadSize);
    }

    public void put(final ByteBuffer src) {
        if (buffer.hasRemaining()) {
            buffer.compact();
        } else {
            buffer.clear();
        }
        buffer.put(src);
        buffer.flip();
    }

    public RawFrame read(final ReadableByteChannel channel) throws IOException {
        for (;;) {
            switch (state) {
                case HEAD_EXPECTED:
                    if (buffer.remaining() >= FrameConsts.HEAD_LEN) {
                        final int lengthAndType = buffer.getInt();
                        payloadLen = lengthAndType >> 8;
                        if (payloadLen > maxFramePayloadSize) {
                            throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, "Frame size exceeds maximum");
                        }
                        type = lengthAndType & 0xff;
                        flags = buffer.get();
                        streamId = Math.abs(buffer.getInt());
                        state = State.PAYLOAD_EXPECTED;
                    } else {
                        break;
                    }
                case PAYLOAD_EXPECTED:
                    if (buffer.remaining() >= payloadLen) {
                        if ((flags & FrameFlag.PADDED.getValue()) > 0) {
                            if (payloadLen == 0) {
                                throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Inconsistent padding");
                            }
                            buffer.mark();
                            final int padding = buffer.get();
                            if (payloadLen < padding + 1) {
                                throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Inconsistent padding");
                            }
                            buffer.reset();
                        }
                        final ByteBuffer payload = payloadLen > 0 ? ByteBuffer.wrap(bytes, buffer.position(), payloadLen) : null;
                        buffer.position(buffer.position() + payloadLen);
                        state = State.HEAD_EXPECTED;
                        metrics.incrementFramesTransferred();
                        return new RawFrame(type, flags, streamId, payload);
                    }
            }
            if (buffer.hasRemaining()) {
                buffer.compact();
            } else {
                buffer.clear();
            }
            final int bytesRead = channel.read(buffer);
            buffer.flip();
            if (bytesRead > 0) {
                metrics.incrementBytesTransferred(bytesRead);
            }
            if (bytesRead == 0) {
                break;
            } else if (bytesRead < 0) {
                if (state != State.HEAD_EXPECTED || buffer.hasRemaining()) {
                    throw new H2CorruptFrameException("Corrupt or incomplete HTTP2 frame");
                } else {
                    throw new ConnectionClosedException();
                }
            }
        }
        return null;
    }

    public void reset() {
        buffer.compact();
        state = State.HEAD_EXPECTED;
    }

    public H2TransportMetrics getMetrics() {
        return metrics;
    }

}
