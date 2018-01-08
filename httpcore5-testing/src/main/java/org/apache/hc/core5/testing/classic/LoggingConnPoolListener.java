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

package org.apache.hc.core5.testing.classic;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolStats;
import org.apache.hc.core5.pool.PoolStats;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class LoggingConnPoolListener implements ConnPoolListener<HttpHost> {

    public final static LoggingConnPoolListener INSTANCE = new LoggingConnPoolListener();

    private final Logger connLog = LoggerFactory.getLogger("org.apache.hc.core5.http.connection");

    private LoggingConnPoolListener() {
    }

    @Override
    public void onLease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
        if (connLog.isDebugEnabled()) {
            final StringBuilder buf = new StringBuilder();
            buf.append("Leased ").append(route).append(" ");
            final PoolStats totals = connPoolStats.getTotalStats();
            buf.append(" total kept alive: ").append(totals.getAvailable()).append("; ");
            buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
            buf.append(" of ").append(totals.getMax());
            connLog.debug(buf.toString());
        }
    }

    @Override
    public void onRelease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
        if (connLog.isDebugEnabled()) {
            final StringBuilder buf = new StringBuilder();
            buf.append("Released ").append(route).append(" ");
            final PoolStats totals = connPoolStats.getTotalStats();
            buf.append(" total kept alive: ").append(totals.getAvailable()).append("; ");
            buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
            buf.append(" of ").append(totals.getMax());
            connLog.debug(buf.toString());
        }
    }

}
