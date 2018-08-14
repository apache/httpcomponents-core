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

package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.util.Args;

/**
 * Abstract {@link ContentDecoder} that serves as a base for all content
 * decoder implementations.
 *
 * @since 4.0
 */
public abstract class AbstractContentDecoder implements ContentDecoder {

    final ReadableByteChannel channel;
    final SessionInputBuffer buffer;
    final BasicHttpTransportMetrics metrics;

    protected boolean completed;

    /**
     * Creates an instance of this class.
     *
     * @param channel the source channel.
     * @param buffer the session input buffer that can be used to store
     *    session data for intermediate processing.
     * @param metrics Transport metrics of the underlying HTTP transport.
     */
    public AbstractContentDecoder(
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final BasicHttpTransportMetrics metrics) {
        super();
        Args.notNull(channel, "Channel");
        Args.notNull(buffer, "Session input buffer");
        Args.notNull(metrics, "Transport metrics");
        this.buffer = buffer;
        this.channel = channel;
        this.metrics = metrics;
    }

    protected ReadableByteChannel channel() {
        return this.channel;
    }

    protected SessionInputBuffer buffer() {
        return this.buffer;
    }

    protected BasicHttpTransportMetrics metrics() {
        return this.metrics;
    }

    @Override
    public boolean isCompleted() {
        return this.completed;
    }

    /**
     * Sets the completed status of this decoder. Normally this is not necessary
     * (the decoder will automatically complete when the underlying channel
     * returns EOF). It is useful to mark the decoder as completed if you have
     * some other means to know all the necessary data has been read and want to
     * reuse the underlying connection for more messages.
     *
     * @param completed the completed status of this decoder.
     * @since 4.4.11
     */
    public void setCompleted(final boolean completed) {
        this.completed = completed;
    }

    /**
     * Sets the completed status of this decoder to true. Normally this is not necessary
     * (the decoder will automatically complete when the underlying channel
     * returns EOF). It is useful to mark the decoder as completed if you have
     * some other means to know all the necessary data has been read and want to
     * reuse the underlying connection for more messages.
     *
     * @since 4.4.11
     */
    protected void setCompleted() {
        this.completed = true;
    }

    /**
     * Reads from the channel to the destination.
     *
     * @param dst destination.
     * @return number of bytes transferred.
     *
     * @since 4.3
     */
    protected int readFromChannel(final ByteBuffer dst) throws IOException {
        final int bytesRead = this.channel.read(dst);
        if (bytesRead > 0) {
            this.metrics.incrementBytesTransferred(bytesRead);
        }
        return bytesRead;
    }

    /**
     * Reads from the channel to the session buffer.
     * @return number of bytes transferred.
     *
     * @since 4.3
     */
    protected int fillBufferFromChannel() throws IOException {
        final int bytesRead = this.buffer.fill(this.channel);
        if (bytesRead > 0) {
            this.metrics.incrementBytesTransferred(bytesRead);
        }
        return bytesRead;
    }

    /**
     * Reads from the channel to the destination.
     *
     * @param dst destination.
     * @param limit max number of bytes to transfer.
     * @return number of bytes transferred.
     *
     * @since 4.3
     */
    protected int readFromChannel(final ByteBuffer dst, final int limit) throws IOException {
        final int bytesRead;
        if (dst.remaining() > limit) {
            final int oldLimit = dst.limit();
            final int newLimit = oldLimit - (dst.remaining() - limit);
            dst.limit(newLimit);
            bytesRead = this.channel.read(dst);
            dst.limit(oldLimit);
        } else {
            bytesRead = this.channel.read(dst);
        }
        if (bytesRead > 0) {
            this.metrics.incrementBytesTransferred(bytesRead);
        }
        return bytesRead;
    }

    @Override
    public List<? extends Header> getTrailers() {
        return null;
    }

}
