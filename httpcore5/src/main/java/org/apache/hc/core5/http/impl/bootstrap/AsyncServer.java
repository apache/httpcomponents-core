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
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionAcceptor;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultListeningIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorService;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Protocol agnostic server side I/O session handler.
 */
public class AsyncServer extends AbstractConnectionInitiatorBase implements IOReactorService, ConnectionAcceptor {

    private final DefaultListeningIOReactor ioReactor;

    @Internal
    public AsyncServer(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final Callback<IOSession> sessionShutdownCallback) {
        this.ioReactor = new DefaultListeningIOReactor(
                eventHandlerFactory,
                ioReactorConfig,
                new DefaultThreadFactory("server-dispatch", true),
                new DefaultThreadFactory("server-listener", true),
                ioSessionDecorator,
                exceptionCallback,
                sessionListener,
                sessionShutdownCallback);
    }

    @Override
    public void start() {
        ioReactor.start();
    }

    @Override
    ConnectionInitiator getIOReactor() {
        return ioReactor;
    }

    /**
     * @since 5.1
     */
    public Future<ListenerEndpoint> listen(
            final SocketAddress address, final Object attachment, final FutureCallback<ListenerEndpoint> callback) {
        return ioReactor.listen(address, attachment, callback);
    }

    @Override
    public Future<ListenerEndpoint> listen(final SocketAddress address, final FutureCallback<ListenerEndpoint> callback) {
        return listen(address, null, callback);
    }

    public Future<ListenerEndpoint> listen(final SocketAddress address) {
        return ioReactor.listen(address, null);
    }

    @Override
    public void pause() throws IOException {
        ioReactor.pause();
    }

    @Override
    public void resume() throws IOException {
        ioReactor.resume();
    }

    @Override
    public Set<ListenerEndpoint> getEndpoints() {
        return ioReactor.getEndpoints();
    }

    @Override
    public IOReactorStatus getStatus() {
        return ioReactor.getStatus();
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
    public void close(final CloseMode closeMode) {
        ioReactor.close(closeMode);
    }

    @Override
    public void close() throws IOException {
        ioReactor.close();
    }

}
