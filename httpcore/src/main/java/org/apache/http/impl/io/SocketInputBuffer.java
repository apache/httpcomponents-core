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

package org.apache.http.impl.io;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.io.EofSensor;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * {@link SessionInputBuffer} implementation bound to a {@link Socket}.
 *
 * @since 4.0
 */
@NotThreadSafe
public class SocketInputBuffer extends AbstractSessionInputBuffer implements EofSensor {

    private final Socket socket;

    private boolean eof;

    /**
     * Creates an instance of this class.
     *
     * @param socket the socket to read data from.
     * @param buffersize the size of the internal buffer. If this number is less
     *   than <code>0</code> it is set to the value of
     *   {@link Socket#getReceiveBufferSize()}. If resultant number is less
     *   than <code>1024</code> it is set to <code>1024</code>.
     * @param params HTTP parameters.
     *
     * @deprecated (4.3) use {@link SocketInputBuffer#create(Socket, int, Charset, int, int, CodingErrorAction, CodingErrorAction)}
     */
    @Deprecated
    public SocketInputBuffer(
            final Socket socket,
            int buffersize,
            final HttpParams params) throws IOException {
        super();
        Args.notNull(socket, "Socket");
        this.socket = socket;
        this.eof = false;
        if (buffersize < 0) {
            buffersize = socket.getReceiveBufferSize();
        }
        if (buffersize < 1024) {
            buffersize = 1024;
        }
        init(socket.getInputStream(), buffersize, params);
    }

    SocketInputBuffer(
            final Socket socket,
            int buffersize,
            final Charset charset,
            int maxLineLen,
            int minChunkLimit,
            final CodingErrorAction malformedCharAction,
            final CodingErrorAction unmappableCharAction) throws IOException {
        super(socket.getInputStream(), buffersize, charset, maxLineLen, minChunkLimit,
                malformedCharAction, unmappableCharAction);
        this.socket = socket;
        this.eof = false;
    }

    /**
     * Creates SocketInputBuffer instance.
     *
     * @param socket socket
     * @param buffersize buffer size. If this number is negative it is set to the value of
     *   {@link Socket#getReceiveBufferSize()}. If resultant number is less than
     *   <code>1024</code> it is set to <code>1024</code>.
     * @param charset charset to be used for decoding HTTP protocol elements.
     *   If <code>null</code> US-ASCII will be used.
     * @param maxLineLen maximum line length limit. If set to a positive value, any line exceeding
     *   this limit will cause an I/O error. A negative value will disable the check.
     * @param minChunkLimit size limit below which data chunks should be buffered in memory
     *   in order to minimize native method invocations on the underlying network socket.
     *   The optimal value of this parameter can be platform specific and defines a trade-off
     *   between performance of memory copy operations and that of native method invocation.
     *   If negative default chunk limited will be used.
     * @param malformedCharAction action to perform upon receiving a malformed input.
     *   If <code>null</code> {@link CodingErrorAction#REPORT} will be used.
     * @param unmappableCharAction action to perform upon receiving an unmappable input.
     *   If <code>null</code> {@link CodingErrorAction#REPORT}  will be used.
     *
     * @since 4.3
     */
    public static SocketInputBuffer create(
            final Socket socket,
            int buffersize,
            final Charset charset,
            int maxLineLen,
            int minChunkLimit,
            final CodingErrorAction malformedCharAction,
            final CodingErrorAction unmappableCharAction) throws IOException {
        Args.notNull(socket, "Socket");
        if (buffersize < 0) {
            buffersize = socket.getReceiveBufferSize();
        }
        if (buffersize < 1024) {
            buffersize = 1024;
        }
        return new SocketInputBuffer(socket, buffersize, charset, maxLineLen, minChunkLimit,
                malformedCharAction, unmappableCharAction);
    }

    /**
     * Creates SocketInputBuffer instance.
     *
     * @param socket socket
     * @param buffersize buffer size. If this number is negative it is set to the value of
     *   {@link Socket#getReceiveBufferSize()}. If resultant number is less than
     *   <code>1024</code> it is set to <code>1024</code>.
     *
     * @since 4.3
     */
    public static SocketInputBuffer create(
            final Socket socket,
            int buffersize) throws IOException {
        return create(socket, buffersize, null, -1, -1, null, null);
    }

    @Override
    protected int fillBuffer() throws IOException {
        int i = super.fillBuffer();
        this.eof = i == -1;
        return i;
    }

    public boolean isDataAvailable(int timeout) throws IOException {
        boolean result = hasBufferedData();
        if (!result) {
            int oldtimeout = this.socket.getSoTimeout();
            try {
                this.socket.setSoTimeout(timeout);
                fillBuffer();
                result = hasBufferedData();
            } catch (SocketTimeoutException ex) {
                throw ex;
            } finally {
                socket.setSoTimeout(oldtimeout);
            }
        }
        return result;
    }

    public boolean isEof() {
        return this.eof;
    }

}
