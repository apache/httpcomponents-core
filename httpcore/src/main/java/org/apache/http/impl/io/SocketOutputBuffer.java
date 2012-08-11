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
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * {@link SessionOutputBuffer} implementation bound to a {@link Socket}.
 *
 * @since 4.0
 */
@NotThreadSafe
public class SocketOutputBuffer extends AbstractSessionOutputBuffer {

    /**
     * Creates an instance of this class.
     *
     * @param socket the socket to write data to.
     * @param buffersize the size of the internal buffer. If this number is less
     *   than <code>0</code> it is set to the value of
     *   {@link Socket#getSendBufferSize()}. If resultant number is less
     *   than <code>1024</code> it is set to <code>1024</code>.
     * @param params HTTP parameters.
     *
     * @deprecated (4.3) use {@link SocketOutputBuffer#create(Socket, int, Charset, int, CodingErrorAction, CodingErrorAction)}
     */
    @Deprecated
    public SocketOutputBuffer(
            final Socket socket,
            int buffersize,
            final HttpParams params) throws IOException {
        super();
        Args.notNull(socket, "Socket");
        if (buffersize < 0) {
            buffersize = socket.getSendBufferSize();
        }
        if (buffersize < 1024) {
            buffersize = 1024;
        }
        init(socket.getOutputStream(), buffersize, params);
    }

    SocketOutputBuffer(
            final Socket socket,
            int buffersize,
            final Charset charset,
            int minChunkLimit,
            final CodingErrorAction malformedCharAction,
            final CodingErrorAction unmappableCharAction) throws IOException {
        super(socket.getOutputStream(), buffersize, charset, minChunkLimit,
                malformedCharAction, unmappableCharAction);
    }

    /**
     * Creates SocketOutputBuffer instance.
     *
     * @param socket socket
     * @param buffersize buffer size. If this number is negative it is set to the value of
     *   {@link Socket#getSendBufferSize()}. If resultant number is less than
     *   <code>1024</code> it is set to <code>1024</code>.
     * @param charset charset to be used for decoding HTTP protocol elements.
     *   If <code>null</code> US-ASCII will be used.
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
    public static SocketOutputBuffer create(
            final Socket socket,
            int buffersize,
            final Charset charset,
            int minChunkLimit,
            final CodingErrorAction malformedCharAction,
            final CodingErrorAction unmappableCharAction) throws IOException {
        Args.notNull(socket, "Socket");
        if (buffersize < 0) {
            buffersize = socket.getSendBufferSize();
        }
        if (buffersize < 1024) {
            buffersize = 1024;
        }
        return new SocketOutputBuffer(socket, buffersize, charset, minChunkLimit,
                malformedCharAction, unmappableCharAction);
    }

    /**
     * Creates SocketOutputBuffer instance.
     *
     * @param socket socket
     * @param buffersize buffer size. If this number is negative it is set to the value of
     *   {@link Socket#getSendBufferSize()}. If resultant number is less than
     *   <code>1024</code> it is set to <code>1024</code>.
     *
     * @since 4.3
     */
    public static SocketOutputBuffer create(
            final Socket socket,
            int buffersize) throws IOException {
        return create(socket, buffersize, null, -1, null, null);
    }

}
