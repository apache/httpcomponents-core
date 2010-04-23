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

package org.apache.http.nio.reactor;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * ListenerEndpoint interface represents an endpoint used by an I/O reactor
 * to listen for incoming connection from remote clients.
 *
 * @since 4.0
 */
public interface ListenerEndpoint {

    /**
     * Returns the socket address of this endpoint.
     *
     * @return socket address.
     */
    SocketAddress getAddress();

    /**
     * Returns an instance of {@link IOException} thrown during initialization
     * of this endpoint or <code>null</code>, if initialization was successful.
     *
     * @return I/O exception object or <code>null</code>.
     */
    IOException getException();

    /**
     * Waits for completion of initialization process of this endpoint.
     *
     * @throws InterruptedException in case the initialization process was
     *   interrupted.
     */
    void waitFor() throws InterruptedException;

    /**
     * Determines if this endpoint has been closed and is no longer listens
     * for incoming connections.
     *
     * @return <code>true</code> if the endpoint has been closed,
     *   <code>false</code> otherwise.
     */
    boolean isClosed();

    /**
     * Closes this endpoint. The endpoint will stop accepting incoming
     * connection.
     */
    void close();

}
