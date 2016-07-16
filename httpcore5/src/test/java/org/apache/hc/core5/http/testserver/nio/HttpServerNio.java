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

package org.apache.hc.core5.http.testserver.nio;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ExceptionLogger;
import org.apache.hc.core5.http.bootstrap.nio.HttpServer;
import org.apache.hc.core5.http.bootstrap.nio.ServerBootstrap;
import org.apache.hc.core5.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.hc.core5.http.impl.nio.UriHttpAsyncRequestHandlerMapper;
import org.apache.hc.core5.http.nio.HttpAsyncExpectationVerifier;
import org.apache.hc.core5.http.nio.HttpAsyncRequestHandler;
import org.apache.hc.core5.http.nio.NHttpConnectionFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;

public class HttpServerNio {

    private final UriHttpAsyncRequestHandlerMapper reqistry;
    private final HttpServer server;

    public HttpServerNio(
            final HttpProcessor httpProcessor,
            final NHttpConnectionFactory<DefaultNHttpServerConnection> connectionFactory,
            final HttpAsyncExpectationVerifier expectationVerifier,
            final IOReactorConfig reactorConfig) throws IOException {
        super();
        this.reqistry = new UriHttpAsyncRequestHandlerMapper();
        this.server = ServerBootstrap.bootstrap()
                .setIOReactorConfig(reactorConfig)
                .setServerInfo("TEST-SERVER/1.1")
                .setConnectionFactory(connectionFactory)
                .setExceptionLogger(new SimpleExceptionLogger())
                .setExpectationVerifier(expectationVerifier)
                .setHttpProcessor(httpProcessor)
                .setHandlerMapper(this.reqistry)
                .create();
    }

    public void registerHandler(
            final String pattern,
            final HttpAsyncRequestHandler handler) {
        this.reqistry.register(pattern, handler);
    }

    public ListenerEndpoint getListenerEndpoint() {
        final HttpServer local = this.server;
        if (local != null) {
            return this.server.getEndpoint();
        }
        throw new IllegalStateException("Server not running");
    }

    public void start() {
        this.server.start();
    }

    public void shutdown() {
        this.server.shutdown(5, TimeUnit.SECONDS);
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
