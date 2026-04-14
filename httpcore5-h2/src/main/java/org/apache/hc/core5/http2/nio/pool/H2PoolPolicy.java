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
package org.apache.hc.core5.http2.nio.pool;

import org.apache.hc.core5.annotation.Experimental;

/**
 * Enumeration of HTTP/2 connection pool policies.
 *
 * @since 5.5
 */
@Experimental
public enum H2PoolPolicy {

    /**
     * Simple session pool with one logical session per endpoint.
     * Multiplexing is handled entirely by the HTTP/2 multiplexer;
     * the pool itself has no awareness of individual streams or
     * peer {@code MAX_CONCURRENT_STREAMS} limits. This is the
     * default policy.
     */
    BASIC,

    /**
     * Stream-capacity-aware pool that manages multiple connections
     * per endpoint. The pool tracks active and reserved streams,
     * honours the peer's {@code MAX_CONCURRENT_STREAMS}, opens
     * additional connections when existing ones are saturated,
     * and drains connections gracefully on {@code GOAWAY}.
     */
    MULTIPLEXING

}
