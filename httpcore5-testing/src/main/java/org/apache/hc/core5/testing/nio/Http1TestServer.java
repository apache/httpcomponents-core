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
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.DefaultAsyncResponseExchangeHandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;

public class Http1TestServer extends AsyncServer {

    private final RequestHandlerRegistry<Supplier<AsyncServerExchangeHandler>> registry;
    private final SSLContext sslContext;
    private final SSLSessionInitializer sslSessionInitializer;
    private final SSLSessionVerifier sslSessionVerifier;

    public Http1TestServer(
            final IOReactorConfig ioReactorConfig,
            final SSLContext sslContext,
            final SSLSessionInitializer sslSessionInitializer,
            final SSLSessionVerifier sslSessionVerifier) throws IOException {
        super(ioReactorConfig);
        this.registry = new RequestHandlerRegistry<>();
        this.sslContext = sslContext;
        this.sslSessionInitializer = sslSessionInitializer;
        this.sslSessionVerifier = sslSessionVerifier;
    }

    public Http1TestServer() throws IOException {
        this(IOReactorConfig.DEFAULT, null, null, null);
    }

    public void register(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        registry.register(null, uriPattern, supplier);
    }

    public <T> void register(
            final String uriPattern,
            final AsyncServerRequestHandler<T> requestHandler) {
        register(uriPattern, () -> new BasicServerExchangeHandler<>(requestHandler));
    }

    public InetSocketAddress start(final IOEventHandlerFactory handlerFactory) throws Exception {
        execute(handlerFactory);
        final Future<ListenerEndpoint> future = listen(new InetSocketAddress(0));
        final ListenerEndpoint listener = future.get();
        return (InetSocketAddress) listener.getAddress();
    }

    public InetSocketAddress start(
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator,
            final Http1Config http1Config) throws Exception {
        return start(new InternalServerHttp1EventHandlerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                new DefaultAsyncResponseExchangeHandlerFactory(
                        registry,
                        exchangeHandlerDecorator != null ? exchangeHandlerDecorator : BasicAsyncServerExpectationDecorator::new),
                http1Config,
                CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                sslContext,
                sslSessionInitializer,
                sslSessionVerifier));
    }

    public InetSocketAddress start(final HttpProcessor httpProcessor, final Http1Config http1Config) throws Exception {
        return start(httpProcessor, null, http1Config);
    }

    public InetSocketAddress start() throws Exception {
        return start(null, null, Http1Config.DEFAULT);
    }

}
