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
package org.apache.hc.core5.http2.impl.nio.bootstrap;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.NamedElementChain;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseWriterFactory;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncFilterHandler;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.AsyncServerExpectationFilter;
import org.apache.hc.core5.http.nio.support.AsyncServerFilterChainElement;
import org.apache.hc.core5.http.nio.support.AsyncServerFilterChainExchangeHandlerFactory;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.DefaultAsyncResponseExchangeHandlerFactory;
import org.apache.hc.core5.http.nio.support.TerminalAsyncServerFilter;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.ServerH2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.ServerHttpProtocolNegotiationStarter;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorMetricsListener;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 capable {@link HttpAsyncServer} bootstrap.
 *
 * @since 5.0
 */
@SuppressWarnings("deprecation")
public class H2ServerBootstrap {

    private static final H2Config DEFAULT_H2_CONFIG = H2Config.custom().setPushEnabled(false).build();

    private final List<RequestRouter.Entry<Supplier<AsyncServerExchangeHandler>>> routeEntries;
    private final List<FilterEntry<AsyncFilterHandler>> filters;
    private String canonicalHostName;
    private org.apache.hc.core5.http.protocol.LookupRegistry<Supplier<AsyncServerExchangeHandler>> lookupRegistry;
    private HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter;
    private IOReactorConfig ioReactorConfig;
    private HttpProcessor httpProcessor;
    private CharCodingConfig charCodingConfig;
    private HttpVersionPolicy versionPolicy;
    private H2Config h2Config;
    private Http1Config http1Config;
    private TlsStrategy tlsStrategy;
    private Timeout handshakeTimeout;
    private Decorator<IOSession> ioSessionDecorator;
    private Callback<Exception> exceptionCallback;
    private IOSessionListener sessionListener;
    private H2StreamListener h2StreamListener;
    private Http1StreamListener http1StreamListener;
    private IOReactorMetricsListener threadPoolListener;
    private FrameFactory frameFactory;

    private H2ServerBootstrap() {
        this.routeEntries = new ArrayList<>();
        this.filters = new ArrayList<>();
    }

    public static H2ServerBootstrap bootstrap() {
        return new H2ServerBootstrap();
    }

    /**
     * Sets canonical name (fully qualified domain name) of the server.
     *
     * @return this instance.
     * @since 5.0
     */
    public final H2ServerBootstrap setCanonicalHostName(final String canonicalHostName) {
        this.canonicalHostName = canonicalHostName;
        return this;
    }

    /**
     * Sets I/O reactor configuration.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets {@link HttpProcessor} instance.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Sets HTTP protocol version policy
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setVersionPolicy(final HttpVersionPolicy versionPolicy) {
        this.versionPolicy = versionPolicy;
        return this;
    }

    /**
     * Sets HTTP/2 protocol parameters
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setH2Config(final H2Config h2Config) {
        Args.check(!h2Config.isPushEnabled(), "A server MUST NOT set enable_push to true");
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets HTTP/1.1 protocol parameters
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    /**
     * Sets message char coding.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setCharset(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Sets {@link TlsStrategy} instance.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    public final H2ServerBootstrap setHandshakeTimeout(final Timeout handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
    }

    /**
     * Sets {@link IOSession} {@link Decorator} instance.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }


    /**
     * Sets {@link IOReactorMetricsListener} instance.
     *
     * @return this instance.
     * @since 5.4
     */
    public final H2ServerBootstrap setIOReactorMetricsListener(final IOReactorMetricsListener threadPoolListener) {
        this.threadPoolListener = threadPoolListener;
        return this;
    }

    /**
     * Sets {@link Exception} {@link Callback} instance.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setExceptionCallback(final Callback<Exception> exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }

    /**
     * Sets {@link IOSessionListener} instance.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Sets {@link H2StreamListener} instance.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setStreamListener(final H2StreamListener h2StreamListener) {
        this.h2StreamListener = h2StreamListener;
        return this;
    }

    /**
     * Sets {@link FrameFactory} instance.
     *
     * @since 5.4
     * @return this instance.
     */
    public final H2ServerBootstrap setFrameFactory(final FrameFactory frameFactory) {
        this.frameFactory = frameFactory;
        return this;
    }

    /**
     * Sets {@link Http1StreamListener} instance.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap setStreamListener(final Http1StreamListener http1StreamListener) {
        this.http1StreamListener = http1StreamListener;
        return this;
    }
    /**
     * @return this instance.
     * @deprecated Use {@link RequestRouter}.
     */
    @Deprecated
    public final H2ServerBootstrap setLookupRegistry(final org.apache.hc.core5.http.protocol.LookupRegistry<Supplier<AsyncServerExchangeHandler>> lookupRegistry) {
        this.lookupRegistry = lookupRegistry;
        return this;
    }

