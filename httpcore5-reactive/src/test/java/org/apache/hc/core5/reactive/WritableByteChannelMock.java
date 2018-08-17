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

package org.apache.hc.core5.reactive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

public class WritableByteChannelMock implements WritableByteChannel {

    private final int capacityLimit;

    private int capacityUsed;
    private ByteBuffer buf;
    private boolean closed;

    public WritableByteChannelMock(final int initialSize, final int capacityLimit) {
        this.buf = ByteBuffer.allocate(initialSize);
        this.capacityLimit = capacityLimit;
    }

    public WritableByteChannelMock(final int initialSize) {
        this(initialSize, 0);
    }

    private void expandCapacity(final int capacity) {
        final ByteBuffer oldbuffer = this.buf;
        this.buf = ByteBuffer.allocate(capacity);
        oldbuffer.flip();
        this.buf.put(oldbuffer);
    }

    private void ensureCapacity(final int requiredCapacity) {
        if (requiredCapacity > this.buf.capacity()) {
            expandCapacity(requiredCapacity);
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (this.closed) {
            throw new ClosedChannelException();
        }
        final int len = src.remaining();
        ensureCapacity(this.buf.position() + len);
        if (this.capacityLimit > 0) {
            final int chunk = Math.min(this.capacityLimit - this.capacityUsed, len);
            if (chunk > 0) {
                final int limit = src.limit();
                src.limit(src.position() + chunk);
                this.buf.put(src);
                src.limit(limit);
                this.capacityUsed += chunk;
                return chunk;
            }
            return 0;
        }
        this.buf.put(src);
        return len;
    }

    @Override
    public boolean isOpen() {
        return !this.closed;
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
    }

    public void flush() {
        this.capacityUsed = 0;
    }

    public void reset() {
        this.capacityUsed = 0;
        this.buf.clear();
    }

    public byte[] toByteArray() {
        final ByteBuffer dup = this.buf.duplicate();
        dup.flip();
        final byte[] bytes = new byte[dup.remaining()];
        dup.get(bytes);
        return bytes;
    }

    public String dump(final Charset charset) throws CharacterCodingException {
        this.buf.flip();
        final CharBuffer charBuffer = charset.newDecoder().decode(this.buf);
        this.buf.compact();
        return charBuffer.toString();
    }

}
