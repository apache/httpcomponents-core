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

import java.net.SocketAddress;

import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.util.Timeout;

/**
 * Basic HTTP connection endpoint details.
 *
 * @since 5.0
 */
public final class BasicEndpointDetails extends EndpointDetails {

    private final HttpConnectionMetrics metrics;

    public BasicEndpointDetails(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final HttpConnectionMetrics metrics,
            final Timeout socketTimeout) {
        super(remoteAddress, localAddress, socketTimeout);
        this.metrics = metrics;
    }

    @Override
    public long getRequestCount() {
        return metrics != null ? metrics.getRequestCount() : 0;
    }

    @Override
    public long getResponseCount() {
        return metrics != null ? metrics.getResponseCount() : 0;
    }

    @Override
    public long getSentBytesCount() {
        return metrics != null ? metrics.getSentBytesCount() : 0;
    }

    @Override
    public long getReceivedBytesCount() {
        return metrics != null ? metrics.getReceivedBytesCount() : 0;
    }

}
