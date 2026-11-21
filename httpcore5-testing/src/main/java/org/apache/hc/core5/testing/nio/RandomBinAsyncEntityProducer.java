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
package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityProducer;

/**
 * An entity that generates random data.
 */
public class RandomBinAsyncEntityProducer extends AbstractBinAsyncEntityProducer {

    /**
     * The range from which to generate random data.
     */
    private final static byte[] RANGE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            .getBytes(StandardCharsets.US_ASCII);

    /**
     * The length of the random data to generate.
     */
    private final long length;
    private long remaining;
    private final ByteBuffer buffer;
    private final int delayMs;
    private final boolean drip;
    private volatile long deadline;

    public RandomBinAsyncEntityProducer(final long len) {
        this(len, 0, false);
    }

    public RandomBinAsyncEntityProducer(final long len, final int delayMs, final boolean drip) {
        super(512, ContentType.DEFAULT_TEXT);
        length = len;
        remaining = len;
        buffer = ByteBuffer.allocate(1024);
        this.delayMs = delayMs;
        this.deadline = System.currentTimeMillis() + (drip ? 0 : delayMs);
        this.drip = drip;
    }

    @Override
    public void releaseResources() {
        remaining = length;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public int availableData() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void produceData(final StreamChannel<ByteBuffer> channel) throws IOException {
        if (System.currentTimeMillis() < deadline) {
            return;
        }

        final int chunk = Math.min((int) (remaining < Integer.MAX_VALUE ? remaining : Integer.MAX_VALUE), buffer.remaining());
        for (int i = 0; i < chunk; i++) {
            final byte b = RANGE[(int) (Math.random() * RANGE.length)];
            buffer.put(b);
        }
        remaining -= chunk;

        buffer.flip();
        channel.write(buffer);
        buffer.compact();

        if (remaining <= 0 && buffer.position() == 0) {
            channel.endStream();
        } else if (drip) {
            deadline = System.currentTimeMillis() + delayMs;
        }
    }

    @Override
    public void failed(final Exception cause) {
    }

}
