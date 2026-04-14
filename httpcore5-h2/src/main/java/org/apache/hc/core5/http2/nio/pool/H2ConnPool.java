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
package org.apache.hc.core5.http2.nio.pool;

import java.net.InetSocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.DefaultAddressResolver;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.StaleCheckCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.AbstractIOSessionPool;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Pool of HTTP/2 message multiplexing capable connections.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class H2ConnPool extends AbstractIOSessionPool<HttpHost> implements H2RequesterConnPool {

    private final ConnectionInitiator connectionInitiator;
    private final Resolver<HttpHost, InetSocketAddress> addressResolver;
    private final TlsStrategy tlsStrategy;

    private volatile TimeValue validateAfterInactivity;

    public H2ConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy) {
        this(connectionInitiator, addressResolver, tlsStrategy, null);
    }

    H2ConnPool(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final TimeValue validateAfterInactivity) {
        super();
        this.connectionInitiator = Args.notNull(connectionInitiator, "Connection initiator");
        this.addressResolver = addressResolver != null ? addressResolver : DefaultAddressResolver.INSTANCE;
        this.tlsStrategy = tlsStrategy;
        this.validateAfterInactivity = validateAfterInactivity != null
                ? validateAfterInactivity : TimeValue.NEG_ONE_MILLISECOND;
    }

    /**
     * Returns the current validate-after-inactivity interval. A non-negative
     * value enables stale-checking of pooled sessions that have been idle
     * for longer than the interval before they are handed out again.
     *
     * @since 5.0
     */
    public TimeValue getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    @Override
    public Future<H2StreamLease> leaseSession(
            final HttpHost endpoint,
            final Timeout connectTimeout,
            final FutureCallback<H2StreamLease> callback) {
        final org.apache.hc.core5.concurrent.BasicFuture<H2StreamLease> future =
                new org.apache.hc.core5.concurrent.BasicFuture<>(callback);
        getSession(endpoint, connectTimeout, new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession ioSession) {
                future.completed(new H2StreamLease(ioSession, () -> {
                }));
            }

            @Override
            public void failed(final Exception ex) {
                future.failed(ex);
            }

            @Override
            public void cancelled() {
                future.cancel();
            }
        });
        return future;
    }

    @Override
    public void setValidateAfterInactivity(final TimeValue timeValue) {
        this.validateAfterInactivity = timeValue != null ? timeValue : TimeValue.NEG_ONE_MILLISECOND;
    }

    @Override
    protected void closeSession(
            final IOSession ioSession,
            final CloseMode closeMode) {
        if (closeMode == CloseMode.GRACEFUL) {
            ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
        } else {
            ioSession.close(closeMode);
        }
    }

    @Override
    protected Future<IOSession> connectSession(
            final HttpHost namedEndpoint,
            final Timeout connectTimeout,
            final FutureCallback<IOSession> callback) {
        final InetSocketAddress remoteAddress = addressResolver.resolve(namedEndpoint);
        return connectionInitiator.connect(
                namedEndpoint,
                remoteAddress,
                null,
                connectTimeout,
                null,
                new CallbackContribution<IOSession>(callback) {

                    @Override
                    public void completed(final IOSession ioSession) {
                        if (tlsStrategy != null
                                && URIScheme.HTTPS.same(namedEndpoint.getSchemeName())
                                && ioSession instanceof TransportSecurityLayer) {
                            tlsStrategy.upgrade(
                                    (TransportSecurityLayer) ioSession,
                                    namedEndpoint,
                                    null,
                                    connectTimeout,
                                    new CallbackContribution<TransportSecurityLayer>(callback) {

                                        @Override
                                        public void completed(final TransportSecurityLayer transportSecurityLayer) {
                                            callback.completed(ioSession);
                                        }

                                    });
                            ioSession.setSocketTimeout(connectTimeout);
                        } else {
                            callback.completed(ioSession);
                        }
                    }

                });
    }

    @Override
    protected void validateSession(
            final IOSession ioSession,
            final Callback<Boolean> callback) {
        if (ioSession.isOpen()) {
            final TimeValue timeValue = validateAfterInactivity;
            if (TimeValue.isNonNegative(timeValue)) {
                final long lastAccessTime = Math.min(ioSession.getLastReadTime(), ioSession.getLastWriteTime());
                final long deadline = lastAccessTime + timeValue.toNanoseconds();
                if (deadline <= System.nanoTime()) {
                    ioSession.enqueue(new StaleCheckCommand(callback::execute), Command.Priority.NORMAL);
                    return;
                }
            }
            callback.execute(true);
        } else {
            callback.execute(false);
        }
    }

}
