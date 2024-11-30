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
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.impl.nio.ClientH2PrefaceHandler;
import org.apache.hc.core5.http2.impl.nio.ClientH2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.nio.support.DefaultAsyncPushConsumerFactory;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorMetricsListener;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Args;

/**
 * {@link H2MultiplexingRequester} bootstrap.
 *
 * @since 5.0
 */
public class H2MultiplexingRequesterBootstrap {

    private final List<RequestRouter.Entry<Supplier<AsyncPushConsumer>>> routeEntries;
    private UriPatternType uriPatternType;
    private IOReactorConfig ioReactorConfig;
    private HttpProcessor httpProcessor;
    private CharCodingConfig charCodingConfig;
    private H2Config h2Config;
    private TlsStrategy tlsStrategy;
    private boolean strictALPNHandshake;
    private Decorator<IOSession> ioSessionDecorator;
    private Callback<Exception> exceptionCallback;
    private IOSessionListener sessionListener;
    private H2StreamListener streamListener;

    private IOReactorMetricsListener threadPoolListener;

    private H2MultiplexingRequesterBootstrap() {
        this.routeEntries = new ArrayList<>();
    }

    public static H2MultiplexingRequesterBootstrap bootstrap() {
        return new H2MultiplexingRequesterBootstrap();
    }

    /**
     * Sets I/O reactor configuration.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets {@link HttpProcessor} instance.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Sets HTTP/2 protocol parameters
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets message char coding.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Sets {@link TlsStrategy} instance.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    public final H2MultiplexingRequesterBootstrap setStrictALPNHandshake(final boolean strictALPNHandshake) {
        this.strictALPNHandshake = strictALPNHandshake;
        return this;
    }

    /**
     * Sets {@link IOSession} {@link Decorator} instance.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Sets {@link Exception} {@link Callback} instance.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setExceptionCallback(final Callback<Exception> exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }

    /**
     * Sets {@link IOSessionListener} instance.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Sets {@link IOReactorMetricsListener} instance.
     *
     * @return this instance.
     * @since 5.4
     */
    public final H2MultiplexingRequesterBootstrap setIOReactorMetricsListener(final IOReactorMetricsListener threadPoolListener) {
        this.threadPoolListener = threadPoolListener;
        return this;
    }

    /**
     * Sets {@link H2StreamListener} instance.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setStreamListener(final H2StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Sets {@link UriPatternType} for handler registration.
     *
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap setUriPatternType(final UriPatternType uriPatternType) {
        this.uriPatternType = uriPatternType;
        return this;
    }

    /**
     * Registers the given {@link AsyncPushConsumer} {@link Supplier} as a default handler for URIs
     * matching the given pattern.
     *
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     * @return this instance.
     */
    public final H2MultiplexingRequesterBootstrap register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Push consumer supplier");
        routeEntries.add(new RequestRouter.Entry<>(uriPattern, supplier));
        return this;
    }

    /**
     * Registers the given {@link AsyncPushConsumer} {@link Supplier} as a handler for URIs
     * matching the given host and the pattern.
     *
     * @param hostname the host name
     * @param uriPattern the pattern to register the handler for.
     * @param supplier the handler supplier.
     * @return this instance.
     *
     * @since 5.3
     */
    public final H2MultiplexingRequesterBootstrap register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Push consumer supplier");
        routeEntries.add(new RequestRouter.Entry<>(hostname, uriPattern, supplier));
        return this;
    }

    /**
     * @return this instance.
     * @deprecated Use {@link #register(String, String, Supplier)}.
     */
    @Deprecated
    public final H2MultiplexingRequesterBootstrap registerVirtual(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        return register(hostname, uriPattern, supplier);
    }

    public H2MultiplexingRequester create() {
        final RequestRouter<Supplier<AsyncPushConsumer>> requestRouter = RequestRouter.create(
                null, uriPatternType, routeEntries, RequestRouter.LOCAL_AUTHORITY_RESOLVER, null);
        final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory = new ClientH2StreamMultiplexerFactory(
                httpProcessor != null ? httpProcessor : H2Processors.client(),
                new DefaultAsyncPushConsumerFactory(requestRouter),
                h2Config != null ? h2Config : H2Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                streamListener);
        return new H2MultiplexingRequester(
                ioReactorConfig,
                (ioSession, attachment) -> new ClientH2PrefaceHandler(ioSession, http2StreamHandlerFactory, strictALPNHandshake),
                ioSessionDecorator,
                exceptionCallback,
                sessionListener,
                DefaultAddressResolver.INSTANCE,
                tlsStrategy != null ? tlsStrategy : new H2ClientTlsStrategy(),
                threadPoolListener,
                null);
    }

}
