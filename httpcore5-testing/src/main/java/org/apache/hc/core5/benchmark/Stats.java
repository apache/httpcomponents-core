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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ProtocolVersion;

/**
 * Statistics for an {@link HttpBenchmark HttpBenchmark}.
 *
 * @since 4.0
 */
public class Stats {

    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicInteger keepAliveCount = new AtomicInteger();
    private final AtomicLong totalBytesRecv = new AtomicLong();
    private final AtomicLong totalBytesSent = new AtomicLong();
    private final AtomicLong contentLength = new AtomicLong();
    private final AtomicLong totalContentLength = new AtomicLong();
    private final AtomicReference<String> serverNameRef = new AtomicReference<>();
    private final AtomicReference<ProtocolVersion> versionRef = new AtomicReference<>();

    public void incSuccessCount() {
        this.successCount.incrementAndGet();
    }

    public int getSuccessCount() {
        return this.successCount.get();
    }

    public void incFailureCount() {
        this.failureCount.incrementAndGet();
    }

    public int getFailureCount() {
        return this.failureCount.get();
    }

    public void incKeepAliveCount() {
        this.keepAliveCount.incrementAndGet();
    }

    public int getKeepAliveCount() {
        return this.keepAliveCount.get();
    }

    public void incTotalBytesRecv(final int n) {
        this.totalBytesRecv.addAndGet(n);
    }

    public long getTotalBytesRecv() {
        return this.totalBytesRecv.get();
    }

    public void incTotalBytesSent(final int n) {
        this.totalBytesSent.addAndGet(n);
    }

    public long getTotalBytesSent() {
        return this.totalBytesSent.get();
    }

    public void setContentLength(final long n) {
        this.contentLength.set(n);
    }

    public void incTotalContentLength(final long n) {
        this.totalContentLength.addAndGet(n);
    }

    public long getContentLength() {
        return this.contentLength.get();
    }

    public long getTotalContentLength() {
        return this.totalContentLength.get();
    }

    public void setServerName(final String serverName) {
        this.serverNameRef.set(serverName);
    }

    public String getServerName() {
        return this.serverNameRef.get();
    }

    public ProtocolVersion getVersion() {
        return versionRef.get();
    }

    public void setVersion(final ProtocolVersion version) {
        this.versionRef.set(version);
    }

    @Override
    public String toString() {
        return "Stats{" +
                "successCount=" + successCount +
                ", failureCount=" + failureCount +
                ", keepAliveCount=" + keepAliveCount +
                ", serverName=" + serverNameRef.get() +
                ", totalBytesRecv=" + totalBytesRecv +
                ", totalBytesSent=" + totalBytesSent +
                ", contentLength=" + contentLength +
                '}';
    }

}
