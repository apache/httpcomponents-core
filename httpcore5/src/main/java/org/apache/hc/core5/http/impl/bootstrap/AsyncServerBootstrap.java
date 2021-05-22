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
package org.apache.hc.core5.http.impl.bootstrap;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.NamedElementChain;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseWriterFactory;
import org.apache.hc.core5.http.impl.nio.ServerHttp1IOEventHandlerFactory;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
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
import org.apache.hc.core5.http.protocol.LookupRegistry;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link HttpAsyncServer} bootstrap.
 *
 * @since 5.0
 */
public class AsyncServerBootstrap {

    private final List<HandlerEntry<Supplier<AsyncServerExchangeHandler>>> handlerList;
    private final List<FilterEntry<AsyncFilterHandler>> filters;
    private String canonicalHostName;
    private LookupRegistry<Supplier<AsyncServerExchangeHandler>> lookupRegistry;
    private IOReactorConfig ioReactorConfig;
    private Http1Config http1Config;
    private CharCodingConfig charCodingConfig;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private TlsStrategy tlsStrategy;
    private Timeout handshakeTimeout;
    private Decorator<IOSession> ioSessionDecorator;
    private Callback<Exception> exceptionCallback;
    private IOSessionListener sessionListener;
    private Http1StreamListener streamListener;

    private AsyncServerBootstrap() {
        this.handlerList = new ArrayList<>();
        this.filters = new ArrayList<>();
    }

    public static AsyncServerBootstrap bootstrap() {
        return new AsyncServerBootstrap();
    }

    /**
     * Sets canonical name (fully qualified domain name) of the server.
     */
    public final AsyncServerBootstrap setCanonicalHostName(final String canonicalHostName) {
        this.canonicalHostName = canonicalHostName;
        return this;
    }

    /**
     * Sets I/O reactor configuration.
     */
    public final AsyncServerBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets HTTP/1.1 protocol parameters.
     */
    public final AsyncServerBootstrap setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    /**
     * Sets connection configuration.
     */
    public final AsyncServerBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Assigns {@link org.apache.hc.core5.http.protocol.HttpProcessor} instance.
     */
    public final AsyncServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Assigns {@link org.apache.hc.core5.http.ConnectionReuseStrategy} instance.
     */
    public final AsyncServerBootstrap setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    /**
     * Assigns {@link TlsStrategy} instance.
     */
    public final AsyncServerBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Assigns TLS handshake {@link Timeout}.
     */
    public final AsyncServerBootstrap setTlsHandshakeTimeout(final Timeout handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
    }

    /**
     * Assigns {@link IOSession} {@link Decorator} instance.
     */
    public final AsyncServerBootstrap setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Assigns {@link Exception} {@link Callback} instance.
     */
    public final AsyncServerBootstrap setExceptionCallback(final Callback<Exception> exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }

    /**
     * Assigns {@link IOSessionListener} instance.
     */
    public final AsyncServerBootstrap setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Assigns {@link LookupRegistry} instance.
     */
    public final AsyncServerBootstrap setLookupRegistry(final LookupRegistry<Supplier<AsyncServerExchangeHandler>> lookupRegistry) {
        this.lookupRegistry = lookupRegistry;
        return this;
    }

