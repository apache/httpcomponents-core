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
package org.apache.hc.core5.http.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public abstract class AbstractBinAsyncEntityProducer implements AsyncEntityProducer {

    private final ByteBuffer bytebuf;
    private final int fragmentSizeHint;
    private final ContentType contentType;

    private volatile boolean endStream;

    public AbstractBinAsyncEntityProducer(
            final int bufferSize,
            final int fragmentSizeHint,
            final ContentType contentType) {
        Args.positive(bufferSize, "Buffer size");
        this.bytebuf = ByteBuffer.allocate(bufferSize);
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : bufferSize / 2;
        this.contentType = contentType;
    }

    protected abstract void produceData(StreamChannel<ByteBuffer> channel) throws IOException;

    @Override
    public final String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        produceData(new StreamChannel<ByteBuffer>() {

            @Override
            public int write(final ByteBuffer src) throws IOException {
                Args.notNull(src, "Buffer");
                final int chunk = src.remaining();
                if (chunk == 0) {
                    return 0;
                }
                if (bytebuf.remaining() >= chunk) {
                    bytebuf.put(src);
                    return chunk;
                }
                int totalBytesWritten = 0;
                if (!bytebuf.hasRemaining() || bytebuf.position() >= fragmentSizeHint) {
                    bytebuf.flip();
                    final int bytesWritten = channel.write(bytebuf);
                    bytebuf.compact();
                    totalBytesWritten += bytesWritten;
                }
                if (bytebuf.position() == 0) {
                    final int bytesWritten = channel.write(src);
                    totalBytesWritten += bytesWritten;
                }
                return totalBytesWritten;
            }

            @Override
            public void endStream() throws IOException {
                endStream = true;
            }

        });

        if (endStream || !bytebuf.hasRemaining() || bytebuf.position() >= fragmentSizeHint) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
        if (bytebuf.position() == 0 && endStream) {
            channel.endStream();
        }
    }

}
