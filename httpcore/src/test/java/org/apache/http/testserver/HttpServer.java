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

package org.apache.http.testserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.ConnectionClosedException;
import org.apache.http.ExceptionLogger;
import org.apache.http.HttpConnectionFactory;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;
import org.apache.http.util.Asserts;

public class HttpServer {

    private final UriHttpRequestHandlerMapper reqistry;
    private volatile HttpExpectationVerifier expectationVerifier;
    private volatile int timeout;

    private volatile org.apache.http.impl.bootstrap.HttpServer server;

    public HttpServer() throws IOException {
        super();
        this.reqistry = new UriHttpRequestHandlerMapper();
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    public void registerHandler(
            final String pattern,
            final HttpRequestHandler handler) {
        this.reqistry.register(pattern, handler);
    }

    public void setExpectationVerifier(final HttpExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
    }

    public int getPort() {
        final org.apache.http.impl.bootstrap.HttpServer local = this.server;
        if (local != null) {
            return this.server.getLocalPort();
        } else {
            throw new IllegalStateException("Server not running");
        }
    }

    public InetAddress getInetAddress() {
        final org.apache.http.impl.bootstrap.HttpServer local = this.server;
        if (local != null) {
            return local.getInetAddress();
        } else {
            throw new IllegalStateException("Server not running");
        }
    }

    public void start() throws IOException {
        Asserts.check(this.server == null, "Server already running");
        this.server = ServerBootstrap.bootstrap()
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(this.timeout)
                        .build())
                .setServerInfo("TEST-SERVER/1.1")
                .setConnectionFactory(new LoggingConnFactory())
                .setExceptionLogger(new SimpleExceptionLogger())
                .setExpectationVerifier(this.expectationVerifier)
                .setHandlerMapper(this.reqistry)
                .create();
        this.server.start();
    }

    public void shutdown() {
        final org.apache.http.impl.bootstrap.HttpServer local = this.server;
        this.server = null;
        if (local != null) {
            local.shutdown(5, TimeUnit.SECONDS);
        }
    }

    static class LoggingConnFactory implements HttpConnectionFactory<LoggingBHttpServerConnection> {

        @Override
        public LoggingBHttpServerConnection createConnection(final Socket socket) throws IOException {
            final LoggingBHttpServerConnection conn = new LoggingBHttpServerConnection(8 * 1024);
            conn.bind(socket);
            return conn;
        }
    }

    static class SimpleExceptionLogger implements ExceptionLogger {

        private final Log log = LogFactory.getLog(HttpServer.class);

        @Override
        public void log(final Exception ex) {
            if (ex instanceof ConnectionClosedException) {
                this.log.debug(ex.getMessage());
            } else if (ex instanceof SocketException) {
                this.log.debug(ex.getMessage());
            } else {
                this.log.error(ex.getMessage(), ex);
            }
        }
    }

}
