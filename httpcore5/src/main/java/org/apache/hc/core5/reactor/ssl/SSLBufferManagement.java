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

/**
 * @since 5.0
 */
public enum SSLBufferManagement {

    STATIC,
    DYNAMIC;

    static SSLBuffer create(final SSLBufferManagement mode, final int size) {
        return mode == DYNAMIC ? new DynamicBuffer(size) : new StaticBuffer(size);
    }

    private static final class StaticBuffer implements SSLBuffer {

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

    private static final class DynamicBuffer implements SSLBuffer {

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
