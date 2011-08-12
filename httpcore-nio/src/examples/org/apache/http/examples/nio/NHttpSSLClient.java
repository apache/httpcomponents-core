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
package org.apache.http.examples.nio;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.pool.BasicNIOConnPool;
import org.apache.http.impl.nio.pool.BasicNIOPoolEntry;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.ssl.SSLClientIOEventDispatch;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

/**
 * Elemental example for executing HTTPS requests using the non-blocking I/O model.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of HttpCore APIs.
 * It is NOT intended to demonstrate the most efficient way of building an HTTP client.
 */
public class NHttpSSLClient {

    public static void main(String[] args) throws Exception {
        HttpParams params = new SyncBasicHttpParams();
        params
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000)
            .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.USER_AGENT, "Test/1.1");

        final ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();

        BasicNIOConnPool pool = new BasicNIOConnPool(ioReactor, params);
        // Limit total number of connections to just two
        pool.setDefaultMaxPerRoute(2);
        pool.setMaxTotal(2);

        HttpProcessor httpproc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        // Initialize default SSL context
        SSLContext sslcontext = SSLContext.getInstance("SSL");
        sslcontext.init(null, null, null);

        BufferingHttpClientHandler handler = new BufferingHttpClientHandler(
                httpproc,
                new MyHttpRequestExecutionHandler(),
                new DefaultConnectionReuseStrategy(),
                params);

        final IOEventDispatch ioEventDispatch = new SSLClientIOEventDispatch(
                handler,
                sslcontext,
                params);

        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    ioReactor.execute(ioEventDispatch);
                } catch (InterruptedIOException ex) {
                    System.err.println("Interrupted");
                } catch (IOException e) {
                    System.err.println("I/O error: " + e.getMessage());
                }
                System.out.println("Shutdown");
            }

        });
        t.start();

        CountDownLatch requestCount = new CountDownLatch(3);

        pool.lease(new HttpHost("www.verisign.com", 443), null,
                new AsyncRequestExecutor(new BasicHttpRequest("GET", "/"), pool, requestCount));
        pool.lease(new HttpHost("www.yahoo.com", 443), null,
                new AsyncRequestExecutor(new BasicHttpRequest("GET", "/"), pool, requestCount));
        pool.lease(new HttpHost("apache.org", 443), null,
                new AsyncRequestExecutor(new BasicHttpRequest("GET", "/"), pool, requestCount));

        // Block until all connections signal
        // completion of the request execution
        requestCount.await(30, TimeUnit.SECONDS);

        System.out.println("Shutting down I/O reactor");

        ioReactor.shutdown();

        System.out.println("Done");
    }

    static class AsyncRequestExecutor implements FutureCallback<BasicNIOPoolEntry> {

        private final HttpRequest request;
        private final BasicNIOConnPool pool;
        private final CountDownLatch requestCount;
        private volatile BasicNIOPoolEntry poolEntry;
        private volatile boolean completed;

        AsyncRequestExecutor(
                final HttpRequest request,
                final BasicNIOConnPool pool,
                final CountDownLatch requestCount) {
            super();
            this.request = request;
            this.pool = pool;
            this.requestCount = requestCount;
        }

        public void failed(final Exception ex) {
            this.requestCount.countDown();
            System.out.println("Connection request failed: " + ex.getMessage());
        }

        public void cancelled() {
            this.requestCount.countDown();
            System.out.println("Connection cancelled failed");
        }

        public void completed(final BasicNIOPoolEntry entry) {
            this.poolEntry = entry;
            IOSession session = entry.getConnection();
            session.setAttribute("executor", this);
            session.setEvent(SelectionKey.OP_WRITE);
            System.out.println(this.poolEntry.getRoute() + ": obtained connection from the pool");
        }

        public HttpRequest getRequest() {
            System.out.println(this.poolEntry.getRoute() + ": sending request " + this.request.getRequestLine());
            return this.request;
        }

        public void handleResponse(final HttpResponse response) {
            if (this.completed) {
                return;
            }
            System.out.println(this.poolEntry.getRoute() + ": received response " + response.getStatusLine());
            this.completed = true;
            this.requestCount.countDown();
            this.pool.release(this.poolEntry, false);
        }

        public void shutdown() {
            if (this.completed) {
                return;
            }
            this.completed = true;
            this.requestCount.countDown();
            this.pool.release(this.poolEntry, false);
        }

    };

    static class MyHttpRequestExecutionHandler implements HttpRequestExecutionHandler {

        public MyHttpRequestExecutionHandler() {
            super();
        }

        public void initalizeContext(final HttpContext context, final Object attachment) {
        }

        public void finalizeContext(final HttpContext context) {
            AsyncRequestExecutor executor = (AsyncRequestExecutor) context.getAttribute("executor");
            if (executor != null) {
                executor.shutdown();
            }
        }

        public HttpRequest submitRequest(final HttpContext context) {
            AsyncRequestExecutor executor = (AsyncRequestExecutor) context.getAttribute("executor");
            return executor.getRequest();
        }

        public void handleResponse(final HttpResponse response, final HttpContext context) {
            AsyncRequestExecutor executor = (AsyncRequestExecutor) context.getAttribute("executor");
            executor.handleResponse(response);
        }

    }

}
