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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;

/**
 * Non-blocking connection acceptor.
 *
 * @since 5.0
 */
public interface ConnectionAcceptor {

    /**
     * Opens a new listener endpoint with the given socket address. Once
     * the endpoint is fully initialized it starts accepting incoming
     * connections and propagates I/O activity notifications to the I/O event
     * dispatcher.
     *
     * @param address the socket address to listen on.
     * @param attachment the attachment object.
     * @param callback the result callback.
     * @return listener endpoint.
     *
     * @since 5.2
     */
    default Future<ListenerEndpoint> listen(
            SocketAddress address, Object attachment, FutureCallback<ListenerEndpoint> callback) {
        return listen(address, callback);
    }

    /**
     * Opens a new listener endpoint with the given socket address. Once
     * the endpoint is fully initialized it starts accepting incoming
     * connections and propagates I/O activity notifications to the I/O event
     * dispatcher.
     *
     * @param address the socket address to listen on.
     * @param callback the result callback.
     * @return listener endpoint.
     */
    Future<ListenerEndpoint> listen(SocketAddress address, FutureCallback<ListenerEndpoint> callback);

    /**
     * Suspends the I/O reactor preventing it from accepting new connections on
     * all active endpoints.
     *
     * @throws IOException in case of an I/O error.
     */
    void pause() throws IOException;

    /**
     * Resumes the I/O reactor restoring its ability to accept incoming
     * connections on all active endpoints.
     *
     * @throws IOException in case of an I/O error.
     */
    void resume() throws IOException;

    /**
     * Returns a set of endpoints for this I/O reactor.
     *
     * @return set of endpoints.
     */
    Set<ListenerEndpoint> getEndpoints();

}
