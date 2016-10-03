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

package org.apache.hc.core5.http2.bootstrap.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http2.nio.command.ClientCommandEndpoint;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionRequest;
import org.apache.hc.core5.reactor.SessionRequestCallback;
import org.apache.hc.core5.util.Args;

public class AsyncRequester extends IOReactorExecutor<DefaultConnectingIOReactor> {

    public AsyncRequester(final ExceptionListener exceptionListener) throws IOException {
        super(exceptionListener);
    }

    protected void start(final IOEventHandlerFactory ioEventHandlerFactory) throws IOException {
        Args.notNull(ioEventHandlerFactory, "Handler factory");
        startExecution(new DefaultConnectingIOReactor(ioEventHandlerFactory));
    }

    public Future<ClientCommandEndpoint> connect(
            final InetSocketAddress address,
            final int connectTimeout,
            final Object attachment,
            final FutureCallback<ClientCommandEndpoint> callback) throws InterruptedException {
        final BasicFuture<ClientCommandEndpoint> future = new BasicFuture<>(callback);
        final SessionRequest sessionRequest = reactor().connect(address, null, attachment, new SessionRequestCallback() {

            @Override
            public void completed(final SessionRequest request) {
                final IOSession session = request.getSession();
                future.completed(new ClientCommandEndpoint(session));
            }

            @Override
            public void failed(final SessionRequest request) {
                future.failed(request.getException());
            }

            @Override
            public void timeout(final SessionRequest request) {
                future.failed(new SocketTimeoutException("Connect timeout"));
            }

            @Override
            public void cancelled(final SessionRequest request) {
                future.cancel();
            }
        });
        sessionRequest.setConnectTimeout(connectTimeout);
        return future;
    }

    public Future<ClientCommandEndpoint> connect(
            final InetSocketAddress address,
            final int connectTimeout) throws InterruptedException {
        return connect(address, connectTimeout, null, null);
    }

    public Future<ClientCommandEndpoint> connect(
            final String hostname,
            final int port,
            final int connectTimeout) throws InterruptedException {
        return connect(new InetSocketAddress(hostname, port), connectTimeout, null, null);
    }

}
