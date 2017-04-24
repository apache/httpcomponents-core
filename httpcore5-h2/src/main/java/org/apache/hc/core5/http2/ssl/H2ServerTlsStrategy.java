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

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.SSLBufferManagement;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Args;

/**
 * Basic side-side implementation of {@link TlsStrategy} that upgrades to TLS for endpoints
 * with the specified local ports.
 *
 * @since 5.0
 */
public class H2ServerTlsStrategy implements TlsStrategy {

    private final SSLContext sslContext;
    private final SecurePortStrategy securePortStrategy;
    private final SSLBufferManagement sslBufferManagement;
    private final SSLSessionInitializer initializer;
    private final SSLSessionVerifier verifier;

    public H2ServerTlsStrategy(
            final SSLContext sslContext,
            final SecurePortStrategy securePortStrategy,
            final SSLBufferManagement sslBufferManagement,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.securePortStrategy = securePortStrategy;
        this.sslBufferManagement = sslBufferManagement;
        this.initializer = initializer;
        this.verifier = verifier;
    }

    public H2ServerTlsStrategy(
            final SSLContext sslContext,
            final SecurePortStrategy securePortStrategy,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) {
        this(sslContext, securePortStrategy, null, initializer, verifier);
    }

    public H2ServerTlsStrategy(
            final SSLContext sslContext,
            final SecurePortStrategy securePortStrategy,
            final SSLSessionVerifier verifier) {
        this(sslContext, securePortStrategy, null, null, verifier);
    }

    public H2ServerTlsStrategy(final SSLContext sslContext, final SecurePortStrategy securePortStrategy) {
        this(sslContext, securePortStrategy, null, null, null);
    }

    public H2ServerTlsStrategy(final int[] securePorts) {
        this(SSLContexts.createSystemDefault(), new FixedPortStrategy(securePorts));
    }

    @Override
    public void upgrade(
            final TransportSecurityLayer tlsSession,
            final HttpHost host,
            final SocketAddress localAddress,
            final SocketAddress remoteAddress,
            final Object attachment) {
        if (securePortStrategy != null && securePortStrategy.isSecure(localAddress)) {
            tlsSession.startTls(sslContext, sslBufferManagement,
                    H2TlsSupport.enforceRequirements(attachment, initializer),
                    verifier);
        }
    }

}
