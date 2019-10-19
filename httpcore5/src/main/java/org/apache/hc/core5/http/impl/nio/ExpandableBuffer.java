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

package org.apache.hc.core5.http.impl.nio;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.annotation.Internal;

/**
 * A buffer that expand its capacity on demand. Internally, this class is backed
 * by an instance of {@link ByteBuffer}.
 * <p>
 * This class is not thread safe.
 * </p>
 * @since 4.0
 */
@Internal
public class ExpandableBuffer {

    public enum Mode {
        INPUT, OUTPUT
    }

    private Mode mode;
    private ByteBuffer buffer;

    /**
     * Allocates buffer of the given size using the given allocator.
     * <p>
     * Sets the mode to input.
     * </p>
     *
     * @param bufferSize the buffer size.
     */
    protected ExpandableBuffer(final int bufferSize) {
        super();
        this.buffer = ByteBuffer.allocate(bufferSize);
        this.mode = Mode.INPUT;
    }

    /**
     * Returns the current mode:
     * <p>
     * {@link Mode#INPUT}: the buffer is in the input mode.
     * <p>
     * {@link Mode#OUTPUT}: the buffer is in the output mode.
     *
     * @return current input/output mode.
     */
    protected Mode mode() {
        return this.mode;
    }

    protected ByteBuffer buffer() {
        return this.buffer;
    }

    /**
     * Sets the mode to output. The buffer can now be read from.
     */
    protected void setOutputMode() {
        if (this.mode != Mode.OUTPUT) {
            this.buffer.flip();
            this.mode = Mode.OUTPUT;
        }
    }

    /**
     * Sets the mode to input. The buffer can now be written into.
     */
    protected void setInputMode() {
        if (this.mode != Mode.INPUT) {
            if (this.buffer.hasRemaining()) {
                this.buffer.compact();
            } else {
                this.buffer.clear();
            }
            this.mode = Mode.INPUT;
        }
    }

    private void expandCapacity(final int capacity) {
        final ByteBuffer oldBuffer = this.buffer;
        this.buffer = ByteBuffer.allocate(capacity);
        oldBuffer.flip();
        this.buffer.put(oldBuffer);
    }

    /**
     * Expands buffer's capacity.
     *
     * @throws BufferOverflowException in case we get over the maximum allowed value
     */
    protected void expand() throws BufferOverflowException {
        int newcapacity = (this.buffer.capacity() + 1) << 1;
        if (newcapacity < 0) {
            final int vmBytes = Long.SIZE >> 3;
            final int javaBytes = 8; // this is to be checked when the JVM version changes
            @SuppressWarnings("unused") // we really need the 8 if we're going to make this foolproof
            final int headRoom = (vmBytes >= javaBytes) ? vmBytes : javaBytes;
            // Reason: In GC the size of objects is passed as int (2 bytes).
            // Then, the header size of the objects is added to the size.
            // Long has the longest header available. Object header seems to be linked to it.
            // Details: I added a minimum of 8 just to be safe and because 8 is used in
            // java.lang.Object.ArrayList: private static final int MAX_ARRAY_SIZE = 2147483639.
            //
            // WARNING: This code assumes you are providing enough heap room with -Xmx.
            // source of inspiration: https://bugs.openjdk.java.net/browse/JDK-8059914
            newcapacity = Integer.MAX_VALUE - headRoom;

            if (newcapacity <= this.buffer.capacity()) {
                throw new BufferOverflowException();
            }
        }
        expandCapacity(newcapacity);
    }

    /**
     * Ensures the buffer can accommodate the exact required capacity.
     *
     * @param requiredCapacity the required capacity.
     */
    protected void ensureCapacity(final int requiredCapacity) {
        if (requiredCapacity > this.buffer.capacity()) {
            expandCapacity(requiredCapacity);
        }
    }

    /**
     * Ensures the buffer can accommodate at least the required capacity adjusted to multiples of 1024.
     *
     * @param requiredCapacity the required capacity.
     */
    protected void ensureAdjustedCapacity(final int requiredCapacity) {
        if (requiredCapacity > this.buffer.capacity()) {
            final int adjustedCapacity = ((requiredCapacity >> 10) + 1) << 10;
            expandCapacity(adjustedCapacity);
        }
    }

    /**
     * Determines if the buffer contains data.
     * <p>
     * Sets the mode to output.
     * </p>
     *
     * @return {@code true} if there is data in the buffer,
     *   {@code false} otherwise.
     */
    protected boolean hasData() {
        setOutputMode();
        return this.buffer.hasRemaining();
    }

    /**
     * Returns the length of this buffer.
     * <p>
     * Sets the mode to output.
     * </p>
     *
     * @return buffer length.
     */
    protected int length() {
        setOutputMode();
        return this.buffer.remaining();
    }

    /**
     * Returns available capacity of this buffer.
     *
     * @return buffer length.
     */
    protected int capacity() {
        setInputMode();
        return this.buffer.remaining();
    }

    /**
     * Clears buffer.
     * <p>
     * Sets the mode to input.
     * </p>
     */
    protected void clear() {
        this.buffer.clear();
        this.mode = Mode.INPUT;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[mode=");
        sb.append(this.mode);
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
