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

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2CorruptFrameException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.frame.ByteBufferFrame;
import org.apache.hc.core5.http2.frame.Frame;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.impl.BasicHttp2TransportMetrics;
import org.apache.hc.core5.http2.io.Http2TransportMetrics;
import org.apache.hc.core5.http2.io.SessionInputBuffer;
import org.apache.hc.core5.util.Args;

/**
 *
 * @since 5.0
 */
@NotThreadSafe
public class SessionInputBufferImpl implements SessionInputBuffer {

    private final BasicHttp2TransportMetrics metrics;
    private final int maxFramePayloadSize;
    private final byte[] buffer;

    private int off;
    private int dataLen;

    SessionInputBufferImpl(final BasicHttp2TransportMetrics metrics, final int bufferLen, final int maxFramePayloadSize) {
        Args.notNull(metrics, "HTTP2 transport metrcis");
        Args.positive(maxFramePayloadSize, "Buffer size");
        this.metrics = metrics;
        this.maxFramePayloadSize = maxFramePayloadSize;
        this.buffer = new byte[bufferLen];
        this.dataLen = 0;
    }

    public SessionInputBufferImpl(final BasicHttp2TransportMetrics metrics, final int maxFramePayloadSize) {
        this(metrics, FrameConsts.HEAD_LEN + maxFramePayloadSize + FrameConsts.MAX_PADDING + 1, maxFramePayloadSize);
    }

    public SessionInputBufferImpl(final int maxFramePayloadSize) {
        this(new BasicHttp2TransportMetrics(), maxFramePayloadSize);
    }

    private void fillBuffer(final InputStream instream, final int requiredLen)throws IOException {
        while (dataLen < requiredLen) {
            if (off > 0) {
                System.arraycopy(buffer, off, buffer, 0, dataLen);
                off = 0;
            }
            final int bytesRead = instream.read(buffer, off + dataLen, buffer.length - dataLen);
            if (bytesRead == -1) {
                if (dataLen > 0) {
                    throw new H2CorruptFrameException("Corrupt or incomplete HTTP2 frame");
                } else {
                    throw new ConnectionClosedException("Connection closed");
                }
            } else {
                dataLen += bytesRead;
                this.metrics.incrementBytesTransferred(bytesRead);
            }
        }
    }

    public Frame<ByteBuffer> readFrame(final InputStream instream) throws ProtocolException, IOException {

        fillBuffer(instream, FrameConsts.HEAD_LEN);
        int payloadOff = FrameConsts.HEAD_LEN;

        final int payloadLen = (buffer[off] & 0xff) << 16 | (buffer[off + 1] & 0xff) << 8 | (buffer[off + 2] & 0xff);
        final int type = buffer[off + 3] & 0xff;
        final int flags = buffer[off + 4] & 0xff;
        final long streamId = (buffer[off + 5] & 0xff) << 24 | (buffer[off + 6] & 0xff << 16) | (buffer[off + 7] & 0xff) << 8 | (buffer[off + 8] & 0xff);
        if (payloadLen > maxFramePayloadSize) {
            throw new H2ConnectionException(H2Error.FRAME_SIZE_ERROR, streamId, "Frame size exceeds maximum");
        }

        final int padding;
        if ((flags & FrameFlag.PADDED.getValue()) == 0) {
            padding = 0;
        } else {
            fillBuffer(instream, FrameConsts.HEAD_LEN + 1);
            payloadOff = FrameConsts.HEAD_LEN + 1;
            padding = buffer[off + 9] & 0xff;
        }

        final int frameLen = payloadOff + payloadLen + padding;
        fillBuffer(instream, frameLen);

        final ByteBuffer payload;
        if (payloadLen > 0) {
            payload = ByteBuffer.wrap(buffer, off + payloadOff, payloadLen).asReadOnlyBuffer();
        } else {
            payload = null;
        }

        final ByteBufferFrame frame = new ByteBufferFrame(type, flags, streamId, payload);

        off += frameLen;
        dataLen -= frameLen;

        this.metrics.incrementFramesTransferred();

        return frame;
    }

    public Http2TransportMetrics getMetrics() {
        return metrics;
    }

}
