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
 * SessionRequest interface represents a request to establish a new connection
 * (or session) to a remote host. It can be used to monitor the status of the
 * request, to block waiting for its completion, or to cancel the request.
 * <p>
 * Implementations of this interface are expected to be threading safe.
 *
 * @since 4.0
 */
public interface SessionRequest {

    /**
     * Returns socket address of the remote host.
     *
     * @return socket address of the remote host
     */
    SocketAddress getRemoteAddress();

    /**
     * Returns local socket address.
     *
     * @return local socket address.
     */
    SocketAddress getLocalAddress();

    /**
     * Returns attachment object will be added to the session's context upon
     * initialization. This object can be used to pass an initial processing
     * state to the protocol handler.
     *
     * @return attachment object.
     */
    Object getAttachment();

    /**
     * Determines whether the request has been completed (either successfully
     * or unsuccessfully).
     *
     * @return <code>true</true> if the request has been completed,
     *  <code>false</true> if still pending.
     */
    boolean isCompleted();

    /**
     * Returns {@link IOSession} instance created as a result of this request
     * or <code>null</code> if the request is still pending.
     *
     * @return I/O session or <code>null</code> if the request is still pending.
     */
    IOSession getSession();

    /**
     * Returns {@link IOException} instance if the request could not be
     * successfully executed due to an I/O error or <code>null</code> if no
     * error occurred to this point.
     *
     * @return I/O exception or <code>null</code> if no error occurred to
     * this point.
     */
    IOException getException();

    /**
     * Waits for completion of this session request.
     *
     * @throws InterruptedException in case the execution process was
     *   interrupted.
     */
    void waitFor() throws InterruptedException;

    /**
     * Sets connect timeout value in milliseconds.
     *
     * @param timeout connect timeout value in milliseconds.
     */
    void setConnectTimeout(int timeout);

    /**
     * Returns connect timeout value in milliseconds.
     *
     * @return connect timeout value in milliseconds.
     */
    int getConnectTimeout();

    /**
     * Cancels the request. Invocation of this method will set the status of
     * the request to completed and will unblock threads blocked in
     * the {{@link #waitFor()}} method.
     */
    void cancel();

}
