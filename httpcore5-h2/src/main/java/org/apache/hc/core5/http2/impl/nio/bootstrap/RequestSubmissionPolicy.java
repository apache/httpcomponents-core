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

package org.apache.hc.core5.http2.impl.nio.bootstrap;

/**
 * Controls how an {@link H2MultiplexingRequester} behaves when it reaches a configured
 * per-connection limit for active / pending request executions (for example when a connection
 * has reached a cap such as {@code maxRequestsPerConnection}).
 * <p>
 * In {@link #REJECT} mode, submissions that exceed the configured cap are failed immediately
 * (typically with {@link java.util.concurrent.RejectedExecutionException}).
 * <p>
 * In {@link #QUEUE} mode, submissions that exceed the configured cap are queued and will be
 * executed later when the number of active requests drops below the cap. This mode improves
 * throughput in bursty workloads at the cost of potentially increased latency for queued
 * requests.
 * <p>
 * <b>Important:</b> QUEUE mode may retain queued requests in memory until capacity becomes
 * available. Applications should ensure reasonable upper bounds on concurrency and use timeouts.
 * A bounded queue option may be introduced in the future.
 *
 * @since 5.5
 */
public enum RequestSubmissionPolicy {

    /**
     * Reject submissions that would exceed the configured per-connection cap.
     * <p>
     * This mode provides fast failure and avoids unbounded memory usage from queued requests.
     *
     * @since 5.5
     */
    REJECT,

    /**
     * Queue submissions that would exceed the configured per-connection cap and execute them
     * when capacity becomes available.
     * <p>
     * This mode avoids {@code RejectedExecutionException} under bursts, but queued requests may
     * experience additional latency.
     *
     * @since 5.5
     */
    QUEUE

}
