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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class CommandLineUtils {

    private CommandLineUtils() {
        // Do not allow utility class to be instantiated.
    }

    public static Options getOptions() {
        final Option nopt = new Option("n", true, "Number of requests to perform. " +
                "The default is to just perform a single request which may lead " +
                "to non-representative benchmarking results");
        nopt.setRequired(false);
        nopt.setArgName("requests");

        final Option copt = new Option("c", true, "Number of multiple requests to make at a time. " +
                "The default is to just execute a single request");
        copt.setRequired(false);
        copt.setArgName("concurrency");

        final Option topt = new Option("t", true, "Seconds to max. to spend on benchmarking");
        topt.setRequired(false);
        topt.setArgName("time-limit");

        final Option sopt = new Option("s", true, "Seconds to max. wait for each response. Default is 60 seconds");
        sopt.setRequired(false);
        sopt.setArgName("socket-Timeout");

        final Option popt = new Option("p", true, "File containing data to enclose in the request");
        popt.setRequired(false);
        popt.setArgName("Payload file");

        final Option Topt = new Option("T", true, "Content-type header to use for enclosed request data");
        Topt.setRequired(false);
        Topt.setArgName("content-type");

        final Option vopt = new Option("v", true, "Set verbosity level: " +
                "1 prints warnings and errors, " +
                "2 prints response codes, " +
                "3 prints message headers, " +
                "4 prints HTTP/2 frame info, " +
                "5 prints HTTP/2 flow control events, " +
                "6 prints response content");
        vopt.setRequired(false);
        vopt.setArgName("verbosity");

        final Option iopt = new Option("i", false, "Use HEAD instead of GET");
        iopt.setRequired(false);

        final Option Hopt = new Option("H", true, "Add arbitrary header line, " +
                "eg. 'Accept-Encoding: gzip' inserted after all normal " +
                "header lines. (repeatable as -H \"h1: v1\",\"h2: v2\" etc)");
        Hopt.setRequired(false);
        Hopt.setArgName("header");

        final Option kopt = new Option("k", false, "Use HTTP KeepAlive feature. Default is no KeepAlive");
        kopt.setRequired(false);

        final Option mopt = new Option("m", true, "HTTP Method. Default is GET or POST if the request to enclose data");
        mopt.setRequired(false);
        mopt.setArgName("HTTP method");

        // HttpCore specific options

        final Option uopt = new Option("u", false, "Chunk entity. Default is false");
        uopt.setRequired(false);

        final Option xopt = new Option("x", false, "Use Expect-Continue. Default is false");
        xopt.setRequired(false);

        final Option gopt = new Option("g", false, "Accept GZip. Default is false");
        gopt.setRequired(false);

        final Option http2opt = new Option("2", false, "Force HTTP/2");
        gopt.setRequired(false);

        final Option hopt = new Option("h", false, "Display usage information");
        nopt.setRequired(false);

        final Options options = new Options();
        options.addOption(nopt);
        options.addOption(copt);
        options.addOption(topt);
        options.addOption(sopt);
        options.addOption(popt);
        options.addOption(Topt);
        options.addOption(vopt);
        options.addOption(iopt);
        options.addOption(Hopt);
        options.addOption(kopt);
        options.addOption(mopt);

        // HttpCore specific options

        options.addOption(uopt);
        options.addOption(xopt);
        options.addOption(gopt);
        options.addOption(http2opt);

        options.addOption(hopt);
        return options;
    }

    public static BenchmarkConfig parseCommandLine(final CommandLine cmd) {
        final BenchmarkConfig.Builder builder = new BenchmarkConfig.Builder();
        if (cmd.hasOption('n')) {
            final String s = cmd.getOptionValue('n');
            try {
                builder.setRequests(Integer.parseInt(s));
            } catch (final NumberFormatException ex) {
                printError("Invalid number of requests: " + s);
            }
        }

        if (cmd.hasOption('c')) {
            final String s = cmd.getOptionValue('c');
            try {
                builder.setConcurrencyLevel(Integer.parseInt(s));
            } catch (final NumberFormatException ex) {
                printError("Invalid number for concurrency: " + s);
            }
        }

        if (cmd.hasOption('t')) {
            final String t = cmd.getOptionValue('t');
            try {
                builder.setTimeLimit(TimeValue.ofSeconds(Integer.parseInt(t)));
            } catch (final NumberFormatException ex) {
                printError("Invalid time limit: " + t);
            }
        }

        if (cmd.hasOption('s')) {
            final String s = cmd.getOptionValue('s');
            try {
                builder.setSocketTimeout(Timeout.ofMilliseconds(Integer.parseInt(s)));
            } catch (final NumberFormatException ex) {
                printError("Invalid socket timeout: " + s);
            }
        }

        if (cmd.hasOption('p')) {
            final File file = new File(cmd.getOptionValue('p'));
            if (!file.exists()) {
                printError("File not found: " + file);
            }
            builder.setPayloadFile(file);
        }

        if (cmd.hasOption('T')) {
            builder.setContentType(ContentType.parse(cmd.getOptionValue('T')));
        }

        if (cmd.hasOption('v')) {
            final String s = cmd.getOptionValue('v');
            try {
                builder.setVerbosity(Integer.parseInt(s));
            } catch (final NumberFormatException ex) {
                printError("Invalid verbosity level: " + s);
            }
        }

        if (cmd.hasOption('i')) {
            builder.setHeadInsteadOfGet(true);
        }

        if (cmd.hasOption('H')) {
            final String headerStr = cmd.getOptionValue('H');
            builder.setHeaders(headerStr.split(","));
        }

        if (cmd.hasOption('k')) {
            builder.setKeepAlive(true);
        }

        if (cmd.hasOption('m')) {
            builder.setMethod(cmd.getOptionValue('m'));
        } else if (cmd.hasOption('p')) {
            builder.setMethod(Method.POST.name());
        }

        if (cmd.hasOption('u')) {
            builder.setUseChunking(true);
        }

        if (cmd.hasOption('x')) {
            builder.setUseExpectContinue(true);
        }

        if (cmd.hasOption('g')) {
            builder.setUseAcceptGZip(true);
        }

        if (cmd.hasOption('2')) {
            builder.setForceHttp2(true);
        }

        final String[] cmdargs = cmd.getArgs();
        if (cmdargs.length > 0) {
            try {
                builder.setUri(new URI(cmdargs[0]));
            } catch (final URISyntaxException e) {
                printError("Invalid request URI: " + cmdargs[0]);
            }
        }

        return builder.build();
    }

    static void showUsage(final Options options) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("HttpBenchmark [options] [http://]hostname[:port]/path?query", options);
    }

    static void printError(final String msg) {
        System.err.println(msg);
        showUsage(getOptions());
        System.exit(-1);
    }
}
