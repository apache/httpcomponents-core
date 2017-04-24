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

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerExchangeHandlerRegistry;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseWriterFactory;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.RequestConsumerSupplier;
import org.apache.hc.core5.http.nio.support.ResponseHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.Http2Processors;
import org.apache.hc.core5.http2.impl.nio.Http2StreamListener;
import org.apache.hc.core5.http2.impl.nio.ServerHttp2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.ServerHttpProtocolNegotiatorFactory;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public class H2ServerBootstrap {

    private final List<HandlerEntry> handlerList;
    private String canonicalHostName;
    private IOReactorConfig ioReactorConfig;
    private HttpProcessor httpProcessor;
    private CharCodingConfig charCodingConfig;
    private HttpVersionPolicy versionPolicy;
    private H2Config h2Config;
    private H1Config h1Config;
    private TlsStrategy tlsStrategy;
    private ExceptionListener exceptionListener;
    private ConnectionListener connectionListener;
    private Http2StreamListener http2StreamListener;
    private Http1StreamListener http1StreamListener;

    private H2ServerBootstrap() {
        this.handlerList = new ArrayList<>();
    }

    public static H2ServerBootstrap bootstrap() {
        return new H2ServerBootstrap();
    }

    /**
     * Sets canonical name (fully qualified domain name) of the server.
     *
     * @since 5.0
     */
    public final H2ServerBootstrap setCanonicalHostName(final String canonicalHostName) {
        this.canonicalHostName = canonicalHostName;
        return this;
    }

    /**
     * Sets I/O reactor configuration.
     */
    public final H2ServerBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final H2ServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Sets HTTP protocol version policy
     */
    public final H2ServerBootstrap setVersionPolicy(final HttpVersionPolicy versionPolicy) {
        this.versionPolicy = versionPolicy;
        return this;
    }

    /**
     * Sets HTTP/2 protocol parameters
     */
    public final H2ServerBootstrap setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets HTTP/1.1 protocol parameters
     */
    public final H2ServerBootstrap setH1Config(final H1Config h1Config) {
        this.h1Config = h1Config;
        return this;
    }

    /**
     * Sets message char coding.
     */
    public final H2ServerBootstrap setCharset(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Assigns {@link TlsStrategy} instance.
     */
    public final H2ServerBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Assigns {@link ExceptionListener} instance.
     */
    public final H2ServerBootstrap setExceptionListener(final ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    /**
     * Assigns {@link ConnectionListener} instance.
     */
    public final H2ServerBootstrap setConnectionListener(final ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
        return this;
    }

    /**
     * Assigns {@link Http2StreamListener} instance.
     */
    public final H2ServerBootstrap setStreamListener(final Http2StreamListener http2StreamListener) {
        this.http2StreamListener = http2StreamListener;
        return this;
    }

    /**
     * Assigns {@link Http1StreamListener} instance.
     */
    public final H2ServerBootstrap setStreamListener(final Http1StreamListener http1StreamListener) {
        this.http1StreamListener = http1StreamListener;
        return this;
    }

    public final H2ServerBootstrap register(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        handlerList.add(new HandlerEntry(null, uriPattern, supplier));
        return this;
    }

    public final H2ServerBootstrap registerVirtual(final String hostname, final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        handlerList.add(new HandlerEntry(hostname, uriPattern, supplier));
        return this;
    }

    public final <T> H2ServerBootstrap register(
            final String uriPattern,
            final RequestConsumerSupplier<T> consumerSupplier,
            final ResponseHandler<T> responseHandler) {
        register(uriPattern, new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicServerExchangeHandler<>(consumerSupplier, responseHandler);
            }

        });
        return this;
    }

    public final <T> H2ServerBootstrap registerVirtual(
            final String hostname,
            final String uriPattern,
            final RequestConsumerSupplier<T> consumerSupplier,
            final ResponseHandler<T> responseHandler) {
        registerVirtual(hostname, uriPattern, new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicServerExchangeHandler<>(consumerSupplier, responseHandler);
            }

        });
        return this;
    }

    public HttpAsyncServer create() {
        final AsyncServerExchangeHandlerRegistry exchangeHandlerFactory = new AsyncServerExchangeHandlerRegistry(
                canonicalHostName != null ? canonicalHostName : InetAddressUtils.getCanonicalLocalHostName());
        for (final HandlerEntry entry: handlerList) {
            exchangeHandlerFactory.register(entry.hostname, entry.uriPattern, entry.supplier);
        }
        final ServerHttp2StreamMultiplexerFactory http2StreamHandlerFactory = new ServerHttp2StreamMultiplexerFactory(
                httpProcessor != null ? httpProcessor : Http2Processors.server(),
                exchangeHandlerFactory,
                h2Config != null ? h2Config : H2Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                connectionListener,
                http2StreamListener);
        final ServerHttp1StreamDuplexerFactory http1StreamHandlerFactory = new ServerHttp1StreamDuplexerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                exchangeHandlerFactory,
                h1Config != null ? h1Config : H1Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                DefaultHttpRequestParserFactory.INSTANCE,
                DefaultHttpResponseWriterFactory.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                connectionListener,
                http1StreamListener);
        final IOEventHandlerFactory ioEventHandlerFactory = new ServerHttpProtocolNegotiatorFactory(
                http1StreamHandlerFactory,
                http2StreamHandlerFactory,
                versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE,
                tlsStrategy != null ? tlsStrategy : new H2ServerTlsStrategy(new int[] {443, 8443}),
                connectionListener);
        try {
            return new HttpAsyncServer(
                    ioEventHandlerFactory,
                    ioReactorConfig,
                    exceptionListener);
        } catch (final IOReactorException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static class HandlerEntry {

        final String hostname;
        final String uriPattern;
        final Supplier<AsyncServerExchangeHandler> supplier;

        public HandlerEntry(final String hostname, final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
            this.hostname = hostname;
            this.uriPattern = uriPattern;
            this.supplier = supplier;
        }

    }

}
