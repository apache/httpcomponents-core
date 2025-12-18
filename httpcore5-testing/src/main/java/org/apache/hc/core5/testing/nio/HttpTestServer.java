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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * @since 5.4
 */
public abstract class HttpTestServer extends AsyncServer {

    final SSLContext sslContext;
    final SSLSessionInitializer sslSessionInitializer;
    final SSLSessionVerifier sslSessionVerifier;
    final List<RequestRouter.Entry<Supplier<AsyncServerExchangeHandler>>> routeEntries;

    HttpProcessor httpProcessor;
    Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator;

    public HttpTestServer(
            final IOReactorConfig ioReactorConfig,
            final SSLContext sslContext,
            final SSLSessionInitializer sslSessionInitializer,
            final SSLSessionVerifier sslSessionVerifier) throws IOException {
        super(ioReactorConfig);
        this.sslContext = sslContext;
        this.sslSessionInitializer = sslSessionInitializer;
        this.sslSessionVerifier = sslSessionVerifier;
        this.routeEntries = new ArrayList<>();
    }

    public HttpTestServer() throws IOException {
        this(IOReactorConfig.DEFAULT, null, null, null);
    }

    public abstract InetSocketAddress start() throws Exception;

    void ensureNotRunning() {
        Asserts.check(getStatus() == IOReactorStatus.INACTIVE, "Server is already running");
    }

    public void register(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Exchange handler supplier");
        Asserts.check(getStatus() == IOReactorStatus.INACTIVE, "Server has already been started");
        ensureNotRunning();
        routeEntries.add(new RequestRouter.Entry<>(uriPattern, supplier));
    }

    public <T> void register(
            final String uriPattern,
            final AsyncServerRequestHandler<T> requestHandler) {
        register(uriPattern, () -> new BasicServerExchangeHandler<>(requestHandler));
    }

    public void configure(final HttpProcessor httpProcessor) {
        ensureNotRunning();
        this.httpProcessor = httpProcessor;
    }

    public void configure(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) {
        ensureNotRunning();
        this.exchangeHandlerDecorator = exchangeHandlerDecorator;
    }

    public InetSocketAddress startExecution(final IOEventHandlerFactory handlerFactory) throws Exception {
        execute(handlerFactory);
        final Future<ListenerEndpoint> future = listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        final ListenerEndpoint listener = future.get();
        return (InetSocketAddress) listener.getAddress();
    }

}
