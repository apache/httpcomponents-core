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

package org.apache.http.nio.util;

import java.nio.ByteBuffer;

import org.apache.http.io.BufferInfo;

/**
 * A buffer that expand its capacity on demand using {@link ByteBufferAllocator}
 * interface. Internally, this class is backed by an instance of
 * {@link ByteBuffer}.
 * <p>
 * This class is not thread safe.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
public class ExpandableBuffer implements BufferInfo, org.apache.http.nio.util.BufferInfo {

    public final static int INPUT_MODE = 0;
    public final static int OUTPUT_MODE = 1;

    private int mode;
    protected ByteBuffer buffer = null;
    private final ByteBufferAllocator allocator;

    /**
     * Allocates buffer of the given size using the given allocator.
     *
     * @param buffersize the buffer size.
     * @param allocator allocator to be used to allocate {@link ByteBuffer}s.
     */
    public ExpandableBuffer(int buffersize, final ByteBufferAllocator allocator) {
        super();
        if (allocator == null) {
            throw new IllegalArgumentException("ByteBuffer allocator may not be null");
        }
        this.allocator = allocator;
        this.buffer = allocator.allocate(buffersize);
        this.mode = INPUT_MODE;
    }

    /**
     * Returns the current mode:
     * <p>
     * {@link #INPUT_MODE}: the buffer is in the input mode.
     * <p>
     * {@link #OUTPUT_MODE}: the buffer is in the output mode.
     *
     * @return current input/output mode.
     */
    protected int getMode() {
        return this.mode;
    }

    /**
     * Sets output mode. The buffer can now be read from.
     */
    protected void setOutputMode() {
        if (this.mode != OUTPUT_MODE) {
            this.buffer.flip();
            this.mode = OUTPUT_MODE;
        }
    }

    /**
     * Sets input mode. The buffer can now be written into.
     */
    protected void setInputMode() {
        if (this.mode != INPUT_MODE) {
            if (this.buffer.hasRemaining()) {
                this.buffer.compact();
            } else {
                this.buffer.clear();
            }
            this.mode = INPUT_MODE;
        }
    }

    private void expandCapacity(int capacity) {
        ByteBuffer oldbuffer = this.buffer;
        this.buffer = allocator.allocate(capacity);
        oldbuffer.flip();
        this.buffer.put(oldbuffer);
    }

    /**
     * Expands buffer's capacity.
     */
    protected void expand() {
        int newcapacity = (this.buffer.capacity() + 1) << 1;
        if (newcapacity < 0) {
            newcapacity = Integer.MAX_VALUE;
        }
        expandCapacity(newcapacity);
    }

    /**
     * Ensures the buffer can accommodate the required capacity.
     *
     * @param requiredCapacity
     */
    protected void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity > this.buffer.capacity()) {
            expandCapacity(requiredCapacity);
        }
    }

    /**
     * Returns the total capacity of this buffer.
     *
     * @return total capacity.
     */
    public int capacity() {
        return this.buffer.capacity();
    }

    /**
     * Determines if the buffer contains data.
     *
     * @return <code>true</code> if there is data in the buffer,
     *   <code>false</code> otherwise.
     */
    public boolean hasData() {
        setOutputMode();
        return this.buffer.hasRemaining();
    }

    /**
     * Returns the length of this buffer.
     *
     * @return buffer length.
     */
    public int length() {
        setOutputMode();
        return this.buffer.remaining();
    }

    /**
     * Returns available capacity of this buffer.
     *
     * @return buffer length.
     */
    public int available() {
        setInputMode();
        return this.buffer.remaining();
    }

    /**
     * Clears buffer.
     */
    protected void clear() {
        this.buffer.clear();
        this.mode = INPUT_MODE;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[mode=");
        int mode = getMode();
        if (mode == INPUT_MODE) {
            sb.append("in");
        } else {
            sb.append("out");
        }
        sb.append(" pos=");
        sb.append(this.buffer.position());
        sb.append(" lim=");
        sb.append(this.buffer.limit());
        sb.append(" cap=");
        sb.append(this.buffer.capacity());
        sb.append("]");
        return sb.toString();
    }

}
