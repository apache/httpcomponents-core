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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.NamedElementChain;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnectionFactory;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.ssl.DefaultTlsSetupHandler;
import org.apache.hc.core5.http.io.support.BasicHttpServerExpectationDecorator;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.io.support.HttpServerExpectationFilter;
import org.apache.hc.core5.http.io.support.HttpServerFilterChainElement;
import org.apache.hc.core5.http.io.support.HttpServerFilterChainRequestHandler;
import org.apache.hc.core5.http.io.support.TerminalServerFilter;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.LookupRegistry;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.Args;

/**
 * {@link HttpServer} bootstrap.
 *
 * @since 4.4
 */
public class ServerBootstrap {

    private final List<HandlerEntry<HttpRequestHandler>> handlerList;
    private final List<FilterEntry<HttpFilterHandler>> filters;
    private String canonicalHostName;
    private LookupRegistry<HttpRequestHandler> lookupRegistry;
    private int listenerPort;
    private InetAddress localAddress;
    private SocketConfig socketConfig;
    private Http1Config http1Config;
    private CharCodingConfig charCodingConfig;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private HttpResponseFactory<ClassicHttpResponse> responseFactory;
    private ServerSocketFactory serverSocketFactory;
    private SSLContext sslContext;
    private Callback<SSLParameters> sslSetupHandler;
    private HttpConnectionFactory<? extends DefaultBHttpServerConnection> connectionFactory;
    private ExceptionListener exceptionListener;
    private Http1StreamListener streamListener;

    private ServerBootstrap() {
        this.handlerList = new ArrayList<>();
        this.filters = new ArrayList<>();
    }

    public static ServerBootstrap bootstrap() {
        return new ServerBootstrap();
    }

    /**
     * Sets canonical name (fully qualified domain name) of the server.
     *
     * @since 5.0
     */
    public final ServerBootstrap setCanonicalHostName(final String canonicalHostName) {
        this.canonicalHostName = canonicalHostName;
        return this;
    }

    /**
     * Sets listener port number.
     */
    public final ServerBootstrap setListenerPort(final int listenerPort) {
        this.listenerPort = listenerPort;
        return this;
    }

    /**
     * Assigns local interface for the listener.
     */
    public final ServerBootstrap setLocalAddress(final InetAddress localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    /**
     * Sets socket configuration.
     */
    public final ServerBootstrap setSocketConfig(final SocketConfig socketConfig) {
        this.socketConfig = socketConfig;
        return this;
    }

    /**
     * Sets connection configuration.
     */
    public final ServerBootstrap setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    /**
     * Sets connection configuration.
     */
    public final ServerBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final ServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     */
    public final ServerBootstrap setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    /**
     * Assigns {@link HttpResponseFactory} instance.
     */
    public final ServerBootstrap setResponseFactory(final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        this.responseFactory = responseFactory;
        return this;
    }

    /**
     * Assigns {@link LookupRegistry} instance.
     */
    public final ServerBootstrap setLookupRegistry(final LookupRegistry<HttpRequestHandler> lookupRegistry) {
        this.lookupRegistry = lookupRegistry;
        return this;
    }

    /**
     * Registers the given {@link HttpRequestHandler} as a default handler for URIs
     * matching the given pattern.
     *
     * @param uriPattern the pattern to register the handler for.
     * @param requestHandler the handler.
     */
    public final ServerBootstrap register(final String uriPattern, final HttpRequestHandler requestHandler) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(requestHandler, "Supplier");
        handlerList.add(new HandlerEntry<>(null, uriPattern, requestHandler));
        return this;
    }

