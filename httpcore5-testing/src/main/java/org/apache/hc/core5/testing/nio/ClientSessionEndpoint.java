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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Asserts;

/**
 * Client endpoint that can be used to initiate HTTP message exchanges.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class ClientSessionEndpoint implements ModalCloseable {

    private final IOSession ioSession;
    private final AtomicBoolean closed;

    public ClientSessionEndpoint(final IOSession ioSession) {
        super();
        this.ioSession = ioSession;
        this.closed = new AtomicBoolean(false);
    }

    public void execute(final Command command, final Command.Priority priority) {
        ioSession.enqueue(command, priority);
        if (!ioSession.isOpen()) {
            command.cancel();
        }
    }

    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context) {
        Asserts.check(!closed.get(), "Connection is already closed");
        final Command executionCommand = new RequestExecutionCommand(exchangeHandler, pushHandlerFactory, null, context);
        ioSession.enqueue(executionCommand, Command.Priority.NORMAL);
        if (!ioSession.isOpen()) {
            exchangeHandler.failed(new ConnectionClosedException());
        }
    }

    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context) {
        execute(exchangeHandler, null, context);
    }

    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Asserts.check(!closed.get(), "Connection is already closed");
        final BasicFuture<T> future = new BasicFuture<>(callback);
        execute(new BasicClientExchangeHandler<>(requestProducer, responseConsumer,
                new FutureContribution<T>(future) {

                    @Override
                    public void completed(final T result) {
                        future.completed(result);
                    }

                }),
                pushHandlerFactory, context);
        return future;
    }

    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, context, callback);
    }

    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, null, callback);
    }

    public boolean isOpen() {
        return !closed.get() && ioSession.isOpen();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (closed.compareAndSet(false, true)) {
            if (closeMode == CloseMode.GRACEFUL) {
                ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);
            } else {
                ioSession.close(closeMode);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            ioSession.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.IMMEDIATE);
        }
    }

    @Override
    public String toString() {
        return ioSession.toString();
    }

}
