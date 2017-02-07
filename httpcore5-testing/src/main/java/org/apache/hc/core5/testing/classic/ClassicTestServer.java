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

package org.apache.hc.core5.testing.classic;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpExpectationVerifier;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.UriHttpRequestHandlerMapper;
import org.apache.hc.core5.util.Asserts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
public class ClassicTestServer {

    private final UriHttpRequestHandlerMapper reqistry;
    private volatile HttpExpectationVerifier expectationVerifier;
    private volatile int timeout;

    private volatile HttpServer server;

    public ClassicTestServer() throws IOException {
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
        final HttpServer local = this.server;
        if (local != null) {
            return this.server.getLocalPort();
        } else {
            throw new IllegalStateException("Server not running");
        }
    }

    public InetAddress getInetAddress() {
        final HttpServer local = this.server;
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
                .setConnectionFactory(new LoggingConnFactory())
                .setExceptionListener(new SimpleExceptionListener())
                .setExpectationVerifier(this.expectationVerifier)
                .setHandlerMapper(this.reqistry)
                .create();
        this.server.start();
    }

    public void shutdown() {
        shutdown(5, TimeUnit.SECONDS);
    }

    public void shutdown(final long gracePeriod, final TimeUnit timeUnit) {
        final HttpServer local = this.server;
        this.server = null;
        if (local != null) {
            local.shutdown(gracePeriod, timeUnit);
        }
    }

    class LoggingConnFactory implements HttpConnectionFactory<LoggingBHttpServerConnection> {

        @Override
        public LoggingBHttpServerConnection createConnection(final Socket socket) throws IOException {
            final LoggingBHttpServerConnection conn = new LoggingBHttpServerConnection(H1Config.DEFAULT);
            conn.bind(socket);
            return conn;
        }
    }

    static class SimpleExceptionListener implements ExceptionListener {

        private final Logger log = LogManager.getLogger(ClassicTestServer.class);

        @Override
        public void onError(final Exception ex) {
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
