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

package org.apache.hc.core5.http2.nio.command;

import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http2.nio.AsyncRequestProducer;
import org.apache.hc.core5.http2.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http2.nio.BasicClientExchangeHandler;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;

/**
 * Endpoint that can be used to initiate client side operations by submitting a {@link Command} object.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class ClientCommandEndpoint {

    private final IOSession ioSession;

    public ClientCommandEndpoint(final IOSession ioSession) {
        super();
        this.ioSession = ioSession;
    }

    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context) {
        final Command executionCommand = new ExecutionCommand(
                exchangeHandler,
                context != null ? context : HttpCoreContext.create());
        ioSession.getCommandQueue().add(executionCommand);
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

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

    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, HttpCoreContext.create(), callback);
    }

    public void requestGracefulShutdown() {
        ioSession.getCommandQueue().addFirst(ShutdownCommand.GRACEFUL);
        ioSession.setEvent(SelectionKey.OP_WRITE);
    }

}
