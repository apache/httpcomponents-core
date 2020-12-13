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

package org.apache.hc.core5.http.nio.ssl;

import java.net.SocketAddress;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * Basic side-side implementation of {@link TlsStrategy} that upgrades to TLS for endpoints
 * with the specified local ports.
 *
 * @since 5.0
 */
public class BasicServerTlsStrategy implements TlsStrategy {

    private final SSLContext sslContext;
    @SuppressWarnings("deprecation")
    private final SecurePortStrategy securePortStrategy;
    private final SSLBufferMode sslBufferMode;
    private final SSLSessionInitializer initializer;
    private final SSLSessionVerifier verifier;

    /**
     * @deprecated Use {@link BasicServerTlsStrategy#BasicServerTlsStrategy(SSLContext, SSLBufferMode, SSLSessionInitializer, SSLSessionVerifier)}
     */
    @Deprecated
    public BasicServerTlsStrategy(
            final SSLContext sslContext,
            final SecurePortStrategy securePortStrategy,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.securePortStrategy = securePortStrategy;
        this.sslBufferMode = sslBufferMode;
        this.initializer = initializer;
        this.verifier = verifier;
    }

    /**
     * @deprecated Use {@link BasicServerTlsStrategy#BasicServerTlsStrategy(SSLContext, SSLSessionInitializer, SSLSessionVerifier)}
     */
    @Deprecated
    public BasicServerTlsStrategy(
            final SSLContext sslContext,
            final SecurePortStrategy securePortStrategy,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this(sslContext, securePortStrategy, null, initializer, verifier);
    }

    /**
     * @deprecated Use {@link BasicServerTlsStrategy#BasicServerTlsStrategy(SSLContext, SSLSessionVerifier)}
     */
    @Deprecated
    public BasicServerTlsStrategy(
            final SSLContext sslContext,
            final SecurePortStrategy securePortStrategy,
            final SSLSessionVerifier verifier) {
        this(sslContext, securePortStrategy, null, null, verifier);
    }

    /**
     * @deprecated Use {@link BasicServerTlsStrategy#BasicServerTlsStrategy(SSLContext)}
     */
    @Deprecated
    public BasicServerTlsStrategy(final SSLContext sslContext, final SecurePortStrategy securePortStrategy) {
        this(sslContext, securePortStrategy, null, null, null);
    }

    /**
     * @deprecated Use {@link BasicServerTlsStrategy#BasicServerTlsStrategy()}
     */
    @Deprecated
    public BasicServerTlsStrategy(final SecurePortStrategy securePortStrategy) {
        this(SSLContexts.createSystemDefault(), securePortStrategy);
    }

    public BasicServerTlsStrategy(
            final SSLContext sslContext,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.sslBufferMode = sslBufferMode;
        this.initializer = initializer;
        this.verifier = verifier;
        this.securePortStrategy = null;
    }

    public BasicServerTlsStrategy(
            final SSLContext sslContext,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this(sslContext, (SSLBufferMode) null, initializer, verifier);
    }

    public BasicServerTlsStrategy(
            final SSLContext sslContext,
            final SSLSessionVerifier verifier) {
        this(sslContext, (SSLBufferMode) null, null, verifier);
    }

    public BasicServerTlsStrategy(final SSLContext sslContext) {
        this(sslContext, null, null, null, null);
    }

    public BasicServerTlsStrategy() {
        this(SSLContexts.createSystemDefault());
    }

    private boolean isApplicable(final SocketAddress localAddress) {
        return securePortStrategy == null || securePortStrategy.isSecure(localAddress);
    }

    @Override
    public void upgrade(
            final TransportSecurityLayer tlsSession,
            final NamedEndpoint endpoint,
            final Object attachment,
            final Timeout handshakeTimeout,
            final FutureCallback<TransportSecurityLayer> callback) {
        tlsSession.startTls(sslContext, endpoint, sslBufferMode,
                TlsSupport.enforceStrongSecurity(initializer), verifier, handshakeTimeout, callback);
    }

    /**
     * @deprecated use {@link #upgrade(TransportSecurityLayer, NamedEndpoint, Object, Timeout, FutureCallback)}
     */
    @Deprecated
    @Override
    public boolean upgrade(
            final TransportSecurityLayer tlsSession,
            final HttpHost host,
            final SocketAddress localAddress,
            final SocketAddress remoteAddress,
            final Object attachment,
            final Timeout handshakeTimeout) {
        if (isApplicable(localAddress)) {
            upgrade(tlsSession, host, attachment, handshakeTimeout, null);
            return true;
        }
        return false;
    }

}
