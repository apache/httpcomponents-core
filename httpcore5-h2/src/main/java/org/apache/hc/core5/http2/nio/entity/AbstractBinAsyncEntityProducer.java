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
package org.apache.hc.core5.http2.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http2.nio.AsyncEntityProducer;
import org.apache.hc.core5.http2.nio.DataStreamChannel;
import org.apache.hc.core5.http2.nio.StreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public abstract class AbstractBinAsyncEntityProducer implements AsyncEntityProducer {

    private final ContentType contentType;
    private final ByteBuffer bytebuf;

    private volatile boolean endStream;

    public AbstractBinAsyncEntityProducer(final int bufferSize, final ContentType contentType) {
        Args.positive(bufferSize, "Buffer size");
        this.bytebuf = ByteBuffer.allocate(bufferSize);
        this.contentType = contentType;
    }

    protected abstract void dataStart() throws IOException;

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
    public final void streamStart(final DataStreamChannel channel)  throws IOException {
        dataStart();
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        produceData(new StreamChannel<ByteBuffer>() {

            @Override
            public int write(final ByteBuffer src) throws IOException {
                Args.notNull(src, "ByteBuffer");
                final int chunk = src.remaining();
                if (chunk == 0) {
                    return 0;
                }
                if (bytebuf.remaining() >= chunk) {
                    bytebuf.put(src);
                    return chunk;
                }
                if (bytebuf.position() > 0) {
                    bytebuf.flip();
                    final int bytesWritten = channel.write(bytebuf);
                    bytebuf.compact();
                    return bytesWritten;
                }
                if (bytebuf.position() == 0) {
                    return channel.write(src);
                }
                return 0;
            }

            @Override
            public void endStream() throws IOException {
                endStream = true;
            }

        });

        if (bytebuf.remaining() > 1024 || endStream) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
        if (bytebuf.position() == 0 && endStream) {
            channel.endStream();
        }
    }

}
