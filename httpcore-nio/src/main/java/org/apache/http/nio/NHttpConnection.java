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

import org.apache.http.HttpConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

/**
 * Abstract non-blocking HTTP connection interface. Each connection contains an
 * HTTP execution context, which can be used to maintain a processing state,
 * as well as the actual {@link HttpRequest} and {@link HttpResponse} that are
 * being transmitted over this connection. Both the request and
 * the response objects can be <code>null</code> if there is no incoming or
 * outgoing message currently being transferred.
 * <p>
 * Please note non-blocking HTTP connections are stateful and not thread safe.
 * Input / output operations on non-blocking HTTP connections should be
 * restricted to the dispatch events triggered by the I/O event dispatch thread.
 * However, the {@link IOControl} interface is fully threading safe and can be
 * manipulated from any thread.
 *
 * @since 4.0
 */
public interface NHttpConnection extends HttpConnection, IOControl {

    public static final int ACTIVE      = 0;
    public static final int CLOSING     = 1;
    public static final int CLOSED      = 2;

    /**
     * Returns status of the connection:
     * <p>
     * {@link #ACTIVE}: connection is active.
     * <p>
     * {@link #CLOSING}: connection is being closed.
     * <p>
     * {@link #CLOSED}: connection has been closed.
     *
     * @return connection status.
     */
    int getStatus();

    /**
     * Returns the current HTTP request if one is being received / transmitted.
     * Otherwise returns <code>null</code>.
     *
     * @return HTTP request, if available, <code>null</code> otherwise.
     */
    HttpRequest getHttpRequest();

    /**
     * Returns the current HTTP response if one is being received / transmitted.
     * Otherwise returns <tt>null</tt>.
     *
     * @return HTTP response, if available, <code>null</code> otherwise.
     */
    HttpResponse getHttpResponse();

    /**
     * Returns an HTTP execution context associated with this connection.
     * @return HTTP context
     */
    HttpContext getContext();

}
