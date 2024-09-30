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

/**
 * A listener interface for receiving metrics related to the I/O reactor's
 * thread pool performance and status.
 *
 * <p>The implementing class can monitor and act upon important metrics such as
 * active threads, saturation levels, and resource starvation.</p>
 *
 * @since 5.4
 */
public interface IOReactorMetricsListener {

    /**
     * Invoked to report the current status of the thread pool, including
     * active threads and pending connections.
     *
     * @param activeThreads      The number of active threads handling connections.
     * @param pendingConnections The number of pending connection requests in the queue.
     */
    void onThreadPoolStatus(int activeThreads, int pendingConnections);

    /**
     * Invoked to report the saturation level of the thread pool as a percentage
     * of the active threads to the maximum allowed connections.
     *
     * @param saturationPercentage The percentage indicating thread pool saturation.
     */
    void onThreadPoolSaturation(double saturationPercentage);

    /**
     * Invoked when the number of pending connection requests exceeds the
     * maximum allowed connections, indicating possible resource starvation.
     */
    void onResourceStarvationDetected();

    /**
     * Notifies about the average wait time for connection requests in the queue.
     *
     * @param averageWaitTimeMillis average time in milliseconds that connection requests spend in the queue.
     */
    void onQueueWaitTime(long averageWaitTimeMillis);
}

