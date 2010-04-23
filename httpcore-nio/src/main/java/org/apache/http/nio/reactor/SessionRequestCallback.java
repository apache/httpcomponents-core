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

/**
 * SessionRequestCallback interface can be used to get notifications of
 * completion of session requests asynchronously without having to wait
 * for it, blocking the current thread of execution.
 *
 * @since 4.0
 */
public interface SessionRequestCallback {

    /**
     * Triggered on successful completion of a {@link SessionRequest}.
     * The {@link SessionRequest#getSession()} method can now be used to obtain
     * the new I/O session.
     *
     * @param request session request.
     */
    void completed(SessionRequest request);

    /**
     * Triggered on unsuccessful completion a {@link SessionRequest}.
     * The {@link SessionRequest#getException()} method can now be used to
     * obtain the cause of the error.
     *
     * @param request session request.
     */
    void failed(SessionRequest request);

    /**
     * Triggered if a {@link SessionRequest} times out.
     *
     * @param request session request.
     */
    void timeout(SessionRequest request);

    /**
     * Triggered on cancellation of a {@link SessionRequest}.
     *
     * @param request session request.
     */
    void cancelled(SessionRequest request);

}
