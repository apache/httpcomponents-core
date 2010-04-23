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

import org.apache.http.nio.ContentDecoder;

/**
 * Buffer for storing content streamed out from a {@link ContentDecoder}.
 *
 * @since 4.0
 */
public interface ContentInputBuffer {

    /**
     * Reads content from the given {@link ContentDecoder} and stores it in
     * this buffer.
     *
     * @param decoder the content decoder.
     * @return number of bytes read.
     * @throws IOException in case of an I/O error.
     */
    int consumeContent(ContentDecoder decoder) throws IOException;

    /**
     * Resets the buffer by clearing its state and stored content.
     */
    void reset();

    /**
     * Reads up to <code>len</code> bytes of data from this buffer into
     * an array of bytes. The exact number of bytes read depends how many bytes
     * are stored in the buffer.
     *
     * <p> If <code>off</code> is negative, or <code>len</code> is negative, or
     * <code>off+len</code> is greater than the length of the array
     * <code>b</code>, this method can throw a runtime exception. The exact type
     * of runtime exception thrown by this method depends on implementation.
     * This method returns <code>-1</code> if the end of content stream has been
     * reached.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array <code>b</code>
     *                   at which the data is written.
     * @param      len   the maximum number of bytes to read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException  if an I/O error occurs.
     */
    int read(byte[] b, int off, int len) throws IOException;

    /**
     * Reads one byte from this buffer. If the buffer is empty this method can
     * throw a runtime exception. The exact type of runtime exception thrown
     * by this method depends on implementation. This method returns
     * <code>-1</code> if the end of content stream has been reached.
     *
     * @return one byte
     */
    int read() throws IOException;

}
