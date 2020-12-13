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

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;

/**
 * TLS protocol upgrade strategy for non-blocking {@link TransportSecurityLayer} sessions.
 *
 * @since 5.0
 */
public interface TlsStrategy {

    /**
     * Secures current session layer with TLS.
     *
     * @param sessionLayer the session layer
     * @param host the name of the opposite endpoint when given or {@code null} otherwise.
     * @param localAddress the address of the local endpoint.
     * @param remoteAddress the address of the remote endpoint.
     * @param attachment arbitrary object passes to the TLS session initialization code.
     * @param handshakeTimeout the timeout to use while performing the TLS handshake; may be {@code null}.
     * @return {@code true} if the session has been upgraded, {@code false} otherwise.
     *
     * @deprecated use {@link #upgrade(TransportSecurityLayer, NamedEndpoint, Object, Timeout, FutureCallback)}
     */
    @Deprecated
    boolean upgrade(
            TransportSecurityLayer sessionLayer,
            HttpHost host,
            SocketAddress localAddress,
            SocketAddress remoteAddress,
            Object attachment,
            Timeout handshakeTimeout);

    /**
     * Secures current session layer with TLS.
     *
     * @param sessionLayer the session layer
     * @param endpoint the name of the opposite endpoint when applicable or {@code null} otherwise.
     * @param attachment arbitrary object passes to the TLS session initialization code.
     * @param handshakeTimeout the timeout to use while performing the TLS handshake; may be {@code null}.
     * @param callback Operation result callback.
     *
     * @since 5.2
     */
    default void upgrade(
            TransportSecurityLayer sessionLayer,
            NamedEndpoint endpoint,
            Object attachment,
            Timeout handshakeTimeout,
            FutureCallback<TransportSecurityLayer> callback) {
        upgrade(sessionLayer, new HttpHost(URIScheme.HTTPS.id, endpoint.getHostName(), endpoint.getPort()),
                null, null, attachment, handshakeTimeout);
        if (callback != null) {
            callback.completed(sessionLayer);
        }
    }

}
