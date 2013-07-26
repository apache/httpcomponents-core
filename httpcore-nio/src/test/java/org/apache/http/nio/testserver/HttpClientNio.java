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

package org.apache.http.nio.testserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.DefaultHttpClientIODispatch;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpClientConnectionFactory;
import org.apache.http.impl.nio.pool.BasicNIOConnPool;
import org.apache.http.impl.nio.pool.BasicNIOPoolEntry;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.ExceptionEvent;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.pool.NIOConnFactory;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncRequester;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

@SuppressWarnings("deprecation")
public class HttpClientNio {

    public static final HttpProcessor DEFAULT_HTTP_PROC = new ImmutableHttpProcessor(
            new HttpRequestInterceptor[] {
                    new RequestContent(),
                    new RequestTargetHost(),
                    new RequestConnControl(),
                    new RequestUserAgent("TEST-CLIENT/1.1"),
                    new RequestExpectContinue(true)});

    private final DefaultConnectingIOReactor ioReactor;
    private final BasicNIOConnPool connpool;

    private volatile  HttpAsyncRequester executor;
    private volatile IOReactorThread thread;
    private volatile int timeout;

    public HttpClientNio(
            final NIOConnFactory<HttpHost, NHttpClientConnection> connFactory) throws IOException {
        super();
        this.ioReactor = new DefaultConnectingIOReactor();
        this.connpool = new BasicNIOConnPool(this.ioReactor, new NIOConnFactory<HttpHost, NHttpClientConnection>() {

            public NHttpClientConnection create(
                final HttpHost route, final IOSession session) throws IOException {
                final NHttpClientConnection conn = connFactory.create(route, session);
                conn.setSocketTimeout(timeout);
                return conn;
            }

        }, 0);
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    public void setMaxTotal(final int max) {
        this.connpool.setMaxTotal(max);
    }

    public void setMaxPerRoute(final int max) {
        this.connpool.setDefaultMaxPerRoute(max);
    }

    public Future<BasicNIOPoolEntry> lease(
            final HttpHost host,
            final FutureCallback<BasicNIOPoolEntry> callback) {
        return this.connpool.lease(host, null, this.timeout, TimeUnit.MILLISECONDS, callback);
    }

    public void release(final BasicNIOPoolEntry poolEntry, final boolean reusable) {
        this.connpool.release(poolEntry, reusable);
    }

    public <T> Future<T> execute(
            final HttpAsyncRequestProducer requestProducer,
            final HttpAsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        final HttpHost target = requestProducer.getTarget();
        final BasicFuture<T> future = new BasicFuture<T>(callback);
        this.connpool.lease(target, null, this.timeout, TimeUnit.MILLISECONDS,
            new FutureCallback<BasicNIOPoolEntry>() {

                public void completed(final BasicNIOPoolEntry result) {
                    executor.execute(
                            requestProducer, responseConsumer,
                            result, connpool,
                            context != null ? context : new BasicHttpContext(),
                            new FutureCallback<T>() {

                                public void completed(final T result) {
                                    future.completed(result);
                                }

                                public void failed(final Exception ex) {
                                    future.failed(ex);
                                }

                                public void cancelled() {
                                    future.cancel();
                                }

                            });
                }

                public void failed(final Exception ex) {
                    future.failed(ex);
                }

                public void cancelled() {
                    future.cancel();
                }

            });
        return future;
    }

    public Future<HttpResponse> execute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context,
            final FutureCallback<HttpResponse> callback) {
        return execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                context != null ? context : new BasicHttpContext(),
                callback);
    }

    public Future<HttpResponse> execute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) {
        return execute(target, request, context, null);
    }

    public Future<HttpResponse> execute(
            final HttpHost target,
            final HttpRequest request) {
        return execute(target, request, null, null);
    }

    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
        this.ioReactor.setExceptionHandler(exceptionHandler);
    }

    private void execute(final NHttpClientEventHandler clientHandler) throws IOException {
        final IOEventDispatch ioEventDispatch = new DefaultHttpClientIODispatch(clientHandler,
            new DefaultNHttpClientConnectionFactory(ConnectionConfig.DEFAULT)) {

            @Override
            protected DefaultNHttpClientConnection createConnection(final IOSession session) {
                final DefaultNHttpClientConnection conn = super.createConnection(session);
                conn.setSocketTimeout(timeout);
                return conn;
            }

        };
        this.ioReactor.execute(ioEventDispatch);
    }

    public SessionRequest openConnection(final InetSocketAddress address, final Object attachment) {
        final SessionRequest sessionRequest = this.ioReactor.connect(address, null, attachment, null);
        sessionRequest.setConnectTimeout(this.timeout);
        return sessionRequest;
    }

    public void start(
            final HttpProcessor protocolProcessor,
            final NHttpClientEventHandler clientHandler) {
        this.executor = new HttpAsyncRequester(protocolProcessor != null ? protocolProcessor :
            DEFAULT_HTTP_PROC);
        this.thread = new IOReactorThread(clientHandler);
        this.thread.start();
    }

    public void start(
            final HttpProcessor protocolProcessor) {
        start(protocolProcessor, new HttpAsyncRequestExecutor());
    }

    public void start(
            final NHttpClientEventHandler clientHandler) {
        start(null, clientHandler);
    }

    public void start() {
        start(null, new HttpAsyncRequestExecutor());
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

        private final NHttpClientEventHandler clientHandler;

        private volatile Exception ex;

        public IOReactorThread(final NHttpClientEventHandler clientHandler) {
            super();
            this.clientHandler = clientHandler;
        }

        @Override
        public void run() {
            try {
                execute(this.clientHandler);
            } catch (final Exception ex) {
                this.ex = ex;
            }
        }

        public Exception getException() {
            return this.ex;
        }

    }

}
