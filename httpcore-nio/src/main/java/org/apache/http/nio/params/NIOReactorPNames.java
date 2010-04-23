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

package org.apache.http.nio.params;

/**
 * Parameter names for I/O reactors.
 *
 * @since 4.0
 */
public interface NIOReactorPNames {

    /**
     * Determines the size of the content input/output buffers used
     * to buffer data while receiving or transmitting HTTP messages.
     * <p>
     * This parameter expects a value of type {@link Integer}.
     * </p>
     */
    public static final String CONTENT_BUFFER_SIZE = "http.nio.content-buffer-size";

    /**
     * Determines the time interval in milliseconds at which the
     * I/O reactor wakes up to check for timed out sessions and session requests.
     * <p>
     * This parameter expects a value of type {@link Long}.
     * </p>
     */
    public static final String SELECT_INTERVAL = "http.nio.select-interval";

    /**
     * Determines the grace period the I/O reactors are expected to block
     * waiting for individual worker threads to terminate cleanly.
     * <p>
     * This parameter expects a value of type {@link Long}.
     * </p>
     */
    public static final String GRACE_PERIOD = "http.nio.grace-period";

    /**
     * Determines whether interestOps() queueing is enabled for the I/O reactors.
     * <p>
     * This parameter expects a value of type {@link Boolean}.
     * </p>
     *
     * @since 4.1
     */
    public static final String INTEREST_OPS_QUEUEING = "http.nio.interest-ops-queueing";

}
