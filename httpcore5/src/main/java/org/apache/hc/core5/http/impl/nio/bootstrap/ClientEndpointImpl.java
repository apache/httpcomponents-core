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

import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;

/**
 * Implementation of {@link ClientEndpoint}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class ClientEndpointImpl implements ClientEndpoint {

    private final IOSession ioSession;

    public ClientEndpointImpl(final IOSession ioSession) {
        super();
        this.ioSession = ioSession;
    }

    @Override
    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context) {
        final Command executionCommand = new ExecutionCommand(
                exchangeHandler,
                context != null ? context : HttpCoreContext.create());
        ioSession.getCommandQueue().add(executionCommand);
        ioSession.setEvent(SelectionKey.OP_WRITE);
        if (ioSession.isClosed()) {
            executionCommand.cancel();
        }
    }

    @Override
    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        final BasicFuture<T> future = new BasicFuture<>(callback);
        execute(new BasicClientExchangeHandler<>(requestProducer, responseConsumer,
                new FutureCallback<T>() {

                    @Override
                    public void completed(final T result) {
                        future.completed(result);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        future.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        future.cancel();
                    }

                }),
                context != null ? context : HttpCoreContext.create());
        return future;
    }

    @Override
    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, callback);
    }

    @Override
    public void close() {
        shutdown(ShutdownType.GRACEFUL);
    }

    @Override
    public void shutdown(final ShutdownType type) {
        ioSession.getCommandQueue().addFirst(new ShutdownCommand(type));
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

}
