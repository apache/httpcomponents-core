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

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;

/**
 * A client-side HTTP connection, which can be used for sending
 * requests and receiving responses.
 *
 * @since 4.0
 */
public interface HttpClientConnection extends BHttpConnection {

    /**
     * Checks whether this connection is in a consistent state.
     *
     * @return  {@code true} if the connection is known to be
     * in a inconsistent state and cannot be re-used.
     *
     * @see #terminateRequest(ClassicHttpRequest)
     *
     * @since 5.0
     */
    boolean isConsistent();

    /**
     * Sends the request line and all headers over the connection.
     * @param request the request whose headers to send.
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    void sendRequestHeader(ClassicHttpRequest request)
        throws HttpException, IOException;

    /**
     * Terminates request prematurely potentially leaving
     * the connection in a inconsistent state.
     *
     * @param request the request to be terminated prematurely.
     * @throws HttpException
     * @throws IOException
     *
     * @see #isConsistent()
     *
     * @since 5.0
     */
    void terminateRequest(ClassicHttpRequest request)
            throws HttpException, IOException;

    /**
     * Sends the request entity over the connection.
     * @param request the request whose entity to send.
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    void sendRequestEntity(ClassicHttpRequest request)
        throws HttpException, IOException;

    /**
     * Receives the request line and headers of the next response available from
     * this connection. The caller should examine the HttpResponse object to
     * find out if it should try to receive a response entity as well.
     *
     * @return a new HttpResponse object with status line and headers
     *         initialized or {@code null} if the connection has been closed
     *         by the opposite endpoint.
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    ClassicHttpResponse receiveResponseHeader()
        throws HttpException, IOException;

    /**
     * Receives the next response entity available from this connection and
     * attaches it to an existing HttpResponse object.
     *
     * @param response the response to attach the entity to
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    void receiveResponseEntity(ClassicHttpResponse response)
        throws HttpException, IOException;

}
