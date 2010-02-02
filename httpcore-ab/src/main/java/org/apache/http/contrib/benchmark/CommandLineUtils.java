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
package org.apache.http.contrib.benchmark;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class CommandLineUtils {
    
    public static Options getOptions() {
        Option iopt = new Option("i", false, "Do HEAD requests instead of GET.");
        iopt.setRequired(false);

        Option oopt = new Option("o", false, "Use HTTP/S 1.0");
        oopt.setRequired(false);

        Option kopt = new Option("k", false, "Enable the HTTP KeepAlive feature, " +
            "i.e., perform multiple requests within one HTTP session. " +
            "Default is no KeepAlive");
        kopt.setRequired(false);

        Option nopt = new Option("n", true, "Number of requests to perform for the " +
            "benchmarking session. The default is to just perform a single " +
            "request which usually leads to non-representative benchmarking " +
            "results.");
        nopt.setRequired(false);
        nopt.setArgName("requests");

        Option copt = new Option("c", true, "Concurrency while performing the " +
            "benchmarking session. The default is to just use a single thread/client.");
        copt.setRequired(false);
        copt.setArgName("concurrency");

        Option popt = new Option("p", true, "File containing data to POST.");
        popt.setRequired(false);
        popt.setArgName("POST-postFile");

        Option Topt = new Option("T", true, "Content-type header to use for POST data.");
        Topt.setRequired(false);
        Topt.setArgName("content-type");

        Option topt = new Option("t", true, "Client side socket timeout (in ms) - default 60 Secs");
        topt.setRequired(false);
        topt.setArgName("socket-Timeout");

        Option Hopt = new Option("H", true, "Add arbitrary header line, " +
            "eg. 'Accept-Encoding: gzip' inserted after all normal " +
            "header lines. (repeatable as -H \"h1: v1\",\"h2: v2\" etc)");
        Hopt.setRequired(false);
        Hopt.setArgName("header");

        Option vopt = new Option("v", true, "Set verbosity level - 4 and above " +
            "prints response content, 3 and above prints " +
            "information on headers, 2 and above prints response codes (404, 200, " +
            "etc.), 1 and above prints warnings and info.");
        vopt.setRequired(false);
        vopt.setArgName("verbosity");

        Option hopt = new Option("h", false, "Display usage information.");
        nopt.setRequired(false);

        Options options = new Options();
        options.addOption(iopt);
        options.addOption(kopt);
        options.addOption(nopt);
        options.addOption(copt);
        options.addOption(popt);
        options.addOption(Topt);
        options.addOption(vopt);
        options.addOption(Hopt);
        options.addOption(hopt);
        options.addOption(topt);
        options.addOption(oopt);
        return options;
    }

    public static void parseCommandLine(CommandLine cmd, Config config) {
        if (cmd.hasOption('v')) {
            String s = cmd.getOptionValue('v');
            try {
                config.setVerbosity(Integer.parseInt(s));
            } catch (NumberFormatException ex) {
                printError("Invalid verbosity level: " + s);
            }
        }

        if (cmd.hasOption('k')) {
            config.setKeepAlive(true);
        }

        if (cmd.hasOption('c')) {
            String s = cmd.getOptionValue('c');
            try {
                config.setThreads(Integer.parseInt(s));
            } catch (NumberFormatException ex) {
                printError("Invalid number for concurrency: " + s);
            }
        }

        if (cmd.hasOption('n')) {
            String s = cmd.getOptionValue('n');
            try {
                config.setRequests(Integer.parseInt(s));
            } catch (NumberFormatException ex) {
                printError("Invalid number of requests: " + s);
            }
        }

        if (cmd.hasOption('p')) {
            File file = new File(cmd.getOptionValue('p'));
            if (!file.exists()) {
                printError("File not found: " + file);
            }
            config.setPostFile(file);
        }

        if (cmd.hasOption('T')) {
            config.setContentType(cmd.getOptionValue('T'));
        }

        if (cmd.hasOption('i')) {
            config.setHeadInsteadOfGet(true);
        }

        if (cmd.hasOption('H')) {
            String headerStr = cmd.getOptionValue('H');
            config.setHeaders(headerStr.split(","));
        }

        if (cmd.hasOption('t')) {
            String t = cmd.getOptionValue('t');
            try {
                config.setSocketTimeout(Integer.parseInt(t));
            } catch (NumberFormatException ex) {
                printError("Invalid socket timeout: " + t);
            }
        }

        if (cmd.hasOption('o')) {
            config.setUseHttp1_0(true);
        }
        
        String[] cmdargs = cmd.getArgs();
        if (cmdargs.length > 1) {
            try {
                config.setUrl(new URL(cmdargs[0]));
            } catch (MalformedURLException e) {
                printError("Invalid request URL : " + cmdargs[0]);
            }
        }
    }

    static void showUsage(final Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("HttpBenchmark [options] [http://]hostname[:port]/path?query", options);
    }

    static void printError(String msg) {
        System.err.println(msg);
        showUsage(getOptions());
        System.exit(-1);
    }
}
