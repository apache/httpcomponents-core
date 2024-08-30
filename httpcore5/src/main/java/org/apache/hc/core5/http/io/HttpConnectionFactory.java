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
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.http.HttpConnection;

/**
 * Factory for {@link HttpConnection} instances.
 *
 * @param <T> The type of {@link HttpConnection}.
 * @since 4.3
 */
public interface HttpConnectionFactory<T extends HttpConnection> {

    /**
     * Creates TLS connection with a {@link SSLSocket} layered over a plain {@link Socket}.
     *
     * @param socket the plain socket SSL socket has been layered over.
     * @return a new HttpConnection.
     * @throws IOException in case of an I/O error.
     */
    T createConnection(Socket socket) throws IOException;

    /**
     * Creates TLS connection with a {@link SSLSocket} layered over a plain {@link Socket}.
     *
     * @param sslSocket the SSL socket. May be {@code null}.
     * @param socket the plain socket SSL socket has been layered over.
     * @return a new HttpConnection.
     * @throws IOException in case of an I/O error.
     * @since 5.3
     */
    default T createConnection(SSLSocket sslSocket, Socket socket) throws IOException {
        return createConnection(sslSocket != null ? sslSocket : socket);
    }

}
