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
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.support.BasicHttpServerExpectationDecorator;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.io.CloseMode;

public class ClassicTestServer {

    private final SSLContext sslContext;
    private final SocketConfig socketConfig;
    private final RequestHandlerRegistry<HttpRequestHandler> registry;

    private final AtomicReference<HttpServer> serverRef;

    public ClassicTestServer(final SSLContext sslContext, final SocketConfig socketConfig) {
        super();
        this.sslContext = sslContext;
        this.socketConfig = socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
        this.registry = new RequestHandlerRegistry<>();
        this.serverRef = new AtomicReference<>(null);
    }

    public ClassicTestServer(final SocketConfig socketConfig) {
        this(null, socketConfig);
    }

    public ClassicTestServer() {
        this(null, null);
    }

    public void registerHandler(final String pattern, final HttpRequestHandler handler) {
        this.registry.register(null, pattern, handler);
    }

    public void registerHandlerVirtual(final String hostname, final String pattern, final HttpRequestHandler handler) {
        this.registry.register(hostname, pattern, handler);
    }

    public int getPort() {
        final HttpServer server = this.serverRef.get();
        if (server != null) {
            return server.getLocalPort();
        }
        throw new IllegalStateException("Server not running");
    }

    public InetAddress getInetAddress() {
        final HttpServer server = this.serverRef.get();
        if (server != null) {
            return server.getInetAddress();
        }
        throw new IllegalStateException("Server not running");
    }

    public void start(
            final Http1Config http1Config,
            final HttpProcessor httpProcessor,
            final Decorator<HttpServerRequestHandler> handlerDecorator) throws IOException {
        if (serverRef.get() == null) {
            final HttpServerRequestHandler handler = new BasicHttpServerRequestHandler(registry);
            final HttpService httpService = new HttpService(
                    httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                    handlerDecorator != null ? handlerDecorator.decorate(handler) : new BasicHttpServerExpectationDecorator(handler),
                    DefaultConnectionReuseStrategy.INSTANCE,
                    LoggingHttp1StreamListener.INSTANCE);
            final HttpServer server = new HttpServer(
                    0,
                    httpService,
                    null,
                    socketConfig,
                    sslContext != null ? sslContext.getServerSocketFactory() : ServerSocketFactory.getDefault(),
                    new LoggingBHttpServerConnectionFactory(
                            sslContext != null ? URIScheme.HTTPS.id : URIScheme.HTTP.id,
                            http1Config != null ? http1Config : Http1Config.DEFAULT,
                            CharCodingConfig.DEFAULT),
                    null,
                    LoggingExceptionListener.INSTANCE);
            if (serverRef.compareAndSet(null, server)) {
                server.start();
            }
        } else {
            throw new IllegalStateException("Server already running");
        }
    }

    public void start() throws IOException {
        start(null, null, null);
    }

    public void shutdown(final CloseMode closeMode) {
        final HttpServer server = serverRef.getAndSet(null);
        if (server != null) {
            server.close(closeMode);
        }
    }

}
