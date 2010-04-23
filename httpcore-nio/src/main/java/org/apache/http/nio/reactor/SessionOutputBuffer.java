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

package org.apache.http.nio.reactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharacterCodingException;

import org.apache.http.util.CharArrayBuffer;

/**
 * Session output buffer for non-blocking connections. This interface
 * facilitates intermediate buffering of output data streamed out to
 * a destination channel and writing data to the buffer from a source, usually
 * {@link ByteBuffer} or {@link ReadableByteChannel}. This interface also
 * provides methods for writing lines of text.
 *
 * @since 4.0
 */
public interface SessionOutputBuffer {

    /**
     * Determines if the buffer contains data.
     *
     * @return <code>true</code> if there is data in the buffer,
     *   <code>false</code> otherwise.
     */
    boolean hasData();

    /**
     * Returns the length of this buffer.
     *
     * @return buffer length.
     */
    int length();

    /**
     * Makes an attempt to flush the content of this buffer to the given
     * destination {@link WritableByteChannel}.
     *
     * @param channel the destination channel.
     * @return The number of bytes written, possibly zero.
     * @throws IOException in case of an I/O error.
     */
    int flush(WritableByteChannel channel)
        throws IOException;

    /**
     * Copies content of the source buffer into this buffer. The capacity of
     * the destination will be expanded in order to accommodate the entire
     * content of the source buffer.
     *
     * @param src the source buffer.
     */
    void write(ByteBuffer src);

    /**
     * Reads a sequence of bytes from the source channel into this buffer.
     *
     * @param src the source channel.
     */
    void write(ReadableByteChannel src)
        throws IOException;

    /**
     * Copies content of the source buffer into this buffer as one line of text
     * including a line delimiter. The capacity of the destination will be
     * expanded in order to accommodate the entire content of the source buffer.
     * <p>
     * The choice of a char encoding and line delimiter sequence is up to the
     * specific implementations of this interface.
     *
     * @param src the source buffer.
     */
    void writeLine(CharArrayBuffer src)
        throws CharacterCodingException;

    /**
     * Copies content of the given string into this buffer as one line of text
     * including a line delimiter.
     * The capacity of the destination will be expanded in order to accommodate
     * the entire string.
     * <p>
     * The choice of a char encoding and line delimiter sequence is up to the
     * specific implementations of this interface.
     *
     * @param s the string.
     */
    void writeLine(String s)
        throws IOException;

}
