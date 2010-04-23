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
 * IOEventDispatch interface is used by I/O reactors to notify clients of I/O
 * events pending for a particular session. All methods of this interface are
 * executed on a dispatch thread of the I/O reactor. Therefore, it is important
 * that processing that takes place in the event methods will not block the
 * dispatch thread for too long, as the I/O reactor will be unable to react to
 * other events.
 *
 * @since 4.0
 */
public interface IOEventDispatch {

    /**
     * Triggered after the given session has been just created.
     *
     * @param session the I/O session.
     */
    void connected(IOSession session);

    /**
     * Triggered when the given session has input pending.
     *
     * @param session the I/O session.
     */
    void inputReady(IOSession session);

    /**
     * Triggered when the given session is ready for output.
     *
     * @param session the I/O session.
     */
    void outputReady(IOSession session);

    /**
     * Triggered when the given session as timed out.
     *
     * @param session the I/O session.
     */
    void timeout(IOSession session);

    /**
     * Triggered when the given session has been terminated.
     *
     * @param session the I/O session.
     */
    void disconnected(IOSession session);

}
