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

package org.apache.http.nio.testserver;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.ConnectionClosedException;
import org.apache.http.ExceptionLogger;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.protocol.HttpAsyncExpectationVerifier;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.util.Asserts;

public class HttpServerNio {

    private final UriHttpAsyncRequestHandlerMapper reqistry;
    private volatile HttpAsyncExpectationVerifier expectationVerifier;
    private volatile NHttpConnectionFactory<DefaultNHttpServerConnection> connectionFactory;
    private volatile HttpProcessor httpProcessor;
    private volatile int timeout;

    private volatile HttpServer server;

    public HttpServerNio() throws IOException {
        super();
        this.reqistry = new UriHttpAsyncRequestHandlerMapper();
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    public void registerHandler(
            final String pattern,
            final HttpAsyncRequestHandler handler) {
        this.reqistry.register(pattern, handler);
    }

    public void setExpectationVerifier(final HttpAsyncExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
    }

    public void setConnectionFactory(final NHttpConnectionFactory<DefaultNHttpServerConnection> connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
    }

    public ListenerEndpoint getListenerEndpoint() {
        final HttpServer local = this.server;
        if (local != null) {
            return this.server.getEndpoint();
        } else {
            throw new IllegalStateException("Server not running");
        }
    }

    public void start() throws IOException {
        Asserts.check(this.server == null, "Server already running");
        this.server = ServerBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(this.timeout)
                        .build())
                .setServerInfo("TEST-SERVER/1.1")
                .setConnectionFactory(connectionFactory)
                .setExceptionLogger(new SimpleExceptionLogger())
                .setExpectationVerifier(this.expectationVerifier)
                .setHttpProcessor(this.httpProcessor)
                .setHandlerMapper(this.reqistry)
                .create();
        this.server.start();
    }

    public void shutdown() {
        final HttpServer local = this.server;
        this.server = null;
        if (local != null) {
            local.shutdown(5, TimeUnit.SECONDS);
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
