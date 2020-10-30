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

package org.apache.hc.core5.http2.impl.nio;

import java.io.IOException;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.reactor.EndpointParameters;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ProtocolUpgradeHandler;
import org.apache.hc.core5.reactor.TransportSecurityLayerEx;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

abstract class AbstractH2UpgradeHandler implements ProtocolUpgradeHandler {

    private final TlsStrategy tlsStrategy;
    private final Timeout handshakeTimeout;

    AbstractH2UpgradeHandler(final TlsStrategy tlsStrategy, final Timeout handshakeTimeout) {
        this.tlsStrategy = tlsStrategy;
        this.handshakeTimeout = handshakeTimeout;
    }

    protected abstract HttpConnectionEventHandler createProtocolNegotiator(ProtocolIOSession ioSession,
                                                                           FutureCallback<ProtocolIOSession> callback);

    private void negotiateHttp(final ProtocolIOSession ioSession,
                               final FutureCallback<ProtocolIOSession> callback) {
        final HttpConnectionEventHandler protocolNegotiator = createProtocolNegotiator(ioSession, callback);
        ioSession.upgrade(protocolNegotiator);
        try {
            protocolNegotiator.connected(ioSession);
        } catch (final IOException ex) {
            protocolNegotiator.exception(ioSession, ex);
        }
    }

    @Override
    public final void upgrade(final ProtocolIOSession ioSession,
                              final EndpointParameters parameters,
                              final FutureCallback<ProtocolIOSession> callback) {
        Args.notNull(parameters, "Endpoint parameters");
        if (URIScheme.HTTPS.same(parameters.getScheme())) {
            if (ioSession instanceof TransportSecurityLayerEx) {
                final TransportSecurityLayerEx transportSecurityLayer = (TransportSecurityLayerEx) ioSession;
                transportSecurityLayer.subscribe(new CallbackContribution<ProtocolIOSession>(callback) {

                    @Override
                    public void completed(final ProtocolIOSession result) {
                        negotiateHttp(ioSession, callback);
                    }

                });
                tlsStrategy.upgrade(
                        transportSecurityLayer,
                        new HttpHost(parameters.getScheme(), parameters.getHostName(), parameters.getPort()),
                        ioSession.getLocalAddress(),
                        ioSession.getRemoteAddress(),
                        HttpVersionPolicy.FORCE_HTTP_2,
                        handshakeTimeout);
            } else {
                throw new UnsupportedOperationException("TLS upgrade not supported");
            }
        } else {
            negotiateHttp(ioSession, callback);
        }
    }

}
