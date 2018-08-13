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

package org.apache.hc.core5.http.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Session input buffer for HTTP/1.1 blocking connections.
 * <p>
 * This interface facilitates intermediate buffering of input data streamed from
 * an input stream and provides methods for reading lines of text.
 *
 * @since 4.0
 */
public interface SessionInputBuffer {

    /**
     * Returns length data stored in the buffer
     *
     * @return data length
     */
    int length();

    /**
     * Returns total capacity of the buffer
     *
     * @return total capacity
     */
    int capacity();

    /**
     * Returns available space in the buffer.
     *
     * @return available space.
     */
    int available();

    /**
     * Reads up to {@code len} bytes of data from the session buffer into
     * an array of bytes.  An attempt is made to read as many as
     * {@code len} bytes, but a smaller number may be read, possibly
     * zero. The number of bytes actually read is returned as an integer.
     *
     * <p> This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     *
     * <p> If {@code off} is negative, or {@code len} is negative, or
     * {@code off+len} is greater than the length of the array
     * {@code b}, then an {@code IndexOutOfBoundsException} is
     * thrown.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array {@code b}
     *                   at which the data is written.
     * @param      len   the maximum number of bytes to read.
     * @param      inputStream Input stream
     * @return     the total number of bytes read into the buffer, or
     *             {@code -1} if there is no more data because the end of
     *             the stream has been reached.
     * @throws  IOException  if an I/O error occurs.
     */
    int read(byte[] b, int off, int len, InputStream inputStream) throws IOException;

    /**
     * Reads some number of bytes from the session buffer and stores them into
     * the buffer array {@code b}. The number of bytes actually read is
     * returned as an integer.  This method blocks until input data is
     * available, end of file is detected, or an exception is thrown.
     *
     * @param      b   the buffer into which the data is read.
     * @param      inputStream Input stream
     * @return     the total number of bytes read into the buffer, or
     *             {@code -1} is there is no more data because the end of
     *             the stream has been reached.
     * @throws  IOException  if an I/O error occurs.
     */
    int read(byte[] b, InputStream inputStream) throws IOException;

    /**
     * Reads the next byte of data from this session buffer. The value byte is
     * returned as an {@code int} in the range {@code 0} to
     * {@code 255}. If no byte is available because the end of the stream
     * has been reached, the value {@code -1} is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * @param      inputStream Input stream
     * @return     the next byte of data, or {@code -1} if the end of the
     *             stream is reached.
     * @throws  IOException  if an I/O error occurs.
     */
    int read(InputStream inputStream) throws IOException;

    /**
     * Reads a complete line of characters up to a line delimiter from this
     * session buffer into the given line buffer. The number of chars actually
     * read is returned as an integer. The line delimiter itself is discarded.
     * If no char is available because the end of the stream has been reached,
     * the value {@code -1} is returned. This method blocks until input
     * data is available, end of file is detected, or an exception is thrown.
     * <p>
     * The choice of a char encoding and line delimiter sequence is up to the
     * specific implementations of this interface.
     *
     * @param      buffer   the line buffer, one line of characters upon return
     * @param      inputStream Input stream
     * @return     the total number of bytes read into the buffer, or
     *             {@code -1} is there is no more data because the end of
     *             the stream has been reached.
     * @throws  IOException  if an I/O error occurs.
     */
    int readLine(CharArrayBuffer buffer, InputStream inputStream) throws IOException;

    /**
     * Returns {@link HttpTransportMetrics} for this session buffer.
     *
     * @return transport metrics.
     */
    HttpTransportMetrics getMetrics();

}
