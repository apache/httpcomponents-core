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
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.io.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.io.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.impl.io.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.impl.io.pool.BasicConnPool;
import org.apache.hc.core5.http.impl.io.pool.BasicPoolEntry;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

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
                .create();

        final BasicConnPool connPool = new BasicConnPool();
        connPool.setDefaultMaxPerRoute(20);
        connPool.setMaxTotal(100);

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setExceptionListener(new ExceptionListener() {

                    @Override
                    public void onError(final Exception ex) {
                        if (ex instanceof SocketTimeoutException) {
                            System.err.println("Connection timed out");
                        } else if (ex instanceof ConnectionClosedException) {
                            System.err.println(ex.getMessage());
                        } else {
                            ex.printStackTrace();
                        }
                    }

                })
                .registerHandler("*", new ProxyHandler(targetHost, connPool, requester))
                .create();

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown(5, TimeUnit.SECONDS);
            }
        });

        System.out.println("Listening on port " + port);
        server.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
    }

    private final static Set<String> HOP_BY_HOP = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            "Keep-Alive".toLowerCase(Locale.ROOT),
            "Proxy-Authenticate".toLowerCase(Locale.ROOT),
            HttpHeaders.TE.toLowerCase(Locale.ROOT),
            HttpHeaders.TRAILER.toLowerCase(Locale.ROOT),
            HttpHeaders.UPGRADE.toLowerCase(Locale.ROOT))));


    static class ProxyHandler implements HttpRequestHandler  {

        private final HttpHost targetHost;
        private final BasicConnPool connPool;
        private final HttpRequester requester;

        public ProxyHandler(
                final HttpHost targetHost,
                final BasicConnPool connPool,
                final HttpRequester requester) {
            super();
            this.targetHost = targetHost;
            this.connPool = connPool;
            this.requester = requester;
        }

        @Override
        public void handle(
                final ClassicHttpRequest incomingRequest,
                final ClassicHttpResponse outgoingResponse,
                final HttpContext serverContext) throws HttpException, IOException {

            final Future<BasicPoolEntry> future = connPool.lease(targetHost, null);
            final BasicPoolEntry poolEntry;
            try {
                poolEntry = future.get();
            } catch (InterruptedException ex) {
                throw new InterruptedIOException();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else {
                    throw new IOException("Failure obtaining connection to " + targetHost);
                }
            }
            final HttpCoreContext clientContext = HttpCoreContext.create();
            final ClassicHttpRequest outgoingRequest = new BasicClassicHttpRequest(incomingRequest.getMethod(), incomingRequest.getPath());
            for (Iterator<Header> it = incomingRequest.headerIterator(); it.hasNext(); ) {
                Header header = it.next();
                if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                    outgoingRequest.addHeader(header);
                }
            }
            final HttpClientConnection connection = poolEntry.getConnection();
            final ClassicHttpResponse incomingResponse = requester.execute(connection, outgoingRequest, clientContext);
            outgoingResponse.setCode(incomingResponse.getCode());
            for (Iterator<Header> it = incomingResponse.headerIterator(); it.hasNext(); ) {
                Header header = it.next();
                if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                    outgoingResponse.addHeader(header);
                }
            }
            outgoingResponse.setEntity(new HttpEntityWrapper(incomingResponse.getEntity()) {

                @Override
                public void close() throws IOException {
                    boolean keepAlive = false;
                    try {
                        super.close();
                        keepAlive = requester.keepAlive(connection, outgoingRequest, incomingResponse, clientContext);
                    } finally {
                        connPool.release(poolEntry, keepAlive);
                    }
                }

            });
        }
    }

}
