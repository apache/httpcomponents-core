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

package org.apache.hc.core5.http.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Session input buffer for HTTP/1.1 non-blocking connections.
 * <p>
 * This interface facilitates intermediate buffering of input data streamed from
 * a source channel and provides methods for reading lines of text.
 * </p>
 *
 * @since 4.0
 */
public interface SessionInputBuffer {

    /**
     * Determines if the buffer contains data.
     *
     * @return {@code true} if there is data in the buffer,
     *   {@code false} otherwise.
     */
    boolean hasData();

    /**
     * Returns the length of this buffer.
     *
     * @return buffer length.
     */
    int length();

    /**
     * Makes an attempt to fill the buffer with data from the given
     * {@link ReadableByteChannel}.
     *
     * @param src the source channel
     * @return The number of bytes read, possibly zero, or {@code -1} if the
     *   channel has reached end-of-stream.
     * @throws IOException in case of an I/O error.
     */
    int fill(ReadableByteChannel src) throws IOException;

    /**
     * Reads one byte from the buffer. If the buffer is empty this method can
     * throw a runtime exception. The exact type of runtime exception thrown
     * by this method depends on implementation.
     *
     * @return one byte
     */
    int read();

    /**
     * Reads a sequence of bytes from this buffer into the destination buffer,
     * up to the given maximum limit. The exact number of bytes transferred
     * depends on availability of data in this buffer and capacity of the
     * destination buffer, but cannot be more than {@code maxLen} value.
     *
     * @param dst the destination buffer.
     * @param maxLen the maximum number of bytes to be read.
     * @return The number of bytes read, possibly zero.
     */
    int read(ByteBuffer dst, int maxLen);

    /**
     * Reads a sequence of bytes from this buffer into the destination buffer.
     * The exact number of bytes transferred depends on availability of data
     * in this buffer and capacity of the destination buffer.
     *
     * @param dst the destination buffer.
     * @return The number of bytes read, possibly zero.
     */
    int read(ByteBuffer dst);

    /**
     * Reads a sequence of bytes from this buffer into the destination channel,
     * up to the given maximum limit. The exact number of bytes transferred
     * depends on availability of data in this buffer, but cannot be more than
     * {@code maxLen} value.
     *
     * @param dst the destination channel.
     * @param maxLen the maximum number of bytes to be read.
     * @return The number of bytes read, possibly zero.
     * @throws IOException in case of an I/O error.
     */
    int read(WritableByteChannel dst, int maxLen) throws IOException;

    /**
     * Reads a sequence of bytes from this buffer into the destination channel.
     * The exact number of bytes transferred depends on availability of data in
     * this buffer.
     *
     * @param dst the destination channel.
     * @return The number of bytes read, possibly zero.
     * @throws IOException in case of an I/O error.
     */
    int read(WritableByteChannel dst) throws IOException;

    /**
     * Attempts to transfer a complete line of characters up to a line delimiter
     * from this buffer to the destination buffer. If a complete line is
     * available in the buffer, the sequence of chars is transferred to the
     * destination buffer the method returns {@code true}. The line
     * delimiter itself is discarded. If a complete line is not available in
     * the buffer, this method returns {@code false} without transferring
     * anything to the destination buffer. If {@code endOfStream} parameter
     * is set to {@code true} this method assumes the end of stream has
     * been reached and the content currently stored in the buffer should be
     * treated as a complete line.
     * <p>
     * The choice of a char encoding and line delimiter sequence is up to the
     * specific implementations of this interface.
     *
     * @param dst the destination buffer.
     * @param endOfStream end of stream flag
     * @return {@code true} if a sequence of chars representing a complete
     *  line has been transferred to the destination buffer, {@code false}
     *  otherwise.
     *
     * @throws java.nio.charset.CharacterCodingException in case a character encoding or decoding
     *   error occurs.
     * @throws org.apache.hc.core5.http.MessageConstraintException in case a message constraint violation.
     */
    boolean readLine(CharArrayBuffer dst, boolean endOfStream) throws IOException;

}
