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

package org.apache.hc.core5.http.impl;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.http.io.HttpTransportMetrics;

/**
 * Default implementation of the {@link HttpConnectionMetrics} interface.
 *
 * @since 4.0
 */
public final class BasicHttpConnectionMetrics implements HttpConnectionMetrics {

    private final HttpTransportMetrics inTransportMetric;
    private final HttpTransportMetrics outTransportMetric;
    private final AtomicLong requestCount;
    private final AtomicLong responseCount;

    public BasicHttpConnectionMetrics(
            final HttpTransportMetrics inTransportMetric,
            final HttpTransportMetrics outTransportMetric) {
        super();
        this.inTransportMetric = inTransportMetric;
        this.outTransportMetric = outTransportMetric;
        this.requestCount = new AtomicLong(0);
        this.responseCount = new AtomicLong(0);
    }

    @Override
    public long getReceivedBytesCount() {
        if (this.inTransportMetric != null) {
            return this.inTransportMetric.getBytesTransferred();
        }
        return -1;
    }

    @Override
    public long getSentBytesCount() {
        if (this.outTransportMetric != null) {
            return this.outTransportMetric.getBytesTransferred();
        }
        return -1;
    }

    @Override
    public long getRequestCount() {
        return this.requestCount.get();
    }

    public void incrementRequestCount() {
        this.requestCount.incrementAndGet();
    }

    @Override
    public long getResponseCount() {
        return this.responseCount.get();
    }

    public void incrementResponseCount() {
        this.responseCount.incrementAndGet();
    }

}
