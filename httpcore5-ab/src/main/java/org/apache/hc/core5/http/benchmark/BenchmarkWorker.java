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
package org.apache.hc.core5.http.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import javax.net.SocketFactory;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;

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
    private final HttpHost host;
    private final ClassicHttpRequest request;
    private final Config config;
    private final SocketFactory socketFactory;
    private final Stats stats = new Stats();

    public BenchmarkWorker(
            final HttpHost host,
            final ClassicHttpRequest request,
            final SocketFactory socketFactory,
            final Config config) {
        super();
        this.context = new HttpCoreContext();
        this.host = host;
        this.request = request;
        this.config = config;
        final HttpProcessorBuilder builder = HttpProcessorBuilder.create()
                .addAll(
                        new RequestContent(),
                        new RequestTargetHost(),
                        new RequestConnControl(),
                        new RequestUserAgent("HttpCore-AB/1.1"));
        if (this.config.isUseExpectContinue()) {
            builder.add(new RequestExpectContinue());
        }
        this.httpProcessor = builder.build();
        this.httpexecutor = new HttpRequestExecutor();

        this.connstrategy = DefaultConnectionReuseStrategy.INSTANCE;
        this.socketFactory = socketFactory;
    }

    @Override
    public void run() {
        ClassicHttpResponse response = null;
        final HttpVersion version = config.isUseHttp1_0() ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
        final BenchmarkConnection conn = new BenchmarkConnection(H1Config.DEFAULT, stats);

        final String scheme = this.host.getSchemeName();
        final String hostname = this.host.getHostName();
        int port = this.host.getPort();
        if (port == -1) {
            if (scheme.equalsIgnoreCase("https")) {
                port = 443;
            } else {
                port = 80;
            }
        }

        // Populate the execution context
        context.setProtocolVersion(version);

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

                if (response.getCode() == HttpStatus.SC_OK) {
                    stats.incSuccessCount();
                } else {
                    stats.incFailureCount();
                }

                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    final ContentType ct = EntityUtils.getContentTypeOrDefault(entity);
                    Charset charset = ct.getCharset();
                    if (charset == null) {
                        charset = StandardCharsets.ISO_8859_1;
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

                if (!config.isKeepAlive() || !conn.isConsistent() || !this.connstrategy.keepAlive(request, response, this.context)) {
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

    private void verboseOutput(final ClassicHttpResponse response) {
        if (config.getVerbosity() >= 3) {
            System.out.println(">> " + request.getMethod() + " " + request.getRequestUri());
            final Header[] headers = request.getAllHeaders();
            for (final Header header : headers) {
                System.out.println(">> " + header.toString());
            }
            System.out.println();
        }
        if (config.getVerbosity() >= 2) {
            System.out.println(response.getCode());
        }
        if (config.getVerbosity() >= 3) {
            System.out.println("<< " + response.getCode() + " " + response.getReasonPhrase());
            final Header[] headers = response.getAllHeaders();
            for (final Header header : headers) {
                System.out.println("<< " + header.toString());
            }
            System.out.println();
        }
    }

    private static void resetHeader(final ClassicHttpRequest request) {
        for (final Iterator<Header> it = request.headerIterator(); it.hasNext();) {
            final Header header = it.next();
            if (!(header instanceof DefaultHeader)) {
                it.remove();
            }
        }
    }

    public Stats getStats() {
        return stats;
    }
}
