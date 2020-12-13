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

package org.apache.hc.core5.http2.ssl;

import java.net.SocketAddress;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * Basic client-side implementation of {@link TlsStrategy} that upgrades to TLS for all endpoints
 * with {@code HTTPS} scheme.
 *
 * @since 5.0
 */
public class H2ClientTlsStrategy implements TlsStrategy {

    private final SSLContext sslContext;
    private final SSLBufferMode sslBufferMode;
    private final SSLSessionInitializer initializer;
    private final SSLSessionVerifier verifier;

    public H2ClientTlsStrategy(
            final SSLContext sslContext,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.sslBufferMode = sslBufferMode;
        this.initializer = initializer;
        this.verifier = verifier;
    }

    public H2ClientTlsStrategy(
            final SSLContext sslContext,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this(sslContext, null, initializer, verifier);
    }

    public H2ClientTlsStrategy(
            final SSLContext sslContext,
            final SSLSessionVerifier verifier) {
        this(sslContext, null, null, verifier);
    }

    public H2ClientTlsStrategy(final SSLContext sslContext) {
        this(sslContext, null, null, null);
    }

    public H2ClientTlsStrategy() {
        this(SSLContexts.createSystemDefault());
    }

    @Override
    public void upgrade(
            final TransportSecurityLayer tlsSession,
            final NamedEndpoint endpoint,
            final Object attachment,
            final Timeout handshakeTimeout,
            final FutureCallback<TransportSecurityLayer> callback) {
        tlsSession.startTls(
                sslContext,
                endpoint,
                sslBufferMode,
                H2TlsSupport.enforceRequirements(attachment, initializer),
                verifier,
                handshakeTimeout,
                callback);
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
        final String scheme = host != null ? host.getSchemeName() : null;
        if (URIScheme.HTTPS.same(scheme)) {
            upgrade(tlsSession, host, attachment, handshakeTimeout, null);
            return true;
        }
        return false;
    }

}
