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

    static SSLManagedBuffer create(final SSLBufferMode mode, final int size) {
        return mode == SSLBufferMode.DYNAMIC ? new DynamicBuffer(size) : new StaticBuffer(size);
    }

    static final class StaticBuffer extends SSLManagedBuffer {

        private final ByteBuffer buffer;

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

    }

}
