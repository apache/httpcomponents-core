/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.impl;

import java.util.HashMap;
import org.apache.http.HttpConnectionMetrics;

/**
 * Implementation of the metrics interface.
 */
public class HttpConnectionMetricsImpl implements HttpConnectionMetrics {
    
    public static final String REQUEST_COUNT = "http.request-count";
    public static final String RESPONSE_COUNT = "http.response-count";
    public static final String SENT_BYTES_COUNT = "http.sent-bytes-count";
    public static final String RECEIVED_BYTES_COUNT = "http.received-bytes-count";
    
    private long requestCount = 0;
    private long responseCount = 0;
    private long sentBytesCount = 0;
    private long receivedBytesCount = 0;
    
    /**
     * The cache map for all metrics values.
     */
    private HashMap metricsCache;
    
    public HttpConnectionMetricsImpl() {
        super();
    }
    
    /* ------------------  Public interface method -------------------------- */

    public long getRequestCount() {
        return requestCount;
    }
    
    public void setRequestCount(long count) {
        requestCount = count;
    }
    
    public void incrementRequestCount(long count) {
        requestCount += count;
    }
    
    public long getResponseCount() {
        return responseCount;
    }
    
    public void setResponseCount(long count) {
        responseCount = count;
    }
    
    public void incrementResponseCount(long count) {
        responseCount += count;
    }
    
    public long getSentBytesCount() {
        return sentBytesCount;
    }
    
    public void setSentBytesCount(long count) {
        sentBytesCount = count;
    }
    
    public void incrementSentBytesCount(long count) {
        sentBytesCount += count;
    }
    
    public long getReceivedBytesCount() {
        return receivedBytesCount;
    }
    
    public void setReceivedBytesCount(long count) {
        receivedBytesCount = count;
    }
    
    public void incrementReceivedBytesCount(long count) {
        receivedBytesCount += count;
    }
    
    public Object getMetric(final String metricName) {
        Object value = null;
        if (this.metricsCache != null) {
            value = this.metricsCache.get(metricName);
        }
        if (value == null) {
            if (REQUEST_COUNT.equals(metricName)) {
                value = new Long(requestCount);
            } else if (RESPONSE_COUNT.equals(metricName)) {
                value = new Long(responseCount);
            } else if (RECEIVED_BYTES_COUNT.equals(metricName)) {
                value = new Long(receivedBytesCount);
            } else if (SENT_BYTES_COUNT.equals(metricName)) {
                value = new Long(sentBytesCount);
            }
        }
        return value;
    }
    
    public void setMetric(final String metricName, Object obj) {
        if (this.metricsCache == null) {
            this.metricsCache = new HashMap();
        }
        this.metricsCache.put(metricName, obj);
    }
    
    public void reset() {
        requestCount = 0;
        responseCount = 0;
        sentBytesCount = 0;
        receivedBytesCount = 0;
        this.metricsCache = null;
    }
    
}
