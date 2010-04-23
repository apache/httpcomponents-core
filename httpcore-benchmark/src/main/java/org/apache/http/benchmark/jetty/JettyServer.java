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

package org.apache.http.benchmark.jetty;

import java.io.IOException;

import org.apache.http.benchmark.HttpServer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class JettyServer implements HttpServer {

    private final Server server;

    public JettyServer(int port) throws IOException {
        super();
        if (port <= 0) {
            throw new IllegalArgumentException("Server port may not be negative or null");
        }

        SocketConnector connector = new SocketConnector();
        connector.setPort(port);
        connector.setRequestBufferSize(12 * 1024);
        connector.setResponseBufferSize(12 * 1024);
        connector.setAcceptors(2);

        QueuedThreadPool threadpool = new QueuedThreadPool();
        threadpool.setMinThreads(25);
        threadpool.setMaxThreads(200);

        this.server = new Server();
        this.server.addConnector(connector);
        this.server.setThreadPool(threadpool);
        this.server.setHandler(new RandomDataHandler());
    }

    public String getName() {
        return "Jetty (blocking I/O)";
    }

    public String getVersion() {
        return Server.getVersion();
    }

    public void start() throws Exception {
        this.server.start();
    }

    public void shutdown() {
        try {
            this.server.stop();
        } catch (Exception ex) {
        }
        try {
            this.server.join();
        } catch (InterruptedException ex) {
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        final JettyServer server = new JettyServer(port);
        System.out.println("Listening on port: " + port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                server.shutdown();
            }

        });
    }

}
