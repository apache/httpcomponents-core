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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.DefaultAsyncResponseExchangeHandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

public class H2TestServer extends AsyncServer {

    private final SSLContext sslContext;
    private final SSLSessionInitializer sslSessionInitializer;
    private final SSLSessionVerifier sslSessionVerifier;
    private final List<RequestRouter.Entry<Supplier<AsyncServerExchangeHandler>>> routeEntries;

    private H2Config h2Config;
    private Http1Config http1Config;
    private HttpProcessor httpProcessor;
    private Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator;

    public H2TestServer(
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

    public H2TestServer() throws IOException {
        this(IOReactorConfig.DEFAULT, null, null, null);
    }

    private void ensureNotRunning() {
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

    /**
     * @since 5.3
     */
    public void configure(final H2Config h2Config) {
        ensureNotRunning();
        this.h2Config = h2Config;
        this.http1Config = null;
    }

    /**
     * @since 5.3
     */
    public void configure(final Http1Config http1Config) {
        ensureNotRunning();
        this.http1Config = http1Config;
        this.h2Config = null;
    }

    /**
     * @since 5.3
     */
    public void configure(final HttpProcessor httpProcessor) {
        ensureNotRunning();
        this.httpProcessor = httpProcessor;
    }

    /**
     * @since 5.3
     */
    public void configure(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) {
        ensureNotRunning();
        this.exchangeHandlerDecorator = exchangeHandlerDecorator;
    }

    public void start(final IOEventHandlerFactory handlerFactory) throws IOException {
        execute(handlerFactory);
    }

    /**
     * @deprecated Use {@link #configure(Http1Config)}, {@link #configure(HttpProcessor)}, {@link #configure(Decorator)}, {@link #start()}.
     */
    @Deprecated
    public InetSocketAddress start(
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator,
            final Http1Config http1Config) throws Exception {
        configure(http1Config);
        configure(exchangeHandlerDecorator);
        configure(httpProcessor);
        return start();
    }

    /**
     * @deprecated Use {@link #configure(H2Config)}, {@link #configure(HttpProcessor)}, {@link #configure(Decorator)}, {@link #start()}.
     */
    @Deprecated
    public InetSocketAddress start(
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator,
            final H2Config h2Config) throws Exception {
        configure(h2Config);
        configure(exchangeHandlerDecorator);
        configure(httpProcessor);
        return start();
    }

    /**
     * @deprecated Use {@link #configure(Http1Config)}, {@link #configure(HttpProcessor)}, {@link #start()}.
     */
    @Deprecated
    public InetSocketAddress start(final HttpProcessor httpProcessor, final Http1Config http1Config) throws Exception {
        configure(http1Config);
        configure(httpProcessor);
        return start();
    }

    /**
     * @deprecated Use {@link #configure(H2Config)}, {@link #start()}.
     */
    @Deprecated
    public InetSocketAddress start(final H2Config h2Config) throws Exception {
        configure(h2Config);
        return start();
    }

    /**
     * @deprecated Use {@link #configure(Http1Config)}, {@link #start()}.
     */
    @Deprecated
    public InetSocketAddress start(final Http1Config http1Config) throws Exception {
        configure(http1Config);
        return start();
    }

    public InetSocketAddress start() throws Exception {
        if (http1Config != null) {
            start(new InternalServerProtocolNegotiationStarter(
                    httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                    new DefaultAsyncResponseExchangeHandlerFactory(
                            RequestRouter.create(RequestRouter.LOCAL_AUTHORITY, UriPatternType.URI_PATTERN, routeEntries, RequestRouter.LOCAL_AUTHORITY_RESOLVER, null),
                            exchangeHandlerDecorator != null ? exchangeHandlerDecorator : BasicAsyncServerExpectationDecorator::new),
                    HttpVersionPolicy.FORCE_HTTP_1,
                    H2Config.DEFAULT,
                    http1Config,
                    CharCodingConfig.DEFAULT,
                    sslContext,
                    sslSessionInitializer,
                    sslSessionVerifier));
        } else {
            start(new InternalServerProtocolNegotiationStarter(
                    httpProcessor != null ? httpProcessor : H2Processors.server(),
                    new DefaultAsyncResponseExchangeHandlerFactory(
                            RequestRouter.create(RequestRouter.LOCAL_AUTHORITY, UriPatternType.URI_PATTERN, routeEntries, RequestRouter.LOCAL_AUTHORITY_RESOLVER, null),
                            exchangeHandlerDecorator != null ? exchangeHandlerDecorator : BasicAsyncServerExpectationDecorator::new),
                    HttpVersionPolicy.FORCE_HTTP_2,
                    h2Config,
                    Http1Config.DEFAULT,
                    CharCodingConfig.DEFAULT,
                    sslContext,
                    sslSessionInitializer,
                    sslSessionVerifier));
        }
        final Future<ListenerEndpoint> future = listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        return (InetSocketAddress) listener.getAddress();
    }

}
