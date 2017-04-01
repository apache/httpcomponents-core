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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.UriHttpRequestHandlerMapper;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Asserts;

public class HttpServer {

    private final HttpProcessor httpproc;
    private final UriHttpRequestHandlerMapper reqistry;
    private final ServerSocket serversocket;

    private Thread listener;
    private volatile boolean shutdown;

    public HttpServer() throws IOException {
        super();
        this.httpproc = new DefaultHttpProcessor(
                new ResponseDate(),
                new ResponseServer("TEST-SERVER/1.1"),
                new ResponseContent(),
                new ResponseConnControl());
        this.reqistry = new UriHttpRequestHandlerMapper();
        this.serversocket = new ServerSocket(0);
    }

    public void registerHandler(
            final String pattern,
            final HttpRequestHandler handler) {
        this.reqistry.register(pattern, handler);
    }

    private HttpServerConnection acceptConnection() throws IOException {
        final Socket socket = this.serversocket.accept();
        final DefaultBHttpServerConnection conn = new DefaultBHttpServerConnection(H1Config.DEFAULT);
        conn.bind(socket);
        return conn;
    }

    public int getPort() {
        return this.serversocket.getLocalPort();
    }

    public InetAddress getInetAddress() {
        return this.serversocket.getInetAddress();
    }

    public void start() {
        Asserts.check(this.listener == null, "Listener already running");
        this.listener = new Thread(new Runnable() {

            @Override
            public void run() {
                while (!shutdown && !Thread.interrupted()) {
                    try {
                        // Set up HTTP connection
                        final HttpServerConnection conn = acceptConnection();
                        // Set up the HTTP service
                        final HttpService httpService = new HttpService(
                                httpproc,
                                DefaultConnectionReuseStrategy.INSTANCE,
                                DefaultClassicHttpResponseFactory.INSTANCE,
                                reqistry,
                                null,
                                null);
                        // Start worker thread
                        final Thread t = new WorkerThread(httpService, conn);
                        t.setDaemon(true);
                        t.start();
                    } catch (final InterruptedIOException ex) {
                        break;
                    } catch (final IOException e) {
                        break;
                    }
                }
            }

        });
        this.listener.start();
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        try {
            this.serversocket.close();
        } catch (final IOException ignore) {}
        this.listener.interrupt();
        try {
            this.listener.join(1000);
        } catch (final InterruptedException ignore) {}
    }

    static class WorkerThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection conn;

        public WorkerThread(
                final HttpService httpservice,
                final HttpServerConnection conn) {
            super();
            this.httpservice = httpservice;
            this.conn = conn;
        }

        @Override
        public void run() {
            final HttpContext context = new BasicHttpContext(null);
            try {
                while (!Thread.interrupted() && this.conn.isOpen()) {
                    this.httpservice.handleRequest(this.conn, context);
                }
                this.conn.shutdown(ShutdownType.GRACEFUL);
            } catch (final ConnectionClosedException ex) {
            } catch (final IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (final HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                this.conn.shutdown(ShutdownType.IMMEDIATE);
            }
        }

    }

}
