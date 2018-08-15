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

package org.apache.hc.core5.http.nio.support.classic;

import java.io.IOException;

/**
 * Generic content output buffer.
 *
 * @since 4.0
 */
public interface ContentOutputBuffer {

    /**
     * Return length data stored in the buffer
     *
     * @return data length
     */
    int length();

    /**
     * Resets the buffer by clearing its state and stored content.
     */
    void reset();

    /**
     * Writes {@code len} bytes from the specified byte array
     * starting at offset {@code off} to this buffer.
     * <p>
     * If {@code off} is negative, or {@code len} is negative, or
     * {@code off+len} is greater than the length of the array
     * {@code b}, this method can throw a runtime exception. The exact type
     * of runtime exception thrown by this method depends on implementation.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @throws  IOException  if an I/O error occurs.
     */
    void write(byte[] b, int off, int len) throws IOException;

    /**
     * Writes the specified byte to this buffer.
     *
     * @param      b   the {@code byte}.
     * @throws  IOException  if an I/O error occurs.
     */
    void write(int b) throws IOException;

    /**
     * Indicates the content has been fully written.
     * @throws  IOException  if an I/O error occurs.
     */
    void writeCompleted() throws IOException;

}
