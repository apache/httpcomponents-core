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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnectionFactory;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.ssl.DefaultTlsSetupHandler;
import org.apache.hc.core5.http.io.ssl.SSLSessionVerifier;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.DefaultDisposalCallback;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link HttpRequester} bootstrap.
 *
 * @since 5.0
 */
public class RequesterBootstrap {

    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connReuseStrategy;
    private SocketConfig socketConfig;
    private HttpConnectionFactory<? extends HttpClientConnection> connectFactory;
    private SSLSocketFactory sslSocketFactory;
    private Callback<SSLParameters> sslSetupHandler;
    private SSLSessionVerifier sslSessionVerifier;
    private int defaultMaxPerRoute;
    private int maxTotal;
    private Timeout timeToLive;
    private PoolReusePolicy poolReusePolicy;
    private PoolConcurrencyPolicy poolConcurrencyPolicy;
    private Http1StreamListener streamListener;
    private ConnPoolListener<HttpHost> connPoolListener;

    private RequesterBootstrap() {
    }

    public static RequesterBootstrap bootstrap() {
        return new RequesterBootstrap();
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final RequesterBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     */
    public final RequesterBootstrap setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connReuseStrategy = connStrategy;
        return this;
    }

    /**
     * Sets socket configuration.
     */
    public final RequesterBootstrap setSocketConfig(final SocketConfig socketConfig) {
        this.socketConfig = socketConfig;
        return this;
    }

    public final RequesterBootstrap setConnectionFactory(final HttpConnectionFactory<? extends HttpClientConnection> connectFactory) {
        this.connectFactory = connectFactory;
        return this;
    }

    public final RequesterBootstrap setSslContext(final SSLContext sslContext) {
        this.sslSocketFactory = sslContext != null ? sslContext.getSocketFactory() : null;
        return this;
    }

    public final RequesterBootstrap setSslSocketFactory(final SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        return this;
    }

    /**
     * Assigns {@link Callback} for {@link SSLParameters}.
     */
    public final RequesterBootstrap setSslSetupHandler(final Callback<SSLParameters> sslSetupHandler) {
        this.sslSetupHandler = sslSetupHandler;
        return this;
    }

    /**
     * Assigns {@link SSLSessionVerifier} instance.
     */
    public final RequesterBootstrap setSslSessionVerifier(final SSLSessionVerifier sslSessionVerifier) {
        this.sslSessionVerifier = sslSessionVerifier;
        return this;
    }

    public final RequesterBootstrap setDefaultMaxPerRoute(final int defaultMaxPerRoute) {
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        return this;
    }

    public final RequesterBootstrap setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        return this;
    }

    public final RequesterBootstrap setTimeToLive(final Timeout timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    public final RequesterBootstrap setPoolReusePolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    @Experimental
    public final RequesterBootstrap setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    public final RequesterBootstrap setStreamListener(final Http1StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    public final RequesterBootstrap setConnPoolListener(final ConnPoolListener<HttpHost> connPoolListener) {
        this.connPoolListener = connPoolListener;
        return this;
    }

    public HttpRequester create() {
        final HttpRequestExecutor requestExecutor = new HttpRequestExecutor(
                HttpRequestExecutor.DEFAULT_WAIT_FOR_CONTINUE,
                connReuseStrategy != null ? connReuseStrategy : DefaultConnectionReuseStrategy.INSTANCE,
                streamListener);
        final ManagedConnPool<HttpHost, HttpClientConnection> connPool;
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
        return new HttpRequester(
                requestExecutor,
                httpProcessor != null ? httpProcessor : HttpProcessors.client(),
                connPool,
                socketConfig != null ? socketConfig : SocketConfig.DEFAULT,
                connectFactory != null ? connectFactory : new DefaultBHttpClientConnectionFactory(
                        Http1Config.DEFAULT, CharCodingConfig.DEFAULT),
                sslSocketFactory,
                sslSetupHandler != null ? sslSetupHandler : new DefaultTlsSetupHandler(),
                sslSessionVerifier,
                DefaultAddressResolver.INSTANCE);
    }

}
