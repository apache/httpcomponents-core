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
package org.apache.hc.core5.io;

import java.nio.ByteBuffer;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Simple {@link ByteBufferAllocator} that allocates a new buffer for
 * every request and does not pool released buffers.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class SimpleByteBufferAllocator implements ByteBufferAllocator {

    public static final SimpleByteBufferAllocator INSTANCE = new SimpleByteBufferAllocator();

    private SimpleByteBufferAllocator() {
    }

    @Override
    public ByteBuffer allocate(final int capacity) {
        Args.notNegative(capacity, "Buffer capacity");
        return ByteBuffer.allocate(capacity);
    }

    @Override
    public ByteBuffer allocateDirect(final int capacity) {
        Args.notNegative(capacity, "Buffer capacity");
        return ByteBuffer.allocateDirect(capacity);
    }

    @Override
    public void release(final ByteBuffer buffer) {
        // No-op: GC will reclaim the buffer.
    }

}
