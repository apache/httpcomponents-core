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
package org.apache.http.nio.protocol;

import java.util.concurrent.Future;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

public class HttpAsyncRequestExecutor {

    private final HttpProcessor httppocessor;
    private final ConnectionReuseStrategy reuseStrategy;
    private final HttpParams params;

    public HttpAsyncRequestExecutor(
            final HttpProcessor httppocessor,
            final ConnectionReuseStrategy reuseStrategy,
            final HttpParams params) {
        super();
        this.httppocessor = httppocessor;
        this.reuseStrategy = reuseStrategy;
        this.params = params;
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn,
            final HttpContext context,
            final FutureCallback<T> callback) {
        if (conn == null) {
            throw new IllegalArgumentException("HTTP connection may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        BasicFuture<T> future = new BasicFuture<T>(callback);
        HttpAsyncClientExchangeHandler<T> handler = new HttpAsyncClientExchangeHandlerImpl<T>(
                future, requestProducer, responseConsumer, context,
                this.httppocessor, conn, this.reuseStrategy, this.params);
        conn.getContext().setAttribute(HttpAsyncClientProtocolHandler.HTTP_HANDLER, handler);
        conn.requestOutput();
        return future;
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn,
            final HttpContext context) {
        return execute(requestProducer, responseConsumer, conn, context);
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final NHttpClientConnection conn) {
        return execute(requestProducer, responseConsumer, conn, new BasicHttpContext());
    }

}