    /**
     * Assigns {@link Http1StreamListener} instance.
     *
     * @since 5.0
     */
    public final AsyncServerBootstrap setStreamListener(final Http1StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Registers the given {@link AsyncServerExchangeHandler} {@link Supplier} as a default handler for URIs
     * matching the given pattern.
     *
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     */
    public final AsyncServerBootstrap register(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        handlerList.add(new HandlerEntry<>(null, uriPattern, supplier));
        return this;
    }

    /**
     * Registers the given {@link AsyncServerExchangeHandler} {@link Supplier} as a handler for URIs
     * matching the given host and the pattern.
     *
     * @param hostname the host name
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     */
    public final AsyncServerBootstrap registerVirtual(final String hostname, final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        handlerList.add(new HandlerEntry<>(hostname, uriPattern, supplier));
        return this;
    }

    /**
     * Registers the given {@link AsyncServerRequestHandler} as a default handler for URIs
     * matching the given pattern.
     *
     * @param uriPattern the pattern to register the handler for.
     * @param requestHandler the handler.
     */
    public final <T> AsyncServerBootstrap register(
            final String uriPattern,
            final AsyncServerRequestHandler<T> requestHandler) {
        register(uriPattern, () -> new BasicServerExchangeHandler<>(requestHandler));
        return this;
    }

    /**
     * Registers the given {@link AsyncServerRequestHandler} as a handler for URIs
     * matching the given host and the pattern.
     *
     * @param hostname the host name
     * @param uriPattern the pattern to register the handler for.
     * @param requestHandler the handler.
     */
    public final <T> AsyncServerBootstrap registerVirtual(
            final String hostname,
            final String uriPattern,
            final AsyncServerRequestHandler<T> requestHandler) {
        registerVirtual(hostname, uriPattern, () -> new BasicServerExchangeHandler<>(requestHandler));
        return this;
    }

    /**
     * Adds the filter before the filter with the given name.
     */
    public final AsyncServerBootstrap addFilterBefore(final String existing, final String name, final AsyncFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.BEFORE, name, filterHandler, existing));
        return this;
    }

    /**
     * Adds the filter after the filter with the given name.
     */
    public final AsyncServerBootstrap addFilterAfter(final String existing, final String name, final AsyncFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.AFTER, name, filterHandler, existing));
        return this;
    }

    /**
     * Replace an existing filter with the given name with new filter.
     */
    public final AsyncServerBootstrap replaceFilter(final String existing, final AsyncFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.REPLACE, existing, filterHandler, existing));
        return this;
    }

    /**
     * Add an filter to the head of the processing list.
     */
    public final AsyncServerBootstrap addFilterFirst(final String name, final AsyncFilterHandler filterHandler) {
        Args.notNull(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.FIRST, name, filterHandler, null));
        return this;
    }

    /**
     * Add an filter to the tail of the processing list.
     */
    public final AsyncServerBootstrap addFilterLast(final String name, final AsyncFilterHandler filterHandler) {
        Args.notNull(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.LAST, name, filterHandler, null));
        return this;
    }

    public HttpAsyncServer create() {
        final RequestHandlerRegistry<Supplier<AsyncServerExchangeHandler>> registry = new RequestHandlerRegistry<>(
                canonicalHostName != null ? canonicalHostName : InetAddressUtils.getCanonicalLocalHostName(),
                () -> lookupRegistry != null ? lookupRegistry :
                        UriPatternType.newMatcher(UriPatternType.URI_PATTERN));
        for (final HandlerEntry<Supplier<AsyncServerExchangeHandler>> entry: handlerList) {
            registry.register(entry.hostname, entry.uriPattern, entry.handler);
        }

        final HandlerFactory<AsyncServerExchangeHandler> handlerFactory;
        if (!filters.isEmpty()) {
            final NamedElementChain<AsyncFilterHandler> filterChainDefinition = new NamedElementChain<>();
            filterChainDefinition.addLast(
                    new TerminalAsyncServerFilter(new DefaultAsyncResponseExchangeHandlerFactory(registry)),
                    StandardFilter.MAIN_HANDLER.name());
            filterChainDefinition.addFirst(
                    new AsyncServerExpectationFilter(),
                    StandardFilter.EXPECT_CONTINUE.name());

            for (final FilterEntry<AsyncFilterHandler> entry: filters) {
                switch (entry.postion) {
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
            handlerFactory = new DefaultAsyncResponseExchangeHandlerFactory(registry, handler -> new BasicAsyncServerExpectationDecorator(handler, exceptionCallback));
        }

        final ServerHttp1StreamDuplexerFactory streamHandlerFactory = new ServerHttp1StreamDuplexerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                handlerFactory,
                http1Config != null ? http1Config : Http1Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                connStrategy != null ? connStrategy : DefaultConnectionReuseStrategy.INSTANCE,
                DefaultHttpRequestParserFactory.INSTANCE,
                DefaultHttpResponseWriterFactory.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                streamListener);
        final IOEventHandlerFactory ioEventHandlerFactory = new ServerHttp1IOEventHandlerFactory(
                streamHandlerFactory,
                tlsStrategy,
                handshakeTimeout);
        return new HttpAsyncServer(ioEventHandlerFactory, ioReactorConfig, ioSessionDecorator, exceptionCallback,
                sessionListener);
    }

}
