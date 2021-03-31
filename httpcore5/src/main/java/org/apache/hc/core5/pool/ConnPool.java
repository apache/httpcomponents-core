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
package org.apache.hc.core5.pool;

import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;

/**
 * {@code ConnPool} represents a shared pool connections can be leased from
 * and released back to.
 *
 * @param <T> the route type that represents the opposite endpoint of a pooled
 *   connection.
 * @param <C> the type of pooled connections.
 * @since 4.2
 */
public interface ConnPool<T, C extends ModalCloseable> {

    /**
     * Attempts to lease a connection for the given route and with the given
     * state from the pool.
     * <p>
     * Please note the connection request can get automatically cancelled by the pool
     * in case of a request timeout.
     *
     * @param route route of the connection.
     * @param state arbitrary object that represents a particular state
     *  (usually a security principal or a unique token identifying
     *  the user whose credentials have been used while establishing the connection).
     *  May be {@code null}.
     * @param requestTimeout request timeout. In case of a timeout the request
     *                       can get automatically cancelled by the pool.
     * @param callback operation completion callback.
     *
     * @return future for a leased pool entry.
     */
    Future<PoolEntry<T, C>> lease(T route, Object state, Timeout requestTimeout, FutureCallback<PoolEntry<T, C>> callback);

    /**
     * Releases the pool entry back to the pool.
     *
     * @param entry pool entry leased from the pool
     * @param reusable flag indicating whether or not the released connection
     *   is in a consistent state and is safe for further use.
     */
    void release(PoolEntry<T, C> entry, boolean reusable);

}
