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

import java.util.Set;

import org.apache.hc.core5.util.TimeValue;

/**
 * Interface to control runtime properties of a {@link ConnPool} such as
 * maximum total number of connections or maximum connections per route
 * allowed.
 *
 * @param <T> the route type that represents the opposite endpoint of a pooled
 *   connection.
 * @since 4.2
 */
public interface ConnPoolControl<T> extends ConnPoolStats<T> {

    /**
     * <p>Set the maximum number of allowed connections.
     * @param max the maximum number of allowed connections.
     */
    void setMaxTotal(int max);

    /**
     * Gets the maximum number of allowed connections.
     *
     * @return the maximum number of allowed connections.
     */
    int getMaxTotal();

    /**
     * Set The maximum number of connections allowed across all routes.
     * @param max The maximum number of connections allowed across all routes.
     */
    void setDefaultMaxPerRoute(int max);

    /**
     * Get The maximum number of connections allowed across all routes.
     * @return The maximum number of connections allowed across all routes.
     */
    int getDefaultMaxPerRoute();

    /**
     * Set The maximum number of connections allowed per route.
     * @param route the http route
     * @param max The maximum number of connections allowed per route.
     */
    void setMaxPerRoute(final T route, int max);

    /**
     * Get The maximum number of connections allowed per route.
     * @return the route with maximum number of connections allowed per route.
     */
    int getMaxPerRoute(final T route);

    /**
     * Closes connections that have been idle longer than the given period of time them from the pool.
     * @param idleTime the period of idle time.
     */
    void closeIdle(TimeValue idleTime);

    /**
     * Closes expired connections from the pool.
     */
    void closeExpired();

    /**
     * Returns all knows routes.
     */
    Set<T> getRoutes();

}
