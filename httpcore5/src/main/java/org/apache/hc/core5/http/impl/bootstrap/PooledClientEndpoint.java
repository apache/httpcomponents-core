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

import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Asserts;

/**
 * Client endpoint leased from a pool of connections.
 * <p>
 * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
 * or {@link #releaseAndDiscard()}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class PooledClientEndpoint {

    private final PoolEntryHolder<HttpHost, ClientSessionEndpoint> poolEntryHolder;

    PooledClientEndpoint(final PoolEntryHolder<HttpHost, ClientSessionEndpoint> poolEntryHolder) {
        super();
        this.poolEntryHolder = poolEntryHolder;
    }

    private ClientSessionEndpoint getClientEndpoint() {
        final ClientSessionEndpoint endpoint = poolEntryHolder.getConnection();
        Asserts.check(endpoint != null, "Client endpoint already released");
        return endpoint;
    }

    /**
     * Initiates a message exchange using the given handler.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public void execute(final AsyncClientExchangeHandler exchangeHandler, final HttpContext context) {
        getClientEndpoint().execute(exchangeHandler, context);
    }

    /**
     * Initiates message exchange using the given request producer and response consumer.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return getClientEndpoint().execute(requestProducer, responseConsumer, context, callback);
    }

    /**
     * Initiates a message exchange using the given request producer and response consumer.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #releaseAndReuse()}
     * or {@link #releaseAndDiscard()}.
     */
    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, callback);
    }

    /**
     * Initiates a message exchange using the given request producer and response consumer and
     * automatically invokes {@link #releaseAndReuse()} upon its successful completion.
     */
    public <T> Future<T> executeAndRelease(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return getClientEndpoint().execute(requestProducer, responseConsumer, context, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                try {
                    if (callback != null) {
                        callback.completed(result);
                    }
                } finally {
                    releaseAndReuse();
                }
            }

            @Override
            public void failed(final Exception ex) {
                try {
                    if (callback != null) {
                        callback.failed(ex);
                    }
                } finally {
                    releaseAndDiscard();
                }
            }

            @Override
            public void cancelled() {
                try {
                    if (callback != null) {
                        callback.cancelled();
                    }
                } finally {
                    releaseAndDiscard();
                }
            }

        });
    }

    /**
     * Initiates a message exchange using the given request producer and response consumer and
     * automatically invokes {@link #releaseAndReuse()} upon its successful completion.
     */
    public <T> Future<T> executeAndRelease(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return executeAndRelease(requestProducer, responseConsumer, null, callback);
    }

    /**
     * Releases the underlying connection back to the connection pool as re-usable.
     */
    public void releaseAndReuse() {
        poolEntryHolder.markReusable();
        poolEntryHolder.releaseConnection();
    }

    /**
     * Shuts down the underlying connection and removes it from the connection pool.
     */
    public void releaseAndDiscard() {
        poolEntryHolder.abortConnection();
    }

    @Override
    public String toString() {
        final ClientSessionEndpoint endpoint = poolEntryHolder.getConnection();
        return endpoint != null ? endpoint.toString() : "released";
    }

}
