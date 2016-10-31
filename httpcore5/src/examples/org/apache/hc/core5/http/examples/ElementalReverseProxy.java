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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.UriHttpRequestHandlerMapper;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;

/**
 * Elemental HTTP/1.1 reverse proxy.
 */
public class ElementalReverseProxy {

    private static final String HTTP_IN_CONN = "http.proxy.in-conn";
    private static final String HTTP_OUT_CONN = "http.proxy.out-conn";
    private static final String HTTP_CONN_KEEPALIVE = "http.proxy.conn-keepalive";

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specified target hostname and port");
            System.exit(1);
        }
        final String hostname = args[0];
        int port = 80;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        final HttpHost target = new HttpHost(hostname, port);

        final Thread t = new RequestListenerThread(8888, target);
        t.setDaemon(false);
        t.start();
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

        private final HttpProcessor httpproc;
        private final HttpRequestExecutor httpexecutor;
        private final ConnectionReuseStrategy connStrategy;

        public ProxyHandler(
                final HttpProcessor httpproc,
                final HttpRequestExecutor httpexecutor) {
            super();
            this.httpproc = httpproc;
            this.httpexecutor = httpexecutor;
            this.connStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        }

        @Override
        public void handle(
                final ClassicHttpRequest incomingRequest,
                final ClassicHttpResponse outgoingResponse,
                final HttpContext context) throws HttpException, IOException {

            final HttpClientConnection conn = (HttpClientConnection) context.getAttribute(
                    HTTP_OUT_CONN);

            context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);

            System.out.println(">> Request URI: " + incomingRequest.getPath());

            ClassicHttpRequest outgoingRequest = new BasicClassicHttpRequest(incomingRequest.getMethod(), incomingRequest.getPath());
            for (Iterator<Header> it = incomingRequest.headerIterator(); it.hasNext(); ) {
                Header header = it.next();
                if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                    outgoingRequest.addHeader(header);
                }
            }
            this.httpexecutor.preProcess(outgoingRequest, this.httpproc, context);
            final ClassicHttpResponse incomingResponse = this.httpexecutor.execute(outgoingRequest, conn, context);
            this.httpexecutor.postProcess(incomingResponse, this.httpproc, context);

            outgoingResponse.setCode(incomingResponse.getCode());
            for (Iterator<Header> it = incomingResponse.headerIterator(); it.hasNext(); ) {
                Header header = it.next();
                if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                    outgoingResponse.addHeader(header);
                }
            }
            outgoingResponse.setEntity(incomingResponse.getEntity());

            System.out.println("<< Response: " + outgoingResponse.getCode());

            final boolean keepalive = this.connStrategy.keepAlive(incomingRequest, outgoingResponse, context);
            context.setAttribute(HTTP_CONN_KEEPALIVE, new Boolean(keepalive));
        }

    }

    static class RequestListenerThread extends Thread {

        private final HttpHost target;
        private final ServerSocket serversocket;
        private final HttpService httpService;

        public RequestListenerThread(final int port, final HttpHost target) throws IOException {
            this.target = target;
            this.serversocket = new ServerSocket(port);

            // Set up HTTP protocol processor for incoming connections
            final HttpProcessor inhttpproc = HttpProcessors.client();

            // Set up HTTP protocol processor for outgoing connections
            final HttpProcessor outhttpproc = HttpProcessors.server();

            // Set up outgoing request executor
            final HttpRequestExecutor httpexecutor = new HttpRequestExecutor();

            // Set up incoming request handler
            final UriHttpRequestHandlerMapper reqistry = new UriHttpRequestHandlerMapper();
            reqistry.register("*", new ProxyHandler(outhttpproc, httpexecutor));

            // Set up the HTTP service
            this.httpService = new HttpService(inhttpproc, reqistry);
        }

        @Override
        public void run() {
            System.out.println("Listening on port " + this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    final int bufsize = 8 * 1024;
                    // Set up incoming HTTP connection
                    final Socket insocket = this.serversocket.accept();
                    final DefaultBHttpServerConnection inconn = new DefaultBHttpServerConnection(bufsize);
                    System.out.println("Incoming connection from " + insocket.getInetAddress());
                    inconn.bind(insocket);

                    // Set up outgoing HTTP connection
                    final Socket outsocket = new Socket(this.target.getHostName(), this.target.getPort());
                    final DefaultBHttpClientConnection outconn = new DefaultBHttpClientConnection(bufsize);
                    outconn.bind(outsocket);
                    System.out.println("Outgoing connection to " + outsocket.getInetAddress());

                    // Start worker thread
                    final Thread t = new ProxyThread(this.httpService, inconn, outconn);
                    t.setDaemon(true);
                    t.start();
                } catch (final InterruptedIOException ex) {
                    break;
                } catch (final IOException e) {
                    System.err.println("I/O error initialising connection thread: "
                            + e.getMessage());
                    break;
                }
            }
        }
    }

    static class ProxyThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection inconn;
        private final HttpClientConnection outconn;

        public ProxyThread(
                final HttpService httpservice,
                final HttpServerConnection inconn,
                final HttpClientConnection outconn) {
            super();
            this.httpservice = httpservice;
            this.inconn = inconn;
            this.outconn = outconn;
        }

        @Override
        public void run() {
            System.out.println("New connection thread");
            final HttpContext context = new BasicHttpContext(null);

            // Bind connection objects to the execution context
            context.setAttribute(HTTP_IN_CONN, this.inconn);
            context.setAttribute(HTTP_OUT_CONN, this.outconn);

            try {
                while (!Thread.interrupted()) {
                    if (!this.inconn.isOpen()) {
                        this.outconn.close();
                        break;
                    }

                    this.httpservice.handleRequest(this.inconn, context);

                    final Boolean keepalive = (Boolean) context.getAttribute(HTTP_CONN_KEEPALIVE);
                    if (!Boolean.TRUE.equals(keepalive)) {
                        this.outconn.close();
                        this.inconn.close();
                        break;
                    }
                }
            } catch (final ConnectionClosedException ex) {
                System.err.println("Client closed connection");
            } catch (final IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (final HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.inconn.shutdown();
                } catch (final IOException ignore) {}
                try {
                    this.outconn.shutdown();
                } catch (final IOException ignore) {}
            }
        }

    }

}
