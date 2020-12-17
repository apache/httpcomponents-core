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

import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;

/**
 * Client side message exchange initiator capable of negotiating
 * HTTP/2 or HTTP/1.1 compatible connections.
 *
 * @since 5.0
 */
public class H2AsyncRequester extends HttpAsyncRequester {

    private final HttpVersionPolicy versionPolicy;

    /**
     * Use {@link H2RequesterBootstrap} to create instances of this class.
     */
    @Internal
    public H2AsyncRequester(
            final HttpVersionPolicy versionPolicy,
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final ManagedConnPool<HttpHost, IOSession> connPool) {
        super(ioReactorConfig, eventHandlerFactory, ioSessionDecorator, exceptionCallback, sessionListener, connPool);
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
    }

    /**
     * Use {@link H2RequesterBootstrap} to create instances of this class.
     *
     * @since 5.2
     */
    @Internal
    public H2AsyncRequester(
            final HttpVersionPolicy versionPolicy,
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final ManagedConnPool<HttpHost, IOSession> connPool,
            final TlsStrategy tlsStrategy,
            final Timeout handshakeTimeout) {
        super(ioReactorConfig, eventHandlerFactory, ioSessionDecorator, exceptionCallback, sessionListener, connPool,
                tlsStrategy, handshakeTimeout);
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
    }

    @Override
    protected Future<AsyncClientEndpoint> doConnect(
            final HttpHost host,
            final Timeout timeout,
            final Object attachment,
            final FutureCallback<AsyncClientEndpoint> callback) {
        return super.doConnect(host, timeout, attachment != null ? attachment : versionPolicy, callback);
    }

    @Override
    protected void doTlsUpgrade(final ProtocolIOSession ioSession,
                                final NamedEndpoint endpoint,
                                final FutureCallback<ProtocolIOSession> callback) {
        super.doTlsUpgrade(ioSession, endpoint, new CallbackContribution<ProtocolIOSession>(callback) {

            @Override
            public void completed(final ProtocolIOSession protocolSession) {
                final boolean switchProtocol;
                switch (versionPolicy) {
                    case FORCE_HTTP_2:
                        switchProtocol = true;
                        break;
                    case NEGOTIATE:
                        final TlsDetails tlsDetails = protocolSession.getTlsDetails();
                        final String appProtocol = tlsDetails != null ? tlsDetails.getApplicationProtocol() : null;
                        switchProtocol = ApplicationProtocol.HTTP_2.id.equals(appProtocol);
                        break;
                    default:
                        switchProtocol = false;
                }
                if (switchProtocol) {
                    protocolSession.switchProtocol(ApplicationProtocol.HTTP_2.id, callback);
                } else {
                    if (callback != null) {
                        callback.completed(protocolSession);
                    }
                }
            }

        });
    }

}
