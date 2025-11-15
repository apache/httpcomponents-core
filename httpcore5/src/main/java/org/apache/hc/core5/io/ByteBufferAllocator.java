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

/**
 * Strategy for allocating and releasing {@link ByteBuffer} instances.
 * <p>
 * Implementations may allocate fresh buffers on every call or reuse
 * buffers from a pool.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface ByteBufferAllocator {

    /**
     * Allocates a new heap buffer of the given capacity.
     *
     * @param capacity buffer capacity in bytes; non-negative.
     * @return a heap {@link ByteBuffer} with the given capacity.
     */
    ByteBuffer allocate(int capacity);

    /**
     * Allocates a new direct buffer of the given capacity.
     *
     * @param capacity buffer capacity in bytes; non-negative.
     * @return a direct {@link ByteBuffer} with the given capacity.
     */
    ByteBuffer allocateDirect(int capacity);

    /**
     * Releases a buffer back to the allocator.
     * <p>
     * Implementations that do not pool buffers may choose to ignore
     * this call.
     *
     * @param buffer the buffer to release; may be {@code null}.
     */
    void release(ByteBuffer buffer);

}
