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

package org.apache.hc.core5.http.testserver.nio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.OoopsieRuntimeException;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestProducer;
import org.apache.hc.core5.http.impl.nio.BasicAsyncResponseConsumer;
import org.apache.hc.core5.http.impl.nio.DefaultHttpClientIOEventHandlerFactory;
import org.apache.hc.core5.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.hc.core5.http.impl.nio.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.HttpAsyncRequestProducer;
import org.apache.hc.core5.http.nio.HttpAsyncResponseConsumer;
import org.apache.hc.core5.http.nio.NHttpClientEventHandler;
import org.apache.hc.core5.http.nio.NHttpConnectionFactory;
import org.apache.hc.core5.http.pool.nio.BasicNIOConnPool;
import org.apache.hc.core5.http.pool.nio.BasicNIOPoolEntry;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.ConnectingIOReactor;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorExceptionHandler;
import org.apache.hc.core5.reactor.IOReactorStatus;

public class HttpClientNio {

    private final DefaultConnectingIOReactor ioReactor;
    private final BasicNIOConnPool connpool;
    private final HttpAsyncRequester executor;
    private final IOReactorThread thread;

    public HttpClientNio(
            final HttpProcessor httpProcessor,
            final NHttpClientEventHandler protoclHandler,
            final NHttpConnectionFactory<DefaultNHttpClientConnection> connFactory,
            final IOReactorConfig reactorConfig) throws IOException {
        super();
        this.ioReactor = new DefaultConnectingIOReactor(
                new DefaultHttpClientIOEventHandlerFactory(protoclHandler, connFactory), reactorConfig);
        this.ioReactor.setExceptionHandler(new SimpleIOReactorExceptionHandler());
        this.connpool = new BasicNIOConnPool(this.ioReactor, reactorConfig.getConnectTimeout());
        this.executor = new HttpAsyncRequester(httpProcessor);
        this.thread = new IOReactorThread();
    }

    public void setMaxTotal(final int max) {
        this.connpool.setMaxTotal(max);
    }

    public void setMaxPerRoute(final int max) {
        this.connpool.setDefaultMaxPerRoute(max);
    }

    public Future<BasicNIOPoolEntry> lease(
            final HttpHost host,
            final int connectTimeout,
            final FutureCallback<BasicNIOPoolEntry> callback) {
        return this.connpool.lease(host, null, connectTimeout, TimeUnit.MILLISECONDS, callback);
    }

    public void release(final BasicNIOPoolEntry poolEntry, final boolean reusable) {
        this.connpool.release(poolEntry, reusable);
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        return executor.execute(requestProducer, responseConsumer, this.connpool,
                context != null ? context : HttpCoreContext.create(), callback);
    }

    public <T> Future<List<T>> executePipelined(
            final HttpHost target,
            final List<HttpAsyncRequestProducer> requestProducers,
            final List<HttpAsyncResponseConsumer<T>> responseConsumers,
            final HttpContext context,
            final FutureCallback<List<T>> callback) {
        return executor.executePipelined(target, requestProducers, responseConsumers, this.connpool,
                context != null ? context : HttpCoreContext.create(), callback);
    }

    public Future<ClassicHttpResponse> execute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context,
            final FutureCallback<ClassicHttpResponse> callback) {
        return execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                context != null ? context : HttpCoreContext.create(),
                callback);
    }

    public Future<List<ClassicHttpResponse>> executePipelined(
            final HttpHost target,
            final List<ClassicHttpRequest> requests,
            final HttpContext context,
            final FutureCallback<List<ClassicHttpResponse>> callback) {
        final List<HttpAsyncRequestProducer> requestProducers =
                new ArrayList<>(requests.size());
        final List<HttpAsyncResponseConsumer<ClassicHttpResponse>> responseConsumers =
                new ArrayList<>(requests.size());
        for (final ClassicHttpRequest request: requests) {
            requestProducers.add(new BasicAsyncRequestProducer(target, request));
            responseConsumers.add(new BasicAsyncResponseConsumer());
        }
        return executor.executePipelined(target, requestProducers, responseConsumers, this.connpool,
                context != null ? context : HttpCoreContext.create(), callback);
    }

    public Future<ClassicHttpResponse> execute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) {
        return execute(target, request, context, null);
    }

    public Future<List<ClassicHttpResponse>> executePipelined(
            final HttpHost target,
            final List<ClassicHttpRequest> requests,
            final HttpContext context) {
        return executePipelined(target, requests, context, null);
    }

    public Future<ClassicHttpResponse> execute(
            final HttpHost target,
            final ClassicHttpRequest request) {
        return execute(target, request, null, null);
    }

    public Future<List<ClassicHttpResponse>> executePipelined(
            final HttpHost target,
            final ClassicHttpRequest... requests) {
        return executePipelined(target, Arrays.asList(requests), null, null);
    }

    public void start() {
        this.thread.start();
    }

    public ConnectingIOReactor getIoReactor() {
        return this.ioReactor;
    }

    public IOReactorStatus getStatus() {
        return this.ioReactor.getStatus();
    }

    public List<ExceptionEvent> getAuditLog() {
        return this.ioReactor.getAuditLog();
    }

    public void join(final long timeout) throws InterruptedException {
        if (this.thread != null) {
            this.thread.join(timeout);
        }
    }

    public Exception getException() {
        if (this.thread != null) {
            return this.thread.getException();
        } else {
            return null;
        }
    }

    public void shutdown() throws IOException {
        this.connpool.shutdown(2000);
        try {
            join(500);
        } catch (final InterruptedException ignore) {
        }
    }

    private class IOReactorThread extends Thread {

        private volatile Exception ex;

        public IOReactorThread() {
            super();
        }

        @Override
        public void run() {
            try {
                ioReactor.execute();
            } catch (final Exception ex) {
                this.ex = ex;
            }
        }

        public Exception getException() {
            return this.ex;
        }

    }

    static class SimpleIOReactorExceptionHandler implements IOReactorExceptionHandler {

        @Override
        public boolean handle(final RuntimeException ex) {
            if (!(ex instanceof OoopsieRuntimeException)) {
                ex.printStackTrace(System.out);
            }
            return false;
        }

        @Override
        public boolean handle(final IOException ex) {
            ex.printStackTrace(System.out);
            return false;
        }

    }

}
