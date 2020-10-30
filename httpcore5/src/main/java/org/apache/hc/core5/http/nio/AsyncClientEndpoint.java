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

package org.apache.hc.core5.http.nio;

import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

/**
 * Client endpoint leased from a connection manager.
 * <p>
 * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
 * or {@link #releaseAndDiscard()}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class AsyncClientEndpoint {

    /**
     * Initiates a message exchange using the given handler.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public abstract void execute(
            AsyncClientExchangeHandler exchangeHandler,
            HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            HttpContext context);

    /**
     * Initiates a message exchange using the given handler.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context) {
        execute(exchangeHandler, null, context);
    }

    /**
     * Releases the underlying connection back to the connection pool as re-usable.
     */
    public abstract void releaseAndReuse();

    /**
     * Shuts down the underlying connection and removes it from the connection pool.
     */
    public abstract void releaseAndDiscard();

    /**
     * Determines if the connection to the remote endpoint is still open and valid.
     */
    public abstract boolean isConnected();

    /**
     * Initiates message exchange using the given request producer and response consumer.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context,
            final FutureCallback<T> callback) {
        final BasicFuture<T> future = new BasicFuture<>(callback);
        execute(new BasicClientExchangeHandler<>(requestProducer, responseConsumer,
                        new FutureContribution<T>(future) {

                            @Override
                            public void completed(final T result) {
                                future.completed(result);
                            }

                        }),
                pushHandlerFactory, context != null ? context : HttpCoreContext.create());
        return future;
    }

    /**
     * Initiates message exchange using the given request producer and response consumer.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, context, callback);
    }

    /**
     * Initiates a message exchange using the given request producer and response consumer.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public final <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, null, callback);
    }

}
