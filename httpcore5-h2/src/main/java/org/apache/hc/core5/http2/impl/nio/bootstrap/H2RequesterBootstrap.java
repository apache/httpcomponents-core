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
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.Http2Processors;
import org.apache.hc.core5.http2.impl.nio.ClientHttpProtocolNegotiatorFactory;
import org.apache.hc.core5.http2.impl.nio.Http2StreamListener;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolPolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * @since 5.0
 */
public class H2RequesterBootstrap {

    private final List<PushConsumerEntry> pushConsumerList;

    private IOReactorConfig ioReactorConfig;
    private HttpProcessor httpProcessor;
    private CharCodingConfig charCodingConfig;
    private H2Config h2Config;
    private int defaultMaxPerRoute;
    private int maxTotal;
    private TimeValue timeToLive;
    private ConnPoolPolicy connPoolPolicy;
    private TlsStrategy tlsStrategy;
    private ExceptionListener exceptionListener;
    private ConnectionListener connectionListener;
    private Http2StreamListener streamListener;
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
     * Sets HTTP/2 protocol parameters
     */
    public final H2RequesterBootstrap setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets char coding for HTTP/2 messages.
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
     * Assigns {@link ConnPoolPolicy} instance.
     */
    public final H2RequesterBootstrap setConnPoolPolicy(final ConnPoolPolicy connPoolPolicy) {
        this.connPoolPolicy = connPoolPolicy;
        return this;
    }

    /**
     * Assigns {@link TlsStrategy} instance.
     */
    public final H2RequesterBootstrap setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Assigns {@link ExceptionListener} instance.
     */
    public final H2RequesterBootstrap setExceptionListener(final ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    /**
     * Assigns {@link ConnectionListener} instance.
     */
    public final H2RequesterBootstrap setConnectionListener(final ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
        return this;
    }

    /**
     * Assigns {@link Http2StreamListener} instance.
     */
    public final H2RequesterBootstrap setStreamListener(final Http2StreamListener streamListener) {
        this.streamListener = streamListener;
        return this;
    }

    /**
     * Assigns {@link ConnPoolListener} instance.
     */
    public final H2RequesterBootstrap setConnPoolListener(final ConnPoolListener<HttpHost> connPoolListener) {
        this.connPoolListener = connPoolListener;
        return this;
    }

    public final H2RequesterBootstrap register(final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushConsumerList.add(new PushConsumerEntry(null, uriPattern, supplier));
        return this;
    }

    public final H2RequesterBootstrap register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(hostname, "Hostname");
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        pushConsumerList.add(new PushConsumerEntry(hostname, uriPattern, supplier));
        return this;
    }

    public HttpAsyncRequester create() {
        final StrictConnPool<HttpHost, IOSession> connPool = new StrictConnPool<>(
                defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 20,
                maxTotal > 0 ? maxTotal : 50,
                timeToLive,
                connPoolPolicy,
                connPoolListener);
        final AsyncPushConsumerRegistry pushConsumerRegistry = new AsyncPushConsumerRegistry();
        for (final PushConsumerEntry entry: pushConsumerList) {
            pushConsumerRegistry.register(entry.hostname, entry.uriPattern, entry.supplier);
        }
        final ClientHttpProtocolNegotiatorFactory ioEventHandlerFactory = new ClientHttpProtocolNegotiatorFactory(
                httpProcessor != null ? httpProcessor : Http2Processors.client(),
                pushConsumerRegistry,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                h2Config != null ? h2Config : H2Config.DEFAULT,
                connectionListener,
                streamListener);
        try {
            return new HttpAsyncRequester(
                    ioReactorConfig,
                    ioEventHandlerFactory,
                    connPool,
                    tlsStrategy != null ? tlsStrategy : new H2ClientTlsStrategy(),
                    exceptionListener);
        } catch (final IOReactorException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static class PushConsumerEntry {

        final String hostname;
        final String uriPattern;
        final Supplier<AsyncPushConsumer> supplier;

        public PushConsumerEntry(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
            this.hostname = hostname;
            this.uriPattern = uriPattern;
            this.supplier = supplier;
        }

    }

}