    /**
     * Sets {@link HttpRequestMapper} instance.
     *
     * @return this instance.
     * @see org.apache.hc.core5.http.impl.routing.RequestRouter
     * @since 5.3
     */
    public final H2ServerBootstrap setRequestRouter(final HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter) {
        this.requestRouter = requestRouter;
        return this;
    }

    /**
     * Registers the given {@link AsyncServerExchangeHandler} {@link Supplier} as a default handler for URIs
     * matching the given pattern.
     *
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     * @return this instance.
     */
    public final H2ServerBootstrap register(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Exchange handler supplier");
        routeEntries.add(new RequestRouter.Entry<>(uriPattern, supplier));
        return this;
    }

    /**
     * Registers the given {@link AsyncServerExchangeHandler} {@link Supplier} as a handler for URIs
     * matching the given host and the pattern.
     *
     * @param hostname the host name
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     * @return this instance.
     *
     * @since 5.3
     */
    public final H2ServerBootstrap register(final String hostname, final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Exchange handler supplier");
        routeEntries.add(new RequestRouter.Entry<>(hostname, uriPattern, supplier));
        return this;
    }

    /**
     * @return this instance.
     * @deprecated Use {@link #register(String, String, Supplier)}.
     */
    @Deprecated
    public final H2ServerBootstrap registerVirtual(final String hostname, final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        return register(hostname, uriPattern, supplier);
    }

    /**
     * Registers the given {@link AsyncServerRequestHandler} as a default handler for URIs
     * matching the given pattern.
     *
     * @param <T> request handler representation.
     * @param uriPattern the pattern to register the handler for.
     * @param requestHandler the handler.
     * @return this instance.
     */
    public final <T> H2ServerBootstrap register(
            final String uriPattern,
            final AsyncServerRequestHandler<T> requestHandler) {
        register(uriPattern, () -> new BasicServerExchangeHandler<>(requestHandler));
        return this;
    }

    /**
     * Registers the given {@link AsyncServerRequestHandler} as a handler for URIs
     * matching the given host and the pattern.
     *
     * @param <T> request handler representation.
     * @param hostname the host name
     * @param uriPattern the pattern to register the handler for.
     * @param requestHandler the handler.
     * @return this instance.
     *
     * @since 5.3
     */
    public final <T> H2ServerBootstrap register(
            final String hostname,
            final String uriPattern,
            final AsyncServerRequestHandler<T> requestHandler) {
        registerVirtual(hostname, uriPattern, () -> new BasicServerExchangeHandler<>(requestHandler));
        return this;
    }

    /**
     * @param <T> request handler representation.
     * @return this instance.
     * @deprecated Use {@link #register(String, String, Supplier)}.
     */
    @Deprecated
    public final <T> H2ServerBootstrap registerVirtual(
            final String hostname,
            final String uriPattern,
            final AsyncServerRequestHandler<T> requestHandler) {
        return register(hostname, uriPattern, requestHandler);
    }

