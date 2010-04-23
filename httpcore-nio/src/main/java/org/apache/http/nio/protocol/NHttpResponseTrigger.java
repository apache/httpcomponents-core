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

package org.apache.http.nio.protocol;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;

/**
 * Callback interface to submit HTTP responses asynchronously.
 * <p/>
 * The {@link NHttpRequestHandler#handle(org.apache.http.HttpRequest, HttpResponse, NHttpResponseTrigger, org.apache.http.protocol.HttpContext)}
 * method does not have to submit a response immediately. It can defer
 * transmission of the HTTP response back to the client without blocking the
 * I/O thread by delegating the process of handling the HTTP request to a worker
 * thread. The worker thread in its turn can use the instance of
 * {@link NHttpResponseTrigger} passed as a parameter to submit a response as at
 * a later point of time once the response becomes available.
 *
 * @since 4.0
 */
public interface NHttpResponseTrigger {

    /**
     * Submits a response to be sent back to the client as a result of
     * processing of the request.
     */
    void submitResponse(HttpResponse response);

    /**
     * Reports a protocol exception thrown while processing the request.
     */
    void handleException(HttpException ex);

    /**
     * Report an IOException thrown while processing the request.
     */
    void handleException(IOException ex);

}
