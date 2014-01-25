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
package org.apache.http.nio.reactor.ssl;

import java.nio.ByteBuffer;

/**
 * Managed internal SSL buffer.
 */
public interface SSLBuffer {
    /**
     * Allocates the resources required for this buffer, or returns the resources already allocated for this buffer.
     * Unless {@link #release() } is called, multiple invokations to this method must return the same
     * {@link java.nio.ByteBuffer}.
     * @return buffer
     */
    ByteBuffer acquire();
    /**
     * Releases the resources for this buffer. If the buffer has already been released, this method does nothing.
     */
    void release();
    /**
     * Tests to see if this buffer has been acquired.
     * @return {@code true} if the buffer is acquired, otherwise {@code false}
     */
    boolean isAcquired();
    /**
     * Tests to make sure that the buffer has been acquired and the underlying buffer has a position larger than
     * {@code 0}. Essentially the same as {@code isAquired() && acquire().position > 0}.
     * @return {@code true} if the buffer has been acquired and the underlying buffer's position is {@code &gt; 0},
     * otherwise {@code false}
     */
    boolean hasData();
}
