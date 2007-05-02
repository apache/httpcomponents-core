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
package org.apache.http.contrib.benchmark;

import org.apache.http.HttpHost;

import java.text.NumberFormat;

public class ResultProcessor {

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

    static void printResults(BenchmarkWorker[] workers, HttpHost host,
        String uri, long contentLength) {

        double totalTimeNano = 0;
        int successCount     = 0;
        int failureCount     = 0;
        int writeErrors      = 0;
        long totalBytesRcvd  = 0;

        Stats stats = workers[0].getStats();

        for (int i = 0; i < workers.length; i++) {
            Stats s = workers[i].getStats();
            totalTimeNano  += s.getDuration();
            successCount   += s.getSuccessCount();
            failureCount   += s.getFailureCount();
            writeErrors    += s.getWriteErrors();
            totalBytesRcvd += s.getTotalBytesRecv();
        }

        int threads = workers.length;
        double totalTimeMs  = (totalTimeNano / threads) / 1000000; // convert nano secs to milli secs
        double timePerReqMs = totalTimeMs / successCount;
        double totalTimeSec = totalTimeMs / 1000;
        double reqsPerSec   = successCount / totalTimeSec;
        long totalBytesSent = contentLength * successCount;
        long totalBytes     = totalBytesRcvd + (totalBytesSent > 0 ? totalBytesSent : 0);

        System.out.println("\nServer Software:\t\t" + stats.getServerName());
        System.out.println("Server Hostname:\t\t" + host.getHostName());
        System.out.println("Server Port:\t\t\t" +
            (host.getPort() > 0 ? host.getPort() : uri.startsWith("https") ? "443" : "80") + "\n");
        System.out.println("Document Path:\t\t\t" + uri);
        System.out.println("Document Length:\t\t" + stats.getContentLength() + " bytes\n");
        System.out.println("Concurrency Level:\t\t" + workers.length);
        System.out.println("Time taken for tests:\t\t" + nf6.format(totalTimeSec) + " seconds");
        System.out.println("Complete requests:\t\t" + successCount);
        System.out.println("Failed requests:\t\t" + failureCount);
        System.out.println("Write errors:\t\t\t" + writeErrors);
        System.out.println("Total transferred:\t\t" + totalBytes + " bytes");
        System.out.println("Requests per second:\t\t" + nf2.format(reqsPerSec) + " [#/sec] (mean)");
        System.out.println("Time per request:\t\t" + nf3.format(timePerReqMs * workers.length) + " [ms] (mean)");
        System.out.println("Time per request:\t\t" + nf3.format(timePerReqMs) +
            " [ms] (mean, across all concurrent requests)");
        System.out.println("Transfer rate:\t\t\t" +
            nf2.format(totalBytesRcvd/1000/totalTimeSec) + " [Kbytes/sec] received");
        System.out.println("\t\t\t\t" +
            (totalBytesSent > 0 ? nf2.format(totalBytesSent/1000/totalTimeSec) : -1) + " kb/s sent");
        System.out.println("\t\t\t\t" +
            nf2.format(totalBytes/1000/totalTimeSec) + " kb/s total");
    }
}
