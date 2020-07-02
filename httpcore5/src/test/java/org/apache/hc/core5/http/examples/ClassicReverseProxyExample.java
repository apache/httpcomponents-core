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

package org.apache.hc.core5.http.examples;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolStats;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of embedded HTTP/1.1 reverse proxy using classic I/O.
 */
public class ClassicReverseProxyExample {

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: <hostname[:port]> [listener port]");
            System.exit(1);
        }
        final HttpHost targetHost = HttpHost.create(args[0]);
        int port = 8080;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        System.out.println("Reverse proxy to " + targetHost);

        final HttpRequester requester = RequesterBootstrap.bootstrap()
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        System.out.println("[proxy->origin] " + Thread.currentThread()  + " " +
                                request.getMethod() + " " + request.getRequestUri());
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        System.out.println("[proxy<-origin] " + Thread.currentThread()  + " status " + response.getCode());
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        System.out.println("[proxy<-origin] " + Thread.currentThread() + " exchange completed; " +
                                "connection " + (keepAlive ? "kept alive" : "cannot be kept alive"));
                    }

                })
                .setConnPoolListener(new ConnPoolListener<HttpHost>() {

                    @Override
                    public void onLease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                        final StringBuilder buf = new StringBuilder();
                        buf.append("[proxy->origin] " + Thread.currentThread()  + " connection leased ").append(route);
                        System.out.println(buf);
                    }

                    @Override
                    public void onRelease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                        final StringBuilder buf = new StringBuilder();
                        buf.append("[proxy->origin] " + Thread.currentThread()  + " connection released ").append(route);
                        final PoolStats totals = connPoolStats.getTotalStats();
                        buf.append("; total kept alive: ").append(totals.getAvailable()).append("; ");
                        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
                        buf.append(" of ").append(totals.getMax());
                        System.out.println(buf);
                    }

                })
                .create();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        System.out.println("[client->proxy] " + Thread.currentThread() + " " +
                                request.getMethod() + " " + request.getRequestUri());
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        System.out.println("[client<-proxy] " + Thread.currentThread() + " status " + response.getCode());
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        System.out.println("[client<-proxy] " + Thread.currentThread() + " exchange completed; " +
                                "connection " + (keepAlive ? "kept alive" : "cannot be kept alive"));
                    }

                })
                .setExceptionListener(new ExceptionListener() {

                    @Override
                    public void onError(final Exception ex) {
                        if (ex instanceof SocketException) {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " " + ex.getMessage());
                        } else {
                            System.out.println("[client->proxy] " + Thread.currentThread()  + " " + ex.getMessage());
                            ex.printStackTrace(System.out);
                        }
                    }

                    @Override
                    public void onError(final HttpConnection connection, final Exception ex) {
                        if (ex instanceof SocketTimeoutException) {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " time out");
                        } else if (ex instanceof SocketException || ex instanceof ConnectionClosedException) {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " " + ex.getMessage());
                        } else {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " " + ex.getMessage());
                            ex.printStackTrace(System.out);
                        }
                    }

                })
                .register("*", new ProxyHandler(targetHost, requester))
                .create();

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.close(CloseMode.GRACEFUL);
                requester.close(CloseMode.GRACEFUL);
            }
        });

        System.out.println("Listening on port " + port);
        server.awaitTermination(TimeValue.MAX_VALUE);
    }

    private final static Set<String> HOP_BY_HOP = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            HttpHeaders.HOST.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            HttpHeaders.KEEP_ALIVE.toLowerCase(Locale.ROOT),
            HttpHeaders.PROXY_AUTHENTICATE.toLowerCase(Locale.ROOT),
            HttpHeaders.TE.toLowerCase(Locale.ROOT),
            HttpHeaders.TRAILER.toLowerCase(Locale.ROOT),
            HttpHeaders.UPGRADE.toLowerCase(Locale.ROOT))));


    static class ProxyHandler implements HttpRequestHandler  {

        private final HttpHost targetHost;
        private final HttpRequester requester;

        public ProxyHandler(
                final HttpHost targetHost,
                final HttpRequester requester) {
            super();
            this.targetHost = targetHost;
            this.requester = requester;
        }

        @Override
        public void handle(
                final ClassicHttpRequest incomingRequest,
                final ClassicHttpResponse outgoingResponse,
                final HttpContext serverContext) throws HttpException, IOException {

            final HttpCoreContext clientContext = HttpCoreContext.create();
            final ClassicHttpRequest outgoingRequest = new BasicClassicHttpRequest(
                    incomingRequest.getMethod(),
                    targetHost,
                    incomingRequest.getPath());
            for (final Iterator<Header> it = incomingRequest.headerIterator(); it.hasNext(); ) {
                final Header header = it.next();
                if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                    outgoingRequest.addHeader(header);
                }
            }
            outgoingRequest.setEntity(incomingRequest.getEntity());
            final ClassicHttpResponse incomingResponse = requester.execute(
                    targetHost, outgoingRequest, Timeout.ofMinutes(1), clientContext);
            outgoingResponse.setCode(incomingResponse.getCode());
            for (final Iterator<Header> it = incomingResponse.headerIterator(); it.hasNext(); ) {
                final Header header = it.next();
                if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                    outgoingResponse.addHeader(header);
                }
            }
            outgoingResponse.setEntity(incomingResponse.getEntity());
        }
    }

}
