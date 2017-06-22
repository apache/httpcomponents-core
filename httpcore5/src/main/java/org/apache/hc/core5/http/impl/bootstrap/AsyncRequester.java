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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorService;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

public class AsyncRequester implements IOReactorService, ConnectionInitiator {

    private final DefaultConnectingIOReactor ioReactor;

    public AsyncRequester(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final Decorator<IOSession> ioSessionDecorator,
            final IOSessionListener sessionListener,
            final Callback<IOSession> sessionShutdownCallback) {
        this.ioReactor = new DefaultConnectingIOReactor(
                eventHandlerFactory,
                ioReactorConfig,
                new DefaultThreadFactory("requester-dispatch", true),
                ioSessionDecorator,
                sessionListener,
                sessionShutdownCallback);
    }

    private InetSocketAddress toSocketAddress(final HttpHost host) {
        int port = host.getPort();
        if (port < 0) {
            final String scheme = host.getSchemeName();
            if (URIScheme.HTTP.same(scheme)) {
                port = 80;
            } else if (URIScheme.HTTPS.same(scheme)) {
                port = 443;
            }
        }
        final String hostName = host.getHostName();
        return new InetSocketAddress(hostName, port);
    }

    @Override
    public Future<IOSession> connect(
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final TimeValue timeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) {
        return ioReactor.connect(remoteEndpoint, remoteAddress, localAddress, timeout, attachment, callback);
    }

    public Future<IOSession> requestSession(
            final HttpHost host,
            final TimeValue timeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeout, "Timeout");
        return connect(host, toSocketAddress(host), null, timeout, attachment, callback);
    }

    @Override
    public void start() {
        ioReactor.start();
    }

    @Override
    public IOReactorStatus getStatus() {
        return ioReactor.getStatus();
    }

    @Override
    public List<ExceptionEvent> getExceptionLog() {
        return ioReactor.getExceptionLog();
    }

    @Override
    public void initiateShutdown() {
        ioReactor.initiateShutdown();
    }

    @Override
    public void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        ioReactor.awaitShutdown(waitTime);
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        ioReactor.shutdown(shutdownType);
    }

    @Override
    public void close() throws IOException {
        ioReactor.close();
    }

}
