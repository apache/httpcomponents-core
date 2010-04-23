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
import org.apache.http.HttpResponse;

/**
 * Abstract non-blocking server-side HTTP connection interface. It can be used
 * to receive HTTP requests and asynchronously submit HTTP responses.
 *
 * @see NHttpConnection
 *
 * @since 4.0
 */
public interface NHttpServerConnection extends NHttpConnection {

    /**
     * Submits {link @HttpResponse} to be sent to the client.
     *
     * @param response HTTP response
     *
     * @throws IOException if I/O error occurs while submitting the response
     * @throws HttpException if the HTTP response violates the HTTP protocol.
     */
    void submitResponse(HttpResponse response) throws IOException, HttpException;

    /**
     * Returns <code>true</code> if an HTTP response has been submitted to the
     * client.
     *
     * @return <code>true</code> if an HTTP response has been submitted,
     * <code>false</code> otherwise.
     */
    boolean isResponseSubmitted();

    /**
     * Resets output state. This method can be used to prematurely terminate
     * processing of the incoming HTTP request.
     */
    void resetInput();

    /**
     * Resets input state. This method can be used to prematurely terminate
     * processing of the outgoing HTTP response.
     */
    void resetOutput();

}
