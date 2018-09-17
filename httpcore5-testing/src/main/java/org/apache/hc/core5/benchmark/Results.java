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
package org.apache.hc.core5.benchmark;

import org.apache.hc.core5.http.ProtocolVersion;

/**
 * Benchmark results
 *
 * @since 4.3
 */
public final class Results {

    private final String serverName;
    private final ProtocolVersion protocolVersion;
    private final String hostName;
    private final int hostPort;
    private final String documentPath;
    private final long contentLength;
    private final int concurrencyLevel;
    private final long totalTimeMillis;
    private final long successCount;
    private final long failureCount;
    private final long keepAliveCount;
    private final long totalBytesRcvd;
    private final long totalBytesSent;
    private final long totalContentBytesRecvd;

    public Results(
            final String serverName,
            final ProtocolVersion protocolVersion,
            final String hostName,
            final int hostPort,
            final String documentPath,
            final long contentLength,
            final int concurrencyLevel,
            final long totalTimeMillis,
            final long successCount,
            final long failureCount,
            final long keepAliveCount,
            final long totalBytesRcvd,
            final long totalBytesSent,
            final long totalContentBytesRecvd) {
        this.serverName = serverName;
        this.protocolVersion = protocolVersion;
        this.hostName = hostName;
        this.hostPort = hostPort;
        this.documentPath = documentPath;
        this.contentLength = contentLength;
        this.concurrencyLevel = concurrencyLevel;
        this.totalTimeMillis = totalTimeMillis;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.keepAliveCount = keepAliveCount;
        this.totalBytesRcvd = totalBytesRcvd;
        this.totalBytesSent = totalBytesSent;
        this.totalContentBytesRecvd = totalContentBytesRecvd;
    }

    public String getServerName() {
        return serverName;
    }

    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public String getHostName() {
        return hostName;
    }

    public int getHostPort() {
        return hostPort;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public long getContentLength() {
        return contentLength;
    }

    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    public long getTotalTimeMillis() {
        return totalTimeMillis;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public long getKeepAliveCount() {
        return keepAliveCount;
    }

    public long getTotalBytesRcvd() {
        return totalBytesRcvd;
    }

    public long getTotalBytesSent() {
        return totalBytesSent;
    }

    public long getTotalContentBytesRecvd() {
        return totalContentBytesRecvd;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[serverName=").append(serverName)
                .append(", hostName=").append(hostName)
                .append(", hostPort=").append(hostPort)
                .append(", documentPath=").append(documentPath)
                .append(", contentLength=").append(contentLength)
                .append(", concurrencyLevel=").append(concurrencyLevel)
                .append(", totalTimeMillis=").append(totalTimeMillis)
                .append(", successCount=").append(successCount)
                .append(", failureCount=").append(failureCount)
                .append(", keepAliveCount=").append(keepAliveCount)
                .append(", totalBytesRcvd=").append(totalBytesRcvd)
                .append(", totalBytesSent=").append(totalBytesSent)
                .append(", totalContentBytesRecvd=").append(totalContentBytesRecvd)
                .append("]");
        return builder.toString();
    }

}
