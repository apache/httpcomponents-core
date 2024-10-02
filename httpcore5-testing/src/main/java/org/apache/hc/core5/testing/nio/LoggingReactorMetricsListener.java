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

package org.apache.hc.core5.testing.nio;

import org.apache.hc.core5.reactor.IOReactorMetricsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingReactorMetricsListener implements IOReactorMetricsListener {

    public static final IOReactorMetricsListener INSTANCE = new LoggingReactorMetricsListener();

    private final Logger logger = LoggerFactory.getLogger("org.apache.hc.core5.http.pool");

    @Override
    public void onThreadPoolStatus(final int activeThreads, final int pendingConnections) {
        if (logger.isDebugEnabled()) {
            logger.debug("Active threads: {}, Pending connections: {}", activeThreads, pendingConnections);
        }
    }

    @Override
    public void onThreadPoolSaturation(final double saturationPercentage) {
        if (logger.isDebugEnabled()) {
            logger.debug("Thread pool saturation: {}%", saturationPercentage);
        }
    }

    @Override
    public void onResourceStarvationDetected() {
        if (logger.isDebugEnabled()) {
            logger.debug("Resource starvation detected!");
        }
    }

    @Override
    public void onQueueWaitTime(final long averageWaitTimeMillis) {
        if (logger.isDebugEnabled()) {
            logger.debug("Average queue wait time: {} ms", averageWaitTimeMillis);
        }
    }
}

