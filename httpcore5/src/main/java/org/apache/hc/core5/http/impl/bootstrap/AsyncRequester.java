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

package org.apache.hc.core5.http.impl.bootstrap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

public class AsyncRequester extends IOReactorExecutor<DefaultConnectingIOReactor> implements ConnectionInitiator {

    public AsyncRequester(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final ExceptionListener exceptionListener,
            final Callback<IOSession> sessionShutdownCallback) throws IOReactorException {
        super(new DefaultConnectingIOReactor(
                        eventHandlerFactory, ioReactorConfig, new ThreadFactoryImpl("requester-dispatch", true), sessionShutdownCallback),
                exceptionListener,
                new ThreadFactoryImpl("connector", true));
    }

    private InetSocketAddress toSocketAddress(final HttpHost host) {
        int port = host.getPort();
        if (port < 0) {
            final String scheme = host.getSchemeName();
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
        }
        final String hostName = host.getHostName();
        return new InetSocketAddress(hostName, port);
    }

    public SessionRequest requestSession(
            final HttpHost host,
            final TimeValue timeout,
            final Object attachment,
            final SessionRequestCallback callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeout, "Timeout");
        final SessionRequest  sessionRequest = reactor().connect(host, toSocketAddress(host), null, attachment, callback);
        sessionRequest.setConnectTimeout(timeout.toMillisIntBound());
        return sessionRequest;
    }

    @Override
    public SessionRequest connect(
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final Object attachment,
            final SessionRequestCallback callback) {
        return reactor().connect(remoteEndpoint, remoteAddress, localAddress, attachment, callback);
    }

}
