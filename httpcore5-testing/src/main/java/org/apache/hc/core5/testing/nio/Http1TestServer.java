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

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.DefaultAsyncResponseExchangeHandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;

public class Http1TestServer extends HttpTestServer {

    private Http1Config http1Config;
    private NHttpMessageParserFactory<HttpRequest> requestParserFactory;
    private NHttpMessageWriterFactory<HttpResponse> responseWriterFactory;

    public Http1TestServer(
            final IOReactorConfig ioReactorConfig,
            final SSLContext sslContext,
            final SSLSessionInitializer sslSessionInitializer,
            final SSLSessionVerifier sslSessionVerifier) throws IOException {
        super(ioReactorConfig, sslContext, sslSessionInitializer, sslSessionVerifier);
    }

    public Http1TestServer() throws IOException {
        this(IOReactorConfig.DEFAULT, null, null, null);
    }

    /**
     * @since 5.3
     */
    public void configure(final Http1Config http1Config) {
        ensureNotRunning();
        this.http1Config = http1Config;
    }

    /**
     * @since 5.4
     */
    public void configure(final NHttpMessageParserFactory<HttpRequest> requestParserFactory) {
        ensureNotRunning();
        this.requestParserFactory = requestParserFactory;
    }

    /**
     * @since 5.4
     */
    public void configure(final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory) {
        ensureNotRunning();
        this.responseWriterFactory = responseWriterFactory;
    }

    /**
     * @deprecated Use {@link #startExecution(IOEventHandlerFactory)}.
     */
    @Deprecated
    public InetSocketAddress start(final IOEventHandlerFactory handlerFactory) throws Exception {
        return startExecution(handlerFactory);
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
     * @deprecated Use {@link #configure(Http1Config)}, {@link #configure(HttpProcessor)}, {@link #start()}.
     */
    @Deprecated
    public InetSocketAddress start(final HttpProcessor httpProcessor, final Http1Config http1Config) throws Exception {
        configure(http1Config);
        configure(httpProcessor);
        return start();
    }

    @Override
    public InetSocketAddress start() throws Exception {
        return startExecution(new InternalServerHttp1EventHandlerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                new DefaultAsyncResponseExchangeHandlerFactory(
                        RequestRouter.create(RequestRouter.LOCAL_AUTHORITY, UriPatternType.URI_PATTERN, routeEntries, RequestRouter.LOCAL_AUTHORITY_RESOLVER, null),
                        exchangeHandlerDecorator != null ? exchangeHandlerDecorator : BasicAsyncServerExpectationDecorator::new),
                http1Config,
                CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                requestParserFactory,
                responseWriterFactory,
                sslContext,
                sslSessionInitializer,
                sslSessionVerifier));
    }

}
