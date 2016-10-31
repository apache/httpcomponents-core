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
package org.apache.hc.core5.http.impl.io.bootstrap;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.ResponseHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public class HttpRequester {

    private final HttpRequestExecutor requestExecutor;
    private final HttpProcessor httpProcessor;
    private final ConnectionReuseStrategy connStrategy;

    public HttpRequester(
            final HttpRequestExecutor requestExecutor,
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy) {
        this.requestExecutor = requestExecutor;
        this.httpProcessor = httpProcessor;
        this.connStrategy = connStrategy;
    }

    public ClassicHttpResponse execute(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(connection, "HTTP connection");
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");
        if (!connection.isOpen()) {
            throw new ConnectionClosedException("Connection is closed");
        }
        requestExecutor.preProcess(request, httpProcessor, context);
        final ClassicHttpResponse response = requestExecutor.execute(request, connection, context);
        requestExecutor.postProcess(response, httpProcessor, context);
        return response;
    }

    public boolean keepAlive(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final ClassicHttpResponse response,
            final HttpContext context) throws IOException {
        Args.notNull(connection, "HTTP connection");
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");
        Args.notNull(context, "HTTP context");
        final HttpEntity entity = response.getEntity();
        if (entity != null && entity.isStreaming()) {
            final InputStream instream = entity.getContent();
            if (instream != null) {
                instream.close();
            }
        }
        if (connStrategy.keepAlive(request, response, context)) {
            return true;
        } else {
            connection.close();
            return false;
        }
    }

    public <T> T execute(
            final HttpClientConnection connection,
            final ClassicHttpRequest request,
            final HttpContext context,
            final ResponseHandler<T> responseHandler) throws HttpException, IOException {
        final ClassicHttpResponse response = execute(connection, request, context);
        final T result = responseHandler.handleResponse(response);
        keepAlive(connection, request, response, context);
        return result;
    }

}
