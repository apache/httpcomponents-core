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

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseWriterFactory;
import org.apache.hc.core5.http.impl.nio.ServerHttp1IOEventHandlerFactory;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.ssl.BasicServerTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.RequestConsumerSupplier;
import org.apache.hc.core5.http.nio.support.ResponseHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.util.Args;

/**
 * @since 4.4
 */
public class AsyncServerBootstrap {

    private final List<HandlerEntry> handlerList;
    private String canonicalHostName;
    private IOReactorConfig ioReactorConfig;
    private H1Config h1Config;
    private CharCodingConfig charCodingConfig;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private TlsStrategy tlsStrategy;
    private ExceptionListener exceptionListener;
    private ConnectionListener connectionListener;
    private Http1StreamListener streamListener;

    private AsyncServerBootstrap() {
        this.handlerList = new ArrayList<>();
    }

    public static AsyncServerBootstrap bootstrap() {
        return new AsyncServerBootstrap();
    }

    /**
     * Sets canonical name (fully qualified domain name) of the server.
     *
     * @since 5.0
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
    public final AsyncServerBootstrap setH1Config(final H1Config h1Config) {
        this.h1Config = h1Config;
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
     * Assigns {@link ExceptionListener} instance.
     */
    public final AsyncServerBootstrap setExceptionListener(final ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    /**
     * Assigns {@link ConnectionListener} instance.
     */
    public final AsyncServerBootstrap setConnectionListener(final ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
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

    public final AsyncServerBootstrap register(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        handlerList.add(new HandlerEntry(null, uriPattern, supplier));
        return this;
    }

    public final AsyncServerBootstrap registerVirtual(final String hostname, final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        handlerList.add(new HandlerEntry(hostname, uriPattern, supplier));
        return this;
    }

    public final <T> AsyncServerBootstrap register(
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

    public final <T> AsyncServerBootstrap registerVirtual(
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
        final ServerHttp1StreamDuplexerFactory streamHandlerFactory = new ServerHttp1StreamDuplexerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.server(),
                exchangeHandlerFactory,
                h1Config != null ? h1Config : H1Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                connStrategy != null ? connStrategy : DefaultConnectionReuseStrategy.INSTANCE,
                DefaultHttpRequestParserFactory.INSTANCE,
                DefaultHttpResponseWriterFactory.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                connectionListener,
                streamListener);
        final IOEventHandlerFactory ioEventHandlerFactory = new ServerHttp1IOEventHandlerFactory(
                streamHandlerFactory,
                tlsStrategy != null ? tlsStrategy : new BasicServerTlsStrategy(new int[] {443, 8443}));
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
