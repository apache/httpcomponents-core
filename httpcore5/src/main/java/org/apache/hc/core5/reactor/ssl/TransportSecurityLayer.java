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

package org.apache.hc.core5.reactor.ssl;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;

/**
 * TLS capable session layer interface.
 *
 * @since 5.0
 */
public interface TransportSecurityLayer {

    /**
     * Starts TLS session over an existing network connection with the given SSL context.
     * {@link NamedEndpoint} details are applicable for client side connections and
     * are used for host name verification, when supported by the SSL engine.
     *
     * @param sslContext SSL context to be used for this session.
     * @param endpoint optional endpoint details for outgoing client side connections.
     * @param sslBufferMode SSL buffer management mode.
     * @param initializer SSL session initialization callback.
     * @param verifier SSL session verification callback.
     * @param handshakeTimeout the timeout to use while performing the TLS handshake; may be {@code null}.
     */
    void startTls(
            SSLContext sslContext,
            NamedEndpoint endpoint,
            SSLBufferMode sslBufferMode,
            SSLSessionInitializer initializer,
            SSLSessionVerifier verifier,
            Timeout handshakeTimeout) throws UnsupportedOperationException;

    /**
     * Starts TLS session over an existing network connection with the given SSL context.
     * {@link NamedEndpoint} details are applicable for client side connections and
     * are used for host name verification, when supported by the SSL engine.
     *
     * @param sslContext SSL context to be used for this session.
     * @param endpoint optional endpoint details for outgoing client side connections.
     * @param sslBufferMode SSL buffer management mode.
     * @param initializer SSL session initialization callback.
     * @param verifier SSL session verification callback.
     * @param handshakeTimeout the timeout to use while performing the TLS handshake; may be {@code null}.
     *
     * @since 5.2
     */
    default void startTls(
            SSLContext sslContext,
            NamedEndpoint endpoint,
            SSLBufferMode sslBufferMode,
            SSLSessionInitializer initializer,
            SSLSessionVerifier verifier,
            Timeout handshakeTimeout,
            FutureCallback<TransportSecurityLayer> callback) throws UnsupportedOperationException {
        startTls(sslContext, endpoint, sslBufferMode, initializer, verifier, handshakeTimeout);
        if (callback != null) {
            callback.completed(null);
        }
    }

    /**
     * Returns details of a fully established TLS session.
     *
     * @return TLS session details.
     */
    TlsDetails getTlsDetails();

}
