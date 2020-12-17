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

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestWriterFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseParserFactory;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestHandlerRegistry;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.impl.nio.ClientH2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.ClientHttpProtocolNegotiatorFactory;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.nio.support.DefaultAsyncPushConsumerFactory;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.DefaultDisposalCallback;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link H2AsyncRequester} bootstrap.
 *
 * @since 5.0
 */
public class H2RequesterBootstrap {

    private final List<HandlerEntry<Supplier<AsyncPushConsumer>>> pushConsumerList;
    private UriPatternType uriPatternType;
    private IOReactorConfig ioReactorConfig;
    private HttpProcessor httpProcessor;
    private CharCodingConfig charCodingConfig;
    private HttpVersionPolicy versionPolicy;
    private H2Config h2Config;
    private Http1Config http1Config;
    private int defaultMaxPerRoute;
    private int maxTotal;
    private TimeValue timeToLive;
    private PoolReusePolicy poolReusePolicy;
    private PoolConcurrencyPolicy poolConcurrencyPolicy;
    private TlsStrategy tlsStrategy;
    private Timeout handshakeTimeout;
    private Decorator<IOSession> ioSessionDecorator;
    private Callback<Exception> exceptionCallback;
    private IOSessionListener sessionListener;
    private H2StreamListener streamListener;
    private Http1StreamListener http1StreamListener;
    private ConnPoolListener<HttpHost> connPoolListener;

    private H2RequesterBootstrap() {
        this.pushConsumerList = new ArrayList<>();
    }

    public static H2RequesterBootstrap bootstrap() {
        return new H2RequesterBootstrap();
    }

    /**
     * Sets I/O reactor configuration.
     */
    public final H2RequesterBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final H2RequesterBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Sets HTTP protocol version policy
     */
    public final H2RequesterBootstrap setVersionPolicy(final HttpVersionPolicy versionPolicy) {
        this.versionPolicy = versionPolicy;
        return this;
    }

    /**
     * Sets HTTP/2 protocol parameters
     */
    public final H2RequesterBootstrap setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets HTTP/1.1 protocol parameters
     */
    public final H2RequesterBootstrap setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    /**
     * Sets message char coding.
     */
    public final H2RequesterBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    public final H2RequesterBootstrap setDefaultMaxPerRoute(final int defaultMaxPerRoute) {
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        return this;
    }

    public final H2RequesterBootstrap setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        return this;
    }

    public final H2RequesterBootstrap setTimeToLive(final TimeValue timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    /**
     * Assigns {@link PoolReusePolicy} instance.
     */
    public final H2RequesterBootstrap setPoolReusePolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    /**
     * Assigns {@link PoolConcurrencyPolicy} instance.
     */
    @Experimental
    public final H2RequesterBootstrap setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    /**
     * Assigns {@link TlsStrategy} instance.
     */
    public final H2RequesterBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    public final H2RequesterBootstrap setHandshakeTimeout(final Timeout handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
    }

    /**
     * Assigns {@link IOSession} {@link Decorator} instance.
     */
    public final H2RequesterBootstrap setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Assigns {@link Exception} {@link Callback} instance.
     */
    public final H2RequesterBootstrap setExceptionCallback(final Callback<Exception> exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }

    /**
     * Assigns {@link IOSessionListener} instance.
     */
    public final H2RequesterBootstrap setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Assigns {@link H2StreamListener} instance.
     */
    public final H2RequesterBootstrap setStreamListener(final H2StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Assigns {@link Http1StreamListener} instance.
     */
    public final H2RequesterBootstrap setStreamListener(final Http1StreamListener http1StreamListener) {
        this.http1StreamListener = http1StreamListener;
        return this;
    }

    /**
     * Assigns {@link ConnPoolListener} instance.
     */
    public final H2RequesterBootstrap setConnPoolListener(final ConnPoolListener<HttpHost> connPoolListener) {
        this.connPoolListener = connPoolListener;
        return this;
    }

    /**
     * Assigns {@link UriPatternType} for handler registration.
     */
    public final H2RequesterBootstrap setUriPatternType(final UriPatternType uriPatternType) {
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
    public final H2RequesterBootstrap register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
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
    public final H2RequesterBootstrap registerVirtual(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushConsumerList.add(new HandlerEntry<>(hostname, uriPattern, supplier));
        return this;
    }

    public H2AsyncRequester create() {
        final ManagedConnPool<HttpHost, IOSession> connPool;
        switch (poolConcurrencyPolicy != null ? poolConcurrencyPolicy : PoolConcurrencyPolicy.STRICT) {
            case LAX:
                connPool = new LaxConnPool<>(
                        defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20,
                        timeToLive,
                        poolReusePolicy,
                        new DefaultDisposalCallback<>(),
                        connPoolListener);
                break;
            case STRICT:
            default:
                connPool = new StrictConnPool<>(
                        defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20,
                        maxTotal > 0 ? maxTotal : 50,
                        timeToLive,
                        poolReusePolicy,
                        new DefaultDisposalCallback<>(),
                        connPoolListener);
                break;
        }
        final RequestHandlerRegistry<Supplier<AsyncPushConsumer>> registry = new RequestHandlerRegistry<>(uriPatternType);
        for (final HandlerEntry<Supplier<AsyncPushConsumer>> entry: pushConsumerList) {
            registry.register(entry.hostname, entry.uriPattern, entry.handler);
        }

        final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory = new ClientH2StreamMultiplexerFactory(
                httpProcessor != null ? httpProcessor : H2Processors.client(),
                new DefaultAsyncPushConsumerFactory(registry),
                h2Config != null ? h2Config : H2Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                streamListener);

        final TlsStrategy actualTlsStrategy = tlsStrategy != null ? tlsStrategy : new H2ClientTlsStrategy();

        final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory = new ClientHttp1StreamDuplexerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.client(),
                http1Config != null ? http1Config : Http1Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                new DefaultHttpResponseParserFactory(http1Config),
                DefaultHttpRequestWriterFactory.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                http1StreamListener);

        final IOEventHandlerFactory ioEventHandlerFactory = new ClientHttpProtocolNegotiatorFactory(
                http1StreamHandlerFactory,
                http2StreamHandlerFactory,
                versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE,
                actualTlsStrategy,
                handshakeTimeout);

        return new H2AsyncRequester(
                versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE,
                ioReactorConfig,
                ioEventHandlerFactory,
                ioSessionDecorator,
                exceptionCallback,
                sessionListener,
                connPool,
                actualTlsStrategy,
                handshakeTimeout);
    }

}
