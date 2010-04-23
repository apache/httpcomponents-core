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

import java.io.IOException;

import org.apache.http.nio.ContentEncoder;

/**
 * Buffer for storing content to be streamed out to a {@link ContentEncoder}.
 *
 * @since 4.0
 */
public interface ContentOutputBuffer {

    /**
     * Writes content from this buffer to the given {@link ContentEncoder}.
     *
     * @param encoder content encoder.
     * @return number of bytes written.
     * @throws IOException in case of an I/O error.
     */
    int produceContent(ContentEncoder encoder) throws IOException;

    /**
     * Resets the buffer by clearing its state and stored content.
     */
    void reset();

    /**
     * @deprecated No longer used.
     */
    @Deprecated
    void flush() throws IOException;

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this buffer.
     * <p>
     * If <code>off</code> is negative, or <code>len</code> is negative, or
     * <code>off+len</code> is greater than the length of the array
     * <code>b</code>, this method can throw a runtime exception. The exact type
     * of runtime exception thrown by this method depends on implementation.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     */
    void write(byte[] b, int off, int len) throws IOException;

    /**
     * Writes the specified byte to this buffer.
     *
     * @param      b   the <code>byte</code>.
     * @exception  IOException  if an I/O error occurs.
     */
    void write(int b) throws IOException;

    /**
     * Indicates the content has been fully written.
     * @exception  IOException  if an I/O error occurs.
     */
    void writeCompleted() throws IOException;

}
