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

package org.apache.hc.core5.reactor.ssl;

import java.nio.ByteBuffer;

import org.apache.hc.core5.util.Args;

abstract class SSLManagedBuffer {

    /**
     * Allocates the resources required for this buffer, or returns the resources already allocated for this buffer.
     * Unless {@link #release() } is called, multiple invocations to this method must return the same
     * {@link java.nio.ByteBuffer}.
     * @return buffer
     */
    abstract ByteBuffer acquire();
    /**
     * Releases the resources for this buffer. If the buffer has already been released, this method does nothing.
     */
    abstract void release();
    /**
     * Tests to see if this buffer has been acquired.
     * @return {@code true} if the buffer is acquired, otherwise {@code false}
     */
    abstract boolean isAcquired();
    /**
     * Tests to make sure that the buffer has been acquired and the underlying buffer has a position larger than
     * {@code 0}. Essentially the same as {@code isAquired() && acquire().position > 0}.
     * @return {@code true} if the buffer has been acquired and the underlying buffer's position is {@code &gt; 0},
     * otherwise {@code false}
     */
    abstract boolean hasData();

    /**
     * Expands the underlying buffer's to make sure it has enough write capacity to accommodate
     * the required amount of bytes. This method has no side effect if the buffer has enough writeable
     * capacity left.
     * @param size the required write capacity
     */
    abstract void ensureWriteable(final int size);

    /**
     * Helper method to ensure additional writeable capacity with respect to the source buffer. It
     * allocates a new buffer and copies all the data if needed, returning the new buffer. This method
     * has no side effect if the source buffer has enough writeable capacity left.
     * @param src source buffer
     * @param size the required write capacity
     * @return new buffer (or the source buffer of it  has enough writeable capacity left)
     */
    ByteBuffer ensureWriteable(final ByteBuffer src, final int size) {
        if (src == null) {
            // Nothing to do, the buffer is not allocated
            return null;
        }

        // There is not enough capacity left, we need to expand
        if (src.remaining() < size) {
            final int additionalCapacityNeeded = size - src.remaining();
            final ByteBuffer expanded = ByteBuffer.allocate(src.capacity() + additionalCapacityNeeded);

            // use a duplicated buffer so we don't disrupt the limit of the original buffer
            final ByteBuffer tmp = src.duplicate();
            tmp.flip();

            // Copy to expanded buffer
            expanded.put(tmp);

            // Use a new buffer
            return expanded;
        } else {
            return src;
        }
    }

    static SSLManagedBuffer create(final SSLBufferMode mode, final int size) {
        return mode == SSLBufferMode.DYNAMIC ? new DynamicBuffer(size) : new StaticBuffer(size);
    }

    static final class StaticBuffer extends SSLManagedBuffer {

        private ByteBuffer buffer;

        public StaticBuffer(final int size) {
            Args.positive(size, "size");
            buffer = ByteBuffer.allocate(size);
        }

        @Override
        public ByteBuffer acquire() {
            return buffer;
        }

        @Override
        public void release() {
            // do nothing
        }

        @Override
        public boolean isAcquired() {
            return true;
        }

        @Override
        public boolean hasData() {
            return buffer.position() > 0;
        }

        @Override
        void ensureWriteable(final int size) {
            buffer = ensureWriteable(buffer, size);
        }
    }

    static final class DynamicBuffer extends SSLManagedBuffer {

        private ByteBuffer wrapped;
        private final int length;

        public DynamicBuffer(final int size) {
            Args.positive(size, "size");
            this.length = size;
        }

        @Override
        public ByteBuffer acquire() {
            if (wrapped != null) {
                return wrapped;
            }
            wrapped = ByteBuffer.allocate(length);
            return wrapped;
        }

        @Override
        public void release() {
            wrapped = null;
        }

        @Override
        public boolean isAcquired() {
            return wrapped != null;
        }

        @Override
        public boolean hasData() {
            return wrapped != null && wrapped.position() > 0;
        }

        @Override
        void ensureWriteable(final int size) {
            wrapped = ensureWriteable(wrapped, size);
        }
    }

}
