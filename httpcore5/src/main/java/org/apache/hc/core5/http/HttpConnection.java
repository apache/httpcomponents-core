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

package org.apache.hc.core5.http;

import java.io.IOException;
import java.net.SocketAddress;

import javax.net.ssl.SSLSession;

/**
 * A generic HTTP connection, useful on client and server side.
 *
 * @since 4.0
 */
public interface HttpConnection extends SocketModalCloseable {

    /**
     * Closes this connection gracefully. This method will attempt to flush the internal output
     * buffer prior to closing the underlying socket. This method MUST NOT be called from a
     * different thread to force shutdown of the connection. Use {@link #close shutdown} instead.
     */
    @Override
    void close() throws IOException;

    /**
     * Returns this connection's endpoint details.
     *
     * @return this connection's endpoint details.
     */
    EndpointDetails getEndpointDetails();

    /**
     * Returns this connection's local address or {@code null} if it is not bound yet.
     *
     * @return this connection's local address or {@code null} if it is not bound yet.
     * @since 5.0
     */
    SocketAddress getLocalAddress();

    /**
     * Returns this connection's remote address or {@code null} if it is not connected yet or
     * unconnected.
     *
     * @return this connection's remote address or {@code null} if it is not connected yet or
     *         unconnected.
     * @since 5.0
     */
    SocketAddress getRemoteAddress();

    /**
     * Returns this connection's protocol version or {@code null} if unknown.
     *
     * @return this connection's protocol version or {@code null} if unknown.
     * @since 5.0
     */
    ProtocolVersion getProtocolVersion();

    /**
     * Returns this connection's SSL session or {@code null} if TLS has not been activated.
     *
     * @return this connection's SSL session or {@code null} if TLS has not been activated.
     */
    SSLSession getSSLSession();

    /**
     * Checks if this connection is open.
     *
     * @return true if it is open, false if it is closed.
     */
    boolean isOpen();

}
