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

import java.io.IOException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

class HttpAsyncClientExchangeHandlerImpl<T> implements HttpAsyncClientExchangeHandler<T> {

    private final HttpAsyncRequestProducer requestProducer;
    private final HttpAsyncResponseConsumer<T> responseConsumer;
    private final HttpContext localContext;
    private final HttpProcessor httppocessor;
    private final NHttpClientConnection conn;
    private final ConnectionReuseStrategy reuseStrategy;
    private final HttpParams params;

    public HttpAsyncClientExchangeHandlerImpl(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final HttpContext localContext,
            final HttpProcessor httppocessor,
            final NHttpClientConnection conn,
            final ConnectionReuseStrategy reuseStrategy,
            final HttpParams params) {
        super();
        if (requestProducer == null) {
            throw new IllegalArgumentException("Request producer may not be null");
        }
        if (responseConsumer == null) {
            throw new IllegalArgumentException("Response consumer may not be null");
        }
        if (localContext == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        if (httppocessor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("HTTP connection may not be null");
        }
        if (reuseStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.requestProducer = requestProducer;
        this.responseConsumer = responseConsumer;
        this.localContext = localContext;
        this.httppocessor = httppocessor;
        this.conn = conn;
        this.reuseStrategy = reuseStrategy;
        this.params = params;
    }

    public void close() throws IOException {
        this.responseConsumer.close();
        this.requestProducer.close();
    }

    public HttpHost getTarget() {
        return this.requestProducer.getTarget();
    }

    public HttpRequest generateRequest() throws IOException, HttpException {
        HttpHost target = this.requestProducer.getTarget();
        HttpRequest request = this.requestProducer.generateRequest();
        request.setParams(new DefaultedHttpParams(request.getParams(), this.params));

        this.localContext.setAttribute(ExecutionContext.HTTP_REQUEST, request);
        this.localContext.setAttribute(ExecutionContext.HTTP_TARGET_HOST, target);
        this.localContext.setAttribute(ExecutionContext.HTTP_CONNECTION, this.conn);

        this.httppocessor.process(request, this.localContext);

        return request;
    }

    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        this.requestProducer.produceContent(encoder, ioctrl);
        if (encoder.isCompleted()) {
            this.requestProducer.close();
        }
    }

    public boolean isRepeatable() {
        return false;
    }

    public void resetRequest() {
    }

    public void responseReceived(final HttpResponse response) throws IOException, HttpException {
        response.setParams(new DefaultedHttpParams(response.getParams(), this.params));
        this.localContext.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
        this.httppocessor.process(response, this.localContext);
        this.responseConsumer.responseReceived(response);
    }

    public void consumeContent(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        this.responseConsumer.consumeContent(decoder, ioctrl);
    }

    public void responseCompleted(final HttpContext context) {
        this.responseConsumer.responseCompleted(context);
    }

    public void failed(final Exception ex) {
        this.responseConsumer.failed(ex);
    }

    public void cancel() {
        this.responseConsumer.cancel();
    }

    public T getResult() {
        return this.responseConsumer.getResult();
    }

    public Exception getException() {
        return this.responseConsumer.getException();
    }

    public HttpContext getContext() {
        return this.localContext;
    }

    public ConnectionReuseStrategy getConnectionReuseStrategy() {
        return this.reuseStrategy;
    }

    public boolean isDone() {
        return this.responseConsumer.isDone();
    }

}
