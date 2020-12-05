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

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Locale;

public class ResultFormatter {

    private ResultFormatter() {
        // Do not allow utility class to be instantiated.
    }

    static final NumberFormat nf2 = NumberFormat.getInstance(Locale.ROOT);
    static final NumberFormat nf3 = NumberFormat.getInstance(Locale.ROOT);
    static final NumberFormat nf6 = NumberFormat.getInstance(Locale.ROOT);

    static {
        nf2.setMaximumFractionDigits(2);
        nf2.setMinimumFractionDigits(2);
        nf3.setMaximumFractionDigits(3);
        nf3.setMinimumFractionDigits(3);
        nf6.setMaximumFractionDigits(6);
        nf6.setMinimumFractionDigits(6);
    }

    public static void print(final PrintStream printStream, final Results results) {
        printStream.println("Server Software:\t\t" + results.getServerName());
        printStream.println("Protocol version:\t\t" + results.getProtocolVersion());
        printStream.println("Server Hostname:\t\t" + results.getHostName());
        printStream.println("Server Port:\t\t\t" + results.getHostPort());
        printStream.println("Document Path:\t\t\t" + results.getDocumentPath());
        printStream.println("Document Length:\t\t" + results.getContentLength() + " bytes\n");
        printStream.println("Concurrency Level:\t\t" + results.getConcurrencyLevel());
        printStream.println("Time taken for tests:\t" + nf6.format((double) results.getTotalTimeMillis() / 1000) + " seconds");
        printStream.println("Complete requests:\t\t" + results.getSuccessCount());
        printStream.println("Failed requests:\t\t" + results.getFailureCount());
        printStream.println("Kept alive:\t\t\t\t" + results.getKeepAliveCount());
        printStream.println("Total transferred:\t\t" + results.getTotalBytesRcvd() + " bytes");
        printStream.println("Content transferred:\t" + results.getTotalContentBytesRecvd() + " bytes");
        printStream.println("Requests per second:\t" + nf2.format(
                results.getSuccessCount() / ((double) results.getTotalTimeMillis() / 1000)) + " [#/sec] (mean)");
        printStream.println("Time per request:\t\t" + nf3.format(
                (double) results.getTotalTimeMillis() * results.getConcurrencyLevel() / results.getSuccessCount()) + " [ms] (mean)");
        printStream.println("Time per request:\t\t" + nf3.format(
                (double) results.getTotalTimeMillis() / results.getSuccessCount()) + " [ms] (mean, across all concurrent requests)");
        printStream.println("Transfer rate:\t\t\t" +
            nf2.format((double) results.getTotalBytesRcvd() / 1024 / ((double) results.getTotalTimeMillis() / 1000)) + " [Kbytes/sec] received");
    }

}
