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

package org.apache.http.nio;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;

/**
 * Abstract non-blocking client-side HTTP connection interface. It can be used
 * to submit HTTP requests and asynchronously receive HTTP responses.
 *
 * @see NHttpConnection
 *
 * @since 4.0
 */
public interface NHttpClientConnection extends NHttpConnection {

    /**
     * Submits {@link HttpRequest} to be sent to the target server.
     *
     * @param request HTTP request
     * @throws IOException if I/O error occurs while submitting the request
     * @throws HttpException if the HTTP request violates the HTTP protocol.
     */
    void submitRequest(HttpRequest request) throws IOException, HttpException;

    /**
     * Returns <code>true</code> if an HTTP request has been submitted to the
     * target server.
     *
     * @return <code>true</code> if an HTTP request has been submitted,
     * <code>false</code> otherwise.
     */
    boolean isRequestSubmitted();

    /**
     * Resets output state. This method can be used to prematurely terminate
     * processing of the outgoing HTTP request.
     */
    void resetOutput();

    /**
     * Resets input state. This method can be used to prematurely terminate
     * processing of the incoming HTTP response.
     */
    void resetInput();

}
