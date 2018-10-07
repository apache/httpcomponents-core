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

package org.apache.hc.core5.http;

import java.net.SocketAddress;

import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP connection endpoint details.
 *
 * @since 5.0
 */
public abstract class EndpointDetails implements HttpConnectionMetrics {

    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;
    private final Timeout socketTimeout;

    protected EndpointDetails(final SocketAddress remoteAddress, final SocketAddress localAddress, final Timeout socketTimeout) {
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.socketTimeout = socketTimeout;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * Gets the number of requests transferred over the connection,
     * 0 if not available.
     */
    @Override
    public abstract long getRequestCount();

    /**
     * Gets the number of responses transferred over the connection,
     * 0 if not available.
     */
    @Override
    public abstract long getResponseCount();

    /**
     * Gets the number of bytes transferred over the connection,
     * 0 if not available.
     */
    @Override
    public abstract long getSentBytesCount();

    /**
     * Gets the number of bytes transferred over the connection,
     * 0 if not available.
     */
    @Override
    public abstract long getReceivedBytesCount();

    /**
     * Gets the socket timeout.
     *
     * @return the socket timeout.
     */
    public Timeout getSocketTimeout() {
        return socketTimeout;
    }

    @Override
    public String toString() {
        // Make enough room for two IPv6 addresses to avoid re-allocation in the StringBuilder.
        final StringBuilder buffer = new StringBuilder(90);
        InetAddressUtils.formatAddress(buffer, localAddress);
        buffer.append("<->");
        InetAddressUtils.formatAddress(buffer, remoteAddress);
        return buffer.toString();
    }

}
