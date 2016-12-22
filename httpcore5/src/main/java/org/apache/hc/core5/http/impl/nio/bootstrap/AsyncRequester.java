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

package org.apache.hc.core5.http.impl.nio.bootstrap;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;

public class AsyncRequester extends IOReactorExecutor<DefaultConnectingIOReactor> implements ConnectionInitiator {

    public AsyncRequester(
            final IOReactorConfig ioReactorConfig,
            final ExceptionListener exceptionListener,
            final Callback<IOSession> sessionShutdownCallback) {
        super(ioReactorConfig,
                exceptionListener,
                new ThreadFactoryImpl("connector", true),
                new ThreadFactoryImpl("requester-dispatch", true),
                sessionShutdownCallback);
    }

    public AsyncRequester(
            final IOReactorConfig ioReactorConfig,
            final ExceptionListener exceptionListener) {
        this(ioReactorConfig, exceptionListener, null);
    }

    @Override
    DefaultConnectingIOReactor createIOReactor(
            final IOEventHandlerFactory ioEventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final ThreadFactory threadFactory,
            final Callback<IOSession> sessionShutdownCallback) throws IOException {
        return new DefaultConnectingIOReactor(
                ioEventHandlerFactory, ioReactorConfig, threadFactory, sessionShutdownCallback);
    }

    private NamedEndpoint toEndpoint(final HttpHost host) {
        int port = host.getPort();
        if (port < 0) {
            final String hostName = host.getHostName();
            final String scheme = host.getSchemeName();
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
            return new HttpHost(hostName, port, scheme);
        } else {
            return host;
        }
    }

    protected SessionRequest requestSession(
            final HttpHost host,
            final long timeout,
            final TimeUnit timeUnit,
            final SessionRequestCallback callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeUnit, "Time unit");
        final SessionRequest  sessionRequest = reactor().connect(toEndpoint(host), null, null, callback);
        sessionRequest.setConnectTimeout((int) timeUnit.toMillis(timeout));
        return sessionRequest;
    }

    @Override
    public SessionRequest connect(
            final NamedEndpoint remoteEndpoint,
            final SocketAddress localAddress,
            final Object attachment,
            final SessionRequestCallback callback) {
        return reactor().connect(remoteEndpoint, localAddress, attachment, callback);
    }

}
