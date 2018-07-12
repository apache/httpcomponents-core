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

package org.apache.hc.core5.reactor;

import java.net.SocketAddress;

import org.apache.hc.core5.io.ModalCloseable;

/**
 * ListenerEndpoint interface represents an endpoint used by an I/O reactor
 * to listen for incoming connection from remote clients.
 *
 * @since 4.0
 */
public interface ListenerEndpoint extends ModalCloseable {

    /**
     * Returns the socket address of this endpoint.
     *
     * @return socket address.
     */
    SocketAddress getAddress();

    /**
     * Determines if this endpoint has been closed and is no longer listens
     * for incoming connections.
     *
     * @return {@code true} if the endpoint has been closed,
     *   {@code false} otherwise.
     */
    boolean isClosed();

}
