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

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.Http2Processors;
import org.apache.hc.core5.http2.impl.nio.ClientHttp2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.Http2OnlyClientProtocolNegotiator;
import org.apache.hc.core5.http2.impl.nio.Http2StreamListener;
import org.apache.hc.core5.http2.nio.support.DefaultAsyncPushConsumerFactory;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;

/**
 * {@link Http2MultiplexingRequester} bootstrap.
 *
 * @since 5.0
 */
public class Http2MultiplexingRequesterBootstrap {

    private final List<HandlerEntry<Supplier<AsyncPushConsumer>>> pushConsumerList;
    private UriPatternType uriPatternType;
    private IOReactorConfig ioReactorConfig;
    private HttpProcessor httpProcessor;
    private CharCodingConfig charCodingConfig;
    private H2Config h2Config;
    private TlsStrategy tlsStrategy;
    private boolean strictALPNHandshake;
    private Decorator<IOSession> ioSessionDecorator;
    private IOSessionListener sessionListener;
    private Http2StreamListener streamListener;

    private Http2MultiplexingRequesterBootstrap() {
        this.pushConsumerList = new ArrayList<>();
    }

    public static Http2MultiplexingRequesterBootstrap bootstrap() {
        return new Http2MultiplexingRequesterBootstrap();
    }

    /**
     * Sets I/O reactor configuration.
     */
    public final Http2MultiplexingRequesterBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final Http2MultiplexingRequesterBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Sets HTTP/2 protocol parameters
     */
    public final Http2MultiplexingRequesterBootstrap setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets message char coding.
     */
    public final Http2MultiplexingRequesterBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Assigns {@link TlsStrategy} instance.
     */
    public final Http2MultiplexingRequesterBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }


    public final Http2MultiplexingRequesterBootstrap setStrictALPNHandshake(final boolean strictALPNHandshake) {
        this.strictALPNHandshake = strictALPNHandshake;
        return this;
    }

    /**
     * Assigns {@link IOSession} {@link Decorator} instance.
     */
    public final Http2MultiplexingRequesterBootstrap setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Assigns {@link IOSessionListener} instance.
     */
    public final Http2MultiplexingRequesterBootstrap setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Assigns {@link Http2StreamListener} instance.
     */
    public final Http2MultiplexingRequesterBootstrap setStreamListener(final Http2StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Assigns {@link UriPatternType} for handler registration.
     */
    public final Http2MultiplexingRequesterBootstrap setUriPatternType(final UriPatternType uriPatternType) {
        this.uriPatternType = uriPatternType;
        return this;
    }

    /**
     * Registers the given {@link AsyncPushConsumer} {@link Supplier} as a default handler for URIs
     * matching the given pattern.
     *
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     */
    public final Http2MultiplexingRequesterBootstrap register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushConsumerList.add(new HandlerEntry<>(null, uriPattern, supplier));
        return this;
    }

    /**
     * Registers the given {@link AsyncPushConsumer} {@link Supplier} as a handler for URIs
     * matching the given host and the pattern.
     *
     * @param hostname the host name
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     */
    public final Http2MultiplexingRequesterBootstrap registerVirtual(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushConsumerList.add(new HandlerEntry<>(hostname, uriPattern, supplier));
        return this;
    }

    public Http2MultiplexingRequester create() {
        final RequestHandlerRegistry<Supplier<AsyncPushConsumer>> registry = new RequestHandlerRegistry<>(uriPatternType);
        for (final HandlerEntry<Supplier<AsyncPushConsumer>> entry: pushConsumerList) {
            registry.register(entry.hostname, entry.uriPattern, entry.handler);
        }
        final ClientHttp2StreamMultiplexerFactory http2StreamHandlerFactory = new ClientHttp2StreamMultiplexerFactory(
                httpProcessor != null ? httpProcessor : Http2Processors.client(),
                new DefaultAsyncPushConsumerFactory(registry),
                h2Config != null ? h2Config : H2Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                streamListener);
        return new Http2MultiplexingRequester(
                ioReactorConfig,
                new IOEventHandlerFactory() {

                    @Override
                    public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
                        return new Http2OnlyClientProtocolNegotiator(ioSession, http2StreamHandlerFactory, strictALPNHandshake);
                    }

                },
                ioSessionDecorator,
                sessionListener,
                DefaultAddressResolver.INSTANCE,
                tlsStrategy != null ? tlsStrategy : new H2ClientTlsStrategy());
    }

}