    /**
     * Adds the filter before the filter with the given name.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap addFilterBefore(final String existing, final String name, final AsyncFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Position.BEFORE, name, filterHandler, existing));
        return this;
    }

    /**
     * Adds the filter after the filter with the given name.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap addFilterAfter(final String existing, final String name, final AsyncFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Position.AFTER, name, filterHandler, existing));
        return this;
    }

    /**
     * Replace an existing filter with the given name with new filter.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap replaceFilter(final String existing, final AsyncFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Position.REPLACE, existing, filterHandler, existing));
        return this;
    }

    /**
     * Add an filter to the head of the processing list.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap addFilterFirst(final String name, final AsyncFilterHandler filterHandler) {
        Args.notNull(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Position.FIRST, name, filterHandler, null));
        return this;
    }

    /**
     * Add an filter to the tail of the processing list.
     *
     * @return this instance.
     */
    public final H2ServerBootstrap addFilterLast(final String name, final AsyncFilterHandler filterHandler) {
        Args.notNull(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Position.LAST, name, filterHandler, null));
        return this;
    }

    public HttpAsyncServer create() {
        final String actualCanonicalHostName = canonicalHostName != null ? canonicalHostName : InetAddressUtils.getCanonicalLocalHostName();
        final HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouterCopy;
        if (lookupRegistry != null && requestRouter == null) {
            final org.apache.hc.core5.http.protocol.RequestHandlerRegistry<Supplier<AsyncServerExchangeHandler>> handlerRegistry = new org.apache.hc.core5.http.protocol.RequestHandlerRegistry<>(
                    actualCanonicalHostName,
                    () -> lookupRegistry != null ? lookupRegistry : new org.apache.hc.core5.http.protocol.UriPatternMatcher<>());
            for (final RequestRouter.Entry<Supplier<AsyncServerExchangeHandler>> entry: routeEntries) {
                handlerRegistry.register(entry.uriAuthority != null ? entry.uriAuthority.getHostName() : null, entry.route.pattern, entry.route.handler);
            }
            requestRouterCopy = handlerRegistry;
        } else {
            if (routeEntries.isEmpty()) {
                requestRouterCopy = requestRouter;
            } else {
                requestRouterCopy = RequestRouter.create(
                        new URIAuthority(actualCanonicalHostName),
                        UriPatternType.URI_PATTERN,
                        routeEntries,
                        RequestRouter.IGNORE_PORT_AUTHORITY_RESOLVER,
                        requestRouter);
            }
        }

        final HandlerFactory<AsyncServerExchangeHandler> handlerFactory;
        if (!filters.isEmpty()) {
            final NamedElementChain<AsyncFilterHandler> filterChainDefinition = new NamedElementChain<>();
            filterChainDefinition.addLast(
                    new TerminalAsyncServerFilter(new DefaultAsyncResponseExchangeHandlerFactory(requestRouterCopy)),
                    StandardFilter.MAIN_HANDLER.name());
            filterChainDefinition.addFirst(
                    new AsyncServerExpectationFilter(),
                    StandardFilter.EXPECT_CONTINUE.name());

            for (final FilterEntry<AsyncFilterHandler> entry: filters) {
                switch (entry.position) {
                    case AFTER:
                        filterChainDefinition.addAfter(entry.existing, entry.filterHandler, entry.name);
                        break;
                    case BEFORE:
                        filterChainDefinition.addBefore(entry.existing, entry.filterHandler, entry.name);
                        break;
                    case REPLACE:
                        filterChainDefinition.replace(entry.existing, entry.filterHandler);
                        break;
                    case FIRST:
                        filterChainDefinition.addFirst(entry.filterHandler, entry.name);
                        break;
                    case LAST:
                        // Don't add last, after TerminalAsyncServerFilter, as that does not delegate to the chain
                        // Instead, add the filter just before it, making it effectively the last filter
                        filterChainDefinition.addBefore(StandardFilter.MAIN_HANDLER.name(), entry.filterHandler, entry.name);
                        break;
                }
            }

            NamedElementChain<AsyncFilterHandler>.Node current = filterChainDefinition.getLast();
            AsyncServerFilterChainElement execChain = null;
            while (current != null) {
                execChain = new AsyncServerFilterChainElement(current.getValue(), execChain);
                current = current.getPrevious();
            }

            handlerFactory = new AsyncServerFilterChainExchangeHandlerFactory(execChain, exceptionCallback);
        } else {
            handlerFactory = new DefaultAsyncResponseExchangeHandlerFactory(requestRouterCopy,
                    handler -> new BasicAsyncServerExpectationDecorator(handler, exceptionCallback));
        }

        final ServerH2StreamMultiplexerFactory http2StreamHandlerFactory = new ServerH2StreamMultiplexerFactory(
                httpProcessor != null ? httpProcessor : H2Processors.server(),
                handlerFactory,
                h2Config != null ? h2Config : DEFAULT_H2_CONFIG,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                h2StreamListener,
                frameFactory);

        final TlsStrategy actualTlsStrategy = tlsStrategy != null ? tlsStrategy : new H2ServerTlsStrategy();

        final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory = new ServerHttp1StreamDuplexerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                handlerFactory,
                http1Config != null ? http1Config : Http1Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                new DefaultHttpRequestParserFactory(http1Config),
                new DefaultHttpResponseWriterFactory(http1Config),
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                http1StreamListener,
                exceptionCallback);

        final IOEventHandlerFactory ioEventHandlerFactory = new ServerHttpProtocolNegotiationStarter(
                http1StreamHandlerFactory,
                http2StreamHandlerFactory,
                versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE,
                actualTlsStrategy,
                handshakeTimeout,
                exceptionCallback);

        return new HttpAsyncServer(ioEventHandlerFactory, ioReactorConfig, ioSessionDecorator, exceptionCallback,
                sessionListener, threadPoolListener, null, actualCanonicalHostName);
    }

}
