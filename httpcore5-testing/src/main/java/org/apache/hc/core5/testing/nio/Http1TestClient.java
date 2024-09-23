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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.CompletingFutureContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;

public class Http1TestClient extends AsyncRequester {

    private final SSLContext sslContext;
    private final SSLSessionInitializer sslSessionInitializer;
    private final SSLSessionVerifier sslSessionVerifier;

    private Http1Config http1Config;
    private HttpProcessor httpProcessor;

    public Http1TestClient(
            final IOReactorConfig ioReactorConfig,
            final SSLContext sslContext,
            final SSLSessionInitializer sslSessionInitializer,
            final SSLSessionVerifier sslSessionVerifier) throws IOException {
        super(ioReactorConfig);
        this.sslContext = sslContext;
        this.sslSessionInitializer = sslSessionInitializer;
        this.sslSessionVerifier = sslSessionVerifier;
    }

    public Http1TestClient() throws IOException {
        this(IOReactorConfig.DEFAULT, null, null, null);
    }

    private void ensureNotRunning() {
        Asserts.check(getStatus() == IOReactorStatus.INACTIVE, "Client is already running");
    }

    /**
     * @since 5.3
     */
    public void configure(final Http1Config http1Config) {
        ensureNotRunning();
        this.http1Config = http1Config;
    }

    /**
     * @since 5.3
     */
    public void configure(final HttpProcessor httpProcessor) {
        ensureNotRunning();
        this.httpProcessor = httpProcessor;
    }

    /**
     * @deprecated Use {@link #configure(Http1Config)}, {@link #configure(HttpProcessor)}, {@link #start()}.
     */
    @Deprecated
    public void start(
            final HttpProcessor httpProcessor,
            final Http1Config http1Config) throws IOException {
        configure(http1Config);
        configure(httpProcessor);
        start();
    }

    /**
     * @deprecated Use {@link #configure(Http1Config)}, {@link #start()}.
     */
    @Deprecated
    public void start(final Http1Config http1Config) throws IOException {
        start(null, http1Config);
    }

    public void start() throws IOException {
        execute(new InternalClientHttp1EventHandlerFactory(
                httpProcessor != null ? httpProcessor : HttpProcessors.client(),
                http1Config,
                CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                sslContext,
                sslSessionInitializer,
                sslSessionVerifier));
    }

    public Future<ClientSessionEndpoint> connect(
            final HttpHost host,
            final Timeout timeout,
            final FutureCallback<ClientSessionEndpoint> callback) {
        final BasicFuture<ClientSessionEndpoint> future = new BasicFuture<>(callback);
        requestSession(host, timeout, new CompletingFutureContribution<>(future, ClientSessionEndpoint::new));
        return future;
    }

    public Future<ClientSessionEndpoint> connect(final HttpHost host, final Timeout timeout) {
        return connect(host, timeout, null);
    }

    public Future<ClientSessionEndpoint> connect(final String hostname, final int port, final Timeout timeout) {
        return connect(new HttpHost(hostname, port), timeout, null);
    }

}