    /**
     * Registers the given {@link HttpRequestHandler} as a handler for URIs
     * matching the given host and the pattern.
     *
     * @param hostname
     * @param uriPattern the pattern to register the handler for.
     * @param requestHandler the handler.
     */
    public final ServerBootstrap registerVirtual(final String hostname, final String uriPattern, final HttpRequestHandler requestHandler) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(requestHandler, "Supplier");
        handlerList.add(new HandlerEntry<>(hostname, uriPattern, requestHandler));
        return this;
    }

    /**
     * Assigns {@link HttpConnectionFactory} instance.
     */
    public final ServerBootstrap setConnectionFactory(
            final HttpConnectionFactory<? extends DefaultBHttpServerConnection> connectionFactory) {
        this.connectionFactory = connectionFactory;
        return this;
    }

    /**
     * Assigns {@link javax.net.ServerSocketFactory} instance.
     */
    public final ServerBootstrap setServerSocketFactory(final ServerSocketFactory serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
        return this;
    }

    /**
     * Assigns {@link javax.net.ssl.SSLContext} instance.
     * <p>
     * Please note this value can be overridden by the {@link #setServerSocketFactory(
     *   javax.net.ServerSocketFactory)} method.
     */
    public final ServerBootstrap setSslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Assigns {@link Callback} for {@link SSLParameters}.
     */
    public final ServerBootstrap setSslSetupHandler(final Callback<SSLParameters> sslSetupHandler) {
        this.sslSetupHandler = sslSetupHandler;
        return this;
    }

    /**
     * Assigns {@link ExceptionListener} instance.
     */
    public final ServerBootstrap setExceptionListener(final ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    /**
     * Assigns {@link ExceptionListener} instance.
     */
    public final ServerBootstrap setStreamListener(final Http1StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Adds the filter before the filter with the given name.
     */
    public final ServerBootstrap addFilterBefore(final String existing, final String name, final HttpFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.BEFORE, name, filterHandler, existing));
        return this;
    }

    /**
     * Adds the filter after the filter with the given name.
     */
    public final ServerBootstrap addFilterAfter(final String existing, final String name, final HttpFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.AFTER, name, filterHandler, existing));
        return this;
    }

    /**
     * Replace an existing filter with the given name with new filter.
     */
    public final ServerBootstrap replaceFilter(final String existing, final HttpFilterHandler filterHandler) {
        Args.notBlank(existing, "Existing");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.REPLACE, existing, filterHandler, existing));
        return this;
    }

    /**
     * Add an filter to the head of the processing list.
     */
    public final ServerBootstrap addFilterFirst(final String name, final HttpFilterHandler filterHandler) {
        Args.notNull(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.FIRST, name, filterHandler, null));
        return this;
    }

    /**
     * Add an filter to the tail of the processing list.
     */
    public final ServerBootstrap addFilterLast(final String name, final HttpFilterHandler filterHandler) {
        Args.notNull(name, "Name");
        Args.notNull(filterHandler, "Filter handler");
        filters.add(new FilterEntry<>(FilterEntry.Postion.LAST, name, filterHandler, null));
        return this;
    }

    public HttpServer create() {
        final RequestHandlerRegistry<HttpRequestHandler> handlerRegistry = new RequestHandlerRegistry<>(
                canonicalHostName != null ? canonicalHostName : InetAddressUtils.getCanonicalLocalHostName(),
                () -> lookupRegistry != null ? lookupRegistry :
                        UriPatternType.newMatcher(UriPatternType.URI_PATTERN));
        for (final HandlerEntry<HttpRequestHandler> entry: handlerList) {
            handlerRegistry.register(entry.hostname, entry.uriPattern, entry.handler);
        }

        final HttpServerRequestHandler requestHandler;
        if (!filters.isEmpty()) {
            final NamedElementChain<HttpFilterHandler> filterChainDefinition = new NamedElementChain<>();
            filterChainDefinition.addLast(
                    new TerminalServerFilter(
                            handlerRegistry,
                            this.responseFactory != null ? this.responseFactory : DefaultClassicHttpResponseFactory.INSTANCE),
                    StandardFilter.MAIN_HANDLER.name());
            filterChainDefinition.addFirst(
                    new HttpServerExpectationFilter(),
                    StandardFilter.EXPECT_CONTINUE.name());

            for (final FilterEntry<HttpFilterHandler> entry: filters) {
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
                        // Don't add last, after TerminalServerFilter, as that does not delegate to the chain
                        // Instead, add the filter just before it, making it effectively the last filter
                        filterChainDefinition.addBefore(StandardFilter.MAIN_HANDLER.name(), entry.filterHandler, entry.name);
                        break;
                }
            }

            NamedElementChain<HttpFilterHandler>.Node current = filterChainDefinition.getLast();
            HttpServerFilterChainElement filterChain = null;
            while (current != null) {
                filterChain = new HttpServerFilterChainElement(current.getValue(), filterChain);
                current = current.getPrevious();
            }
            requestHandler = new HttpServerFilterChainRequestHandler(filterChain);
        } else {
            requestHandler = new BasicHttpServerExpectationDecorator(new BasicHttpServerRequestHandler(
                    handlerRegistry,
                    this.responseFactory != null ? this.responseFactory : DefaultClassicHttpResponseFactory.INSTANCE));
        }

        final HttpService httpService = new HttpService(
                this.httpProcessor != null ? this.httpProcessor : HttpProcessors.server(),
                requestHandler,
                this.connStrategy != null ? this.connStrategy : DefaultConnectionReuseStrategy.INSTANCE,
                this.streamListener);

        ServerSocketFactory serverSocketFactoryCopy = this.serverSocketFactory;
        if (serverSocketFactoryCopy == null) {
            if (this.sslContext != null) {
                serverSocketFactoryCopy = this.sslContext.getServerSocketFactory();
            } else {
                serverSocketFactoryCopy = ServerSocketFactory.getDefault();
            }
        }

        HttpConnectionFactory<? extends DefaultBHttpServerConnection> connectionFactoryCopy = this.connectionFactory;
        if (connectionFactoryCopy == null) {
            final String scheme = serverSocketFactoryCopy instanceof SSLServerSocketFactory ? URIScheme.HTTPS.id : URIScheme.HTTP.id;
            connectionFactoryCopy = new DefaultBHttpServerConnectionFactory(scheme, this.http1Config, this.charCodingConfig);
        }

        return new HttpServer(
                this.listenerPort > 0 ? this.listenerPort : 0,
                httpService,
                this.localAddress,
                this.socketConfig != null ? this.socketConfig : SocketConfig.DEFAULT,
                serverSocketFactoryCopy,
                connectionFactoryCopy,
                sslSetupHandler != null ? sslSetupHandler : new DefaultTlsSetupHandler(),
                this.exceptionListener != null ? this.exceptionListener : ExceptionListener.NO_OP);
    }

}
