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
package org.apache.http.benchmark;

import java.net.URL;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.http.benchmark.httpcore.HttpCoreNIOServer;
import org.apache.http.benchmark.httpcore.HttpCoreServer;
import org.apache.http.benchmark.jetty.JettyNIOServer;
import org.apache.http.benchmark.jetty.JettyServer;
import org.apache.http.benchmark.CommandLineUtils;
import org.apache.http.benchmark.Config;
import org.apache.http.benchmark.HttpBenchmark;

public class Benchmark {

    private static final int PORT = 8989;

    public static void main(String[] args) throws Exception {

        Config config = new Config();
        if (args.length > 0) {
            Options options = CommandLineUtils.getOptions();
            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption('h')) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Benchmark [options]", options);
                System.exit(1);
            }
            CommandLineUtils.parseCommandLine(cmd, config);
        } else {
            config.setKeepAlive(true);
            config.setRequests(20000);
            config.setThreads(25);
        }

        URL target = new URL("http", "localhost", PORT, "/rnd?c=2048");
        config.setUrl(target);

        Benchmark benchmark = new Benchmark();
        benchmark.run(new JettyServer(PORT), config);
        benchmark.run(new HttpCoreServer(PORT), config);
        benchmark.run(new JettyNIOServer(PORT), config);
        benchmark.run(new HttpCoreNIOServer(PORT), config);
    }

    public Benchmark() {
        super();
    }

    public void run(final HttpServer server, final Config config) throws Exception {
        server.start();
        try {
            System.out.println("---------------------------------------------------------------");
            System.out.println(server.getName() + "; version: " + server.getVersion());
            System.out.println("---------------------------------------------------------------");

            HttpBenchmark ab = new HttpBenchmark(config);
            ab.execute();
            System.out.println("---------------------------------------------------------------");
        } finally {
            server.shutdown();
        }
    }

}
