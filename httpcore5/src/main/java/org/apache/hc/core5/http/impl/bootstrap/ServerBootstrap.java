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
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnectionFactory;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpExpectationVerifier;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.Args;

/**
 * @since 4.4
 */
public class ServerBootstrap {

    private final List<HandlerEntry<HttpRequestHandler>> handlerList;
    private String canonicalHostName;
    private UriPatternType uriPatternType;
    private int listenerPort;
    private InetAddress localAddress;
    private SocketConfig socketConfig;
    private H1Config h1Config;
    private CharCodingConfig charCodingConfig;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private HttpResponseFactory<ClassicHttpResponse> responseFactory;
    private HttpExpectationVerifier expectationVerifier;
    private ServerSocketFactory serverSocketFactory;
    private SSLContext sslContext;
    private SSLServerSetupHandler sslSetupHandler;
    private HttpConnectionFactory<? extends DefaultBHttpServerConnection> connectionFactory;
    private ExceptionListener exceptionListener;
    private Http1StreamListener streamListener;

    private ServerBootstrap() {
        this.handlerList = new ArrayList<>();
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
    public final ServerBootstrap setH1Config(final H1Config h1Config) {
        this.h1Config = h1Config;
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
     * Assigns {@link UriPatternType} for handler registration.
     */
    public final ServerBootstrap  setUriPatternType(final UriPatternType uriPatternType) {
        this.uriPatternType = uriPatternType;
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
     * Assigns {@link HttpExpectationVerifier} instance.
     */
    public final ServerBootstrap setExpectationVerifier(final HttpExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
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
     * Assigns {@link SSLServerSetupHandler} instance.
     */
    public final ServerBootstrap setSslSetupHandler(final SSLServerSetupHandler sslSetupHandler) {
        this.sslSetupHandler = sslSetupHandler;
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

    public HttpServer create() {
        final RequestHandlerRegistry<HttpRequestHandler> requestHandlerRegistry = new RequestHandlerRegistry<>(
                canonicalHostName != null ? canonicalHostName : InetAddressUtils.getCanonicalLocalHostName(),
                uriPatternType);
        for (final HandlerEntry<HttpRequestHandler> entry: handlerList) {
            requestHandlerRegistry.register(entry.hostname, entry.uriPattern, entry.handler);
        }

        HttpProcessor httpProcessorCopy = this.httpProcessor;
        if (httpProcessorCopy == null) {
            httpProcessorCopy = HttpProcessors.server();
        }

        ConnectionReuseStrategy connStrategyCopy = this.connStrategy;
        if (connStrategyCopy == null) {
            connStrategyCopy = DefaultConnectionReuseStrategy.INSTANCE;
        }

        HttpResponseFactory<ClassicHttpResponse> responseFactoryCopy = this.responseFactory;
        if (responseFactoryCopy == null) {
            responseFactoryCopy = DefaultClassicHttpResponseFactory.INSTANCE;
        }

        final HttpService httpService = new HttpService(
                httpProcessorCopy, connStrategyCopy, responseFactoryCopy, requestHandlerRegistry,
                this.expectationVerifier, this.streamListener);

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
            connectionFactoryCopy = new DefaultBHttpServerConnectionFactory(scheme, this.h1Config, this.charCodingConfig);
        }

        ExceptionListener exceptionListenerCopy = this.exceptionListener;
        if (exceptionListenerCopy == null) {
            exceptionListenerCopy = ExceptionListener.NO_OP;
        }

        return new HttpServer(
                this.listenerPort > 0 ? this.listenerPort : 0,
                httpService,
                this.localAddress,
                this.socketConfig != null ? this.socketConfig : SocketConfig.DEFAULT,
                serverSocketFactoryCopy,
                connectionFactoryCopy,
                this.sslSetupHandler,
                exceptionListenerCopy);
    }

}
