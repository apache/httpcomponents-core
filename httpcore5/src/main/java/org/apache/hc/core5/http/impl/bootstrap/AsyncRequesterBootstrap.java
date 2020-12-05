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

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandlerFactory;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
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
import org.apache.hc.core5.util.Timeout;

/**
 * {@link HttpAsyncRequester} bootstrap.
 *
 * @since 5.0
 */
public class AsyncRequesterBootstrap {

    private IOReactorConfig ioReactorConfig;
    private Http1Config http1Config;
    private CharCodingConfig charCodingConfig;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;
    private int defaultMaxPerRoute;
    private int maxTotal;
    private Timeout timeToLive;
    private PoolReusePolicy poolReusePolicy;
    private PoolConcurrencyPolicy poolConcurrencyPolicy;
    private TlsStrategy tlsStrategy;
    private Timeout handshakeTimeout;
    private Decorator<IOSession> ioSessionDecorator;
    private Callback<Exception> exceptionCallback;
    private IOSessionListener sessionListener;
    private Http1StreamListener streamListener;
    private ConnPoolListener<HttpHost> connPoolListener;

    private AsyncRequesterBootstrap() {
    }

    public static AsyncRequesterBootstrap bootstrap() {
        return new AsyncRequesterBootstrap();
    }

    /**
     * Sets I/O reactor configuration.
     */
    public final AsyncRequesterBootstrap setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets HTTP/1.1 protocol parameters
     */
    public final AsyncRequesterBootstrap setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    /**
     * Sets message char coding.
     */
    public final AsyncRequesterBootstrap setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final AsyncRequesterBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     */
    public final AsyncRequesterBootstrap setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    public final AsyncRequesterBootstrap setDefaultMaxPerRoute(final int defaultMaxPerRoute) {
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        return this;
    }

    public final AsyncRequesterBootstrap setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        return this;
    }

    public final AsyncRequesterBootstrap setTimeToLive(final Timeout timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    /**
     * Assigns {@link PoolReusePolicy} instance.
     */
    public final AsyncRequesterBootstrap setPoolReusePolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    /**
     * Assigns {@link PoolConcurrencyPolicy} instance.
     */
    @Experimental
    public final AsyncRequesterBootstrap setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    /**
     * Assigns {@link TlsStrategy} instance.
     */
    public final AsyncRequesterBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    public final AsyncRequesterBootstrap setTlsHandshakeTimeout(final Timeout handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
    }

    /**
     * Assigns {@link IOSession} {@link Decorator} instance.
     */
    public final AsyncRequesterBootstrap setIOSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Assigns {@link Exception} {@link Callback} instance.
     */
    public final AsyncRequesterBootstrap setExceptionCallback(final Callback<Exception> exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }

    /**
     * Assigns {@link IOSessionListener} instance.
     */
    public final AsyncRequesterBootstrap setIOSessionListener(final IOSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Assigns {@link Http1StreamListener} instance.
     */
    public final AsyncRequesterBootstrap setStreamListener(final Http1StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Assigns {@link ConnPoolListener} instance.
     */
    public final AsyncRequesterBootstrap setConnPoolListener(final ConnPoolListener<HttpHost> connPoolListener) {
        this.connPoolListener = connPoolListener;
        return this;
    }

    public HttpAsyncRequester create() {
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
        final ClientHttp1StreamDuplexerFactory streamDuplexerFactory = new ClientHttp1StreamDuplexerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.client(),
                http1Config != null ? http1Config : Http1Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                connStrategy,
                null,
                null,
                streamListener);
        final IOEventHandlerFactory ioEventHandlerFactory = new ClientHttp1IOEventHandlerFactory(
                streamDuplexerFactory,
                tlsStrategy != null ? tlsStrategy : new BasicClientTlsStrategy(),
                handshakeTimeout);
        return new HttpAsyncRequester(
                ioReactorConfig,
                ioEventHandlerFactory,
                ioSessionDecorator,
                exceptionCallback,
                sessionListener,
                connPool);
    }

}
