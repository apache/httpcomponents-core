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
package org.apache.http.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;

import javax.net.SocketFactory;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

/**
 * Worker thread for the {@link HttpBenchmark HttpBenchmark}.
 *
 *
 * @since 4.0
 */
class BenchmarkWorker implements Runnable {

    private final byte[] buffer = new byte[4096];
    private final HttpCoreContext context;
    private final HttpProcessor httpProcessor;
    private final HttpRequestExecutor httpexecutor;
    private final ConnectionReuseStrategy connstrategy;
    private final HttpRequest request;
    private final HttpHost targetHost;
    private final Config config;
    private final SocketFactory socketFactory;
    private final Stats stats = new Stats();

    public BenchmarkWorker(
            final HttpRequest request,
            final HttpHost targetHost,
            final SocketFactory socketFactory,
            final Config config) {
        super();
        this.context = new HttpCoreContext();
        this.request = request;
        this.targetHost = targetHost;
        this.config = config;
        this.httpProcessor = new ImmutableHttpProcessor(
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent("HttpCore-AB/1.1"),
                new RequestExpectContinue(this.config.isUseExpectContinue()));
        this.httpexecutor = new HttpRequestExecutor();

        this.connstrategy = DefaultConnectionReuseStrategy.INSTANCE;
        this.socketFactory = socketFactory;
    }

    @Override
    public void run() {
        HttpResponse response = null;
        final BenchmarkConnection conn = new BenchmarkConnection(8 * 1024, stats);

        final String scheme = targetHost.getSchemeName();
        final String hostname = targetHost.getHostName();
        int port = targetHost.getPort();
        if (port == -1) {
            if (scheme.equalsIgnoreCase("https")) {
                port = 443;
            } else {
                port = 80;
            }
        }

        // Populate the execution context
        this.context.setTargetHost(this.targetHost);

        stats.start();
        final int count = config.getRequests();
        for (int i = 0; i < count; i++) {

            try {
                resetHeader(request);
                if (!conn.isOpen()) {

                    final Socket socket;
                    if (socketFactory != null) {
                        socket = socketFactory.createSocket();
                    } else {
                        socket = new Socket();
                    }

                    final int timeout = config.getSocketTimeout();
                    socket.setSoTimeout(timeout);
                    socket.connect(new InetSocketAddress(hostname, port), timeout);

                    conn.bind(socket);
                }

                try {
                    // Prepare request
                    this.httpexecutor.preProcess(this.request, this.httpProcessor, this.context);
                    // Execute request and get a response
                    response = this.httpexecutor.execute(this.request, conn, this.context);
                    // Finalize response
                    this.httpexecutor.postProcess(response, this.httpProcessor, this.context);

                } catch (final HttpException e) {
                    stats.incWriteErrors();
                    if (config.getVerbosity() >= 2) {
                        System.err.println("Failed HTTP request : " + e.getMessage());
                    }
                    conn.shutdown();
                    continue;
                }

                verboseOutput(response);

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    stats.incSuccessCount();
                } else {
                    stats.incFailureCount();
                }

                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    final ContentType ct = ContentType.getOrDefault(entity);
                    Charset charset = ct.getCharset();
                    if (charset == null) {
                        charset = HTTP.DEF_CONTENT_CHARSET;
                    }
                    long contentlen = 0;
                    final InputStream instream = entity.getContent();
                    int l;
                    while ((l = instream.read(this.buffer)) != -1) {
                        contentlen += l;
                        if (config.getVerbosity() >= 4) {
                            final String s = new String(this.buffer, 0, l, charset);
                            System.out.print(s);
                        }
                    }
                    instream.close();
                    stats.setContentLength(contentlen);
                }

                if (config.getVerbosity() >= 4) {
                    System.out.println();
                    System.out.println();
                }

                if (!config.isKeepAlive() || !this.connstrategy.keepAlive(response, this.context)) {
                    conn.close();
                } else {
                    stats.incKeepAliveCount();
                }

            } catch (final IOException ex) {
                stats.incFailureCount();
                if (config.getVerbosity() >= 2) {
                    System.err.println("I/O error: " + ex.getMessage());
                }
            } catch (final Exception ex) {
                stats.incFailureCount();
                if (config.getVerbosity() >= 2) {
                    System.err.println("Generic error: " + ex.getMessage());
                }
            }

        }
        stats.finish();

        if (response != null) {
            final Header header = response.getFirstHeader("Server");
            if (header != null) {
                stats.setServerName(header.getValue());
            }
        }

        try {
            conn.close();
        } catch (final IOException ex) {
            stats.incFailureCount();
            if (config.getVerbosity() >= 2) {
                System.err.println("I/O error: " + ex.getMessage());
            }
        }
    }

    private void verboseOutput(final HttpResponse response) {
        if (config.getVerbosity() >= 3) {
            System.out.println(">> " + request.getRequestLine().toString());
            final Header[] headers = request.getAllHeaders();
            for (final Header header : headers) {
                System.out.println(">> " + header.toString());
            }
            System.out.println();
        }
        if (config.getVerbosity() >= 2) {
            System.out.println(response.getStatusLine().getStatusCode());
        }
        if (config.getVerbosity() >= 3) {
            System.out.println("<< " + response.getStatusLine().toString());
            final Header[] headers = response.getAllHeaders();
            for (final Header header : headers) {
                System.out.println("<< " + header.toString());
            }
            System.out.println();
        }
    }

    private static void resetHeader(final HttpRequest request) {
        for (final HeaderIterator it = request.headerIterator(); it.hasNext();) {
            final Header header = it.nextHeader();
            if (!(header instanceof DefaultHeader)) {
                it.remove();
            }
        }
    }

    public Stats getStats() {
        return stats;
    }
}
