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

import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnectionFactory;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolPolicy;
import org.apache.hc.core5.pool.StrictConnPool;

/**
 * @since 5.0
 */
public class RequesterBootstrap {

    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connReuseStrategy;
    private HttpConnectionFactory<? extends HttpClientConnection> connectFactory;
    private SSLSocketFactory sslSocketFactory;
    private int defaultMaxPerRoute;
    private int maxTotal;
    private long timeToLive;
    private TimeUnit timeUnit;
    private ConnPoolPolicy connPoolPolicy;
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

    public final RequesterBootstrap setConnectFactory(final HttpConnectionFactory<? extends HttpClientConnection> connectFactory) {
        this.connectFactory = connectFactory;
        return this;
    }

    public final RequesterBootstrap setSslSocketFactory(final SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
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

    public final RequesterBootstrap setTimeToLive(final long timeToLive, final TimeUnit timeUnit) {
        this.timeToLive = timeToLive;
        this.timeUnit = timeUnit;
        return this;
    }

    public final RequesterBootstrap setConnPoolPolicy(final ConnPoolPolicy connPoolPolicy) {
        this.connPoolPolicy = connPoolPolicy;
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
        final StrictConnPool<HttpHost, HttpClientConnection> connPool = new StrictConnPool<>(
                defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20,
                maxTotal > 0 ? maxTotal : 50,
                timeToLive, timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS,
                connPoolPolicy,
                connPoolListener);
        return new HttpRequester(
                requestExecutor,
                httpProcessor != null ? httpProcessor : HttpProcessors.client(),
                connPool,
                connectFactory != null ? connectFactory : DefaultBHttpClientConnectionFactory.INSTANCE,
                sslSocketFactory);
    }

}
