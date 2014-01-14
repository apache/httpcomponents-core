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

package org.apache.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

import org.apache.http.nio.util.ExpandableBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;

public class WritableByteChannelMock implements WritableByteChannel {

    static class InternalBuffer extends ExpandableBuffer {

        private final int capacityLimit;
        private int curCapacity;

        public InternalBuffer(final int buffersize, final int capacityLimit) {
            super(buffersize, HeapByteBufferAllocator.INSTANCE);
            this.capacityLimit = capacityLimit;
            this.curCapacity = capacityLimit;
        }

        public int write(final ByteBuffer src) {
            if (src == null) {
                return 0;
            }
            setInputMode();
            if (this.capacityLimit > 0) {
                if (this.curCapacity > 0) {
                    final int requiredCapacity = this.buffer.position() + this.curCapacity;
                    ensureCapacity(requiredCapacity);
                    int count = 0;
                    while (src.hasRemaining() && this.curCapacity > 0) {
                        this.buffer.put(src.get());
                        count++;
                        this.curCapacity--;
                    }
                    return count;
                } else {
                    return 0;
                }
            } else {
                final int chunk = src.remaining();
                final int requiredCapacity = this.buffer.position() + src.remaining();
                ensureCapacity(requiredCapacity);
                this.buffer.put(src);
                return chunk;
            }
        }

        @Override
        protected void clear() {
            super.clear();
        }

        public void resetCapacity() {
            this.curCapacity = this.capacityLimit;
        }

        private static String toString(
            final byte[] b, final int off, final int len, final Charset charset) {
            return new String(b, off, len, charset);
        }

        public String dump(final Charset charset) {
            setOutputMode();
            if (this.buffer.hasArray()) {
                return toString(this.buffer.array(), this.buffer.position(), this.buffer.limit(),
                    charset);
            } else {
                final ByteBuffer dup = this.buffer.duplicate();
                final byte[] b = new byte[dup.remaining()];
                int i = 0;
                while (dup.hasRemaining()) {
                    b[i] = dup.get();
                    i++;
                }
                return toString(b, 0, b.length, charset);
            }
        }

    }

    private final InternalBuffer buf;
    private boolean closed;

    public WritableByteChannelMock(final int size, final int capacityLimit) {
        super();
        this.buf = new InternalBuffer(size, capacityLimit);
    }

    public WritableByteChannelMock(final int size) {
        this(size, 0);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (this.closed) {
            throw new ClosedChannelException();
        }
        return this.buf.write(src);
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
        this.buf.resetCapacity();
    }

    public void reset() {
        this.buf.resetCapacity();
        this.buf.clear();
    }

    public String dump(final Charset charset){
        return this.buf.dump(charset);
    }

}
