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

import java.io.IOException;

/**
 * Connection input/output control interface. It can be used to control interest
 * in I/O event notifications for non-blocking HTTP connections.
 * <p>
 * Implementations of this interface are expected to be threading safe.
 * Therefore it can be used to request / suspend I/O event notifications from
 * any thread of execution.
 *
 * @since 4.0
 */
public interface IOControl {

    /**
     * Requests event notifications to be triggered when the underlying
     * channel is ready for input operations.
     */
    void requestInput();

    /**
     * Suspends event notifications about the underlying channel being
     * ready for input operations.
     */
    void suspendInput();

    /**
     * Requests event notifications to be triggered when the underlying
     * channel is ready for output operations.
     */
    void requestOutput();

    /**
     * Suspends event notifications about the underlying channel being
     * ready for output operations.
     */
    void suspendOutput();

    /**
     * Shuts down the underlying channel.
     *
     * @throws IOException
     */
    void shutdown() throws IOException;

}
