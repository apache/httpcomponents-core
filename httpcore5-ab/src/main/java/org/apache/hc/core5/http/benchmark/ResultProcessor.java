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
package org.apache.hc.core5.http.benchmark;

import java.text.NumberFormat;

import org.apache.hc.core5.http.HttpHost;

public class ResultProcessor {

    private ResultProcessor() {
        // Do not allow utility class to be instantiated.
    }

    static NumberFormat nf2 = NumberFormat.getInstance();
    static NumberFormat nf3 = NumberFormat.getInstance();
    static NumberFormat nf6 = NumberFormat.getInstance();

    static {
        nf2.setMaximumFractionDigits(2);
        nf2.setMinimumFractionDigits(2);
        nf3.setMaximumFractionDigits(3);
        nf3.setMinimumFractionDigits(3);
        nf6.setMaximumFractionDigits(6);
        nf6.setMinimumFractionDigits(6);
    }

    static Results collectResults(final BenchmarkWorker[] workers, final HttpHost host, final String uri) {
        long totalTimeNano = 0;
        long successCount    = 0;
        long failureCount    = 0;
        long writeErrors     = 0;
        long keepAliveCount  = 0;
        long totalBytesRcvd  = 0;
        long totalBytesSent  = 0;

        final Stats stats = workers[0].getStats();

        for (final BenchmarkWorker worker : workers) {
            final Stats s = worker.getStats();
            totalTimeNano  += s.getDuration();
            successCount   += s.getSuccessCount();
            failureCount   += s.getFailureCount();
            writeErrors    += s.getWriteErrors();
            keepAliveCount += s.getKeepAliveCount();
            totalBytesRcvd += s.getTotalBytesRecv();
            totalBytesSent += s.getTotalBytesSent();
        }

        final Results results = new Results();
        results.serverName = stats.getServerName();
        results.hostName = host.getHostName();
        results.hostPort = host.getPort() > 0 ? host.getPort() :
            host.getSchemeName().equalsIgnoreCase("https") ? 443 : 80;
        results.documentPath = uri;
        results.contentLength = stats.getContentLength();
        results.concurrencyLevel = workers.length;
        results.totalTimeNano = totalTimeNano;
        results.successCount = successCount;
        results.failureCount = failureCount;
        results.writeErrors = writeErrors;
        results.keepAliveCount = keepAliveCount;
        results.totalBytesRcvd = totalBytesRcvd;
        results.totalBytesSent = totalBytesSent;
        results.totalBytes = totalBytesRcvd + (totalBytesSent > 0 ? totalBytesSent : 0);
        return results;
    }

    static void printResults(final Results results) {
        final int threads = results.getConcurrencyLevel();
        final double totalTimeMs  = (results.getTotalTimeNano() / threads) / 1000000; // convert nano secs to milli secs
        final double timePerReqMs = totalTimeMs / results.getSuccessCount();
        final double totalTimeSec = totalTimeMs / 1000;
        final double reqsPerSec   = results.getSuccessCount() / totalTimeSec;

        System.out.println("\nServer Software:\t\t" + results.getServerName());
        System.out.println( "Server Hostname:\t\t" + results.getHostName());
        System.out.println( "Server Port:\t\t\t" + Integer.valueOf(results.getHostPort()));
        System.out.println( "Document Path:\t\t\t" + results.getDocumentPath());
        System.out.println( "Document Length:\t\t" + results.getContentLength() + " bytes\n");
        System.out.println( "Concurrency Level:\t\t" + results.getConcurrencyLevel());
        System.out.println( "Time taken for tests:\t\t" + nf6.format(totalTimeSec) + " seconds");
        System.out.println( "Complete requests:\t\t" + results.getSuccessCount());
        System.out.println( "Failed requests:\t\t" + results.getFailureCount());
        System.out.println( "Write errors:\t\t\t" + results.getWriteErrors());
        System.out.println( "Kept alive:\t\t\t" + results.getKeepAliveCount());
        System.out.println( "Total transferred:\t\t" + results.getTotalBytes() + " bytes");
        System.out.println( "Requests per second:\t\t" + nf2.format(reqsPerSec) + " [#/sec] (mean)");
        System.out.println( "Time per request:\t\t" + nf3.format(timePerReqMs
                * results.getConcurrencyLevel()) + " [ms] (mean)");
        System.out.println( "Time per request:\t\t" + nf3.format(timePerReqMs) +
            " [ms] (mean, across all concurrent requests)");
        System.out.println( "Transfer rate:\t\t\t" +
            nf2.format(results.getTotalBytesRcvd() / 1000 / totalTimeSec) + " [Kbytes/sec] received");
        System.out.println( "\t\t\t\t" +
            (results.getTotalBytesSent() > 0 ? nf2.format(results.getTotalBytesSent()
                    / 1000 / totalTimeSec) : Integer.valueOf(-1)) + " kb/s sent");
        System.out.println( "\t\t\t\t" +
            nf2.format(results.getTotalBytes() / 1000 / totalTimeSec) + " kb/s total");
    }

}
