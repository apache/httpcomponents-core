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
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
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
    private final int verbosity;
    private final HttpContext context;
    private final HttpProcessor httpProcessor;
    private final HttpRequestExecutor httpexecutor;
    private final ConnectionReuseStrategy connstrategy;
    private final HttpRequest request;
    private final HttpHost targetHost;
    private final int count;
    private final boolean keepalive;
    private final SocketFactory socketFactory;
    private final Stats stats = new Stats();

    public BenchmarkWorker(
            final HttpRequest request,
            final HttpHost targetHost,
            int count,
            boolean keepalive,
            int verbosity,
            final SocketFactory socketFactory) {

        super();
        this.context = new BasicHttpContext(null);
        this.request = request;
        this.targetHost = targetHost;
        this.count = count;
        this.keepalive = keepalive;

        this.httpProcessor = new ImmutableHttpProcessor(
                new RequestContent(), 
                new RequestTargetHost(), 
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue());
        this.httpexecutor = new HttpRequestExecutor();

        this.connstrategy = DefaultConnectionReuseStrategy.INSTANCE;
        this.verbosity = verbosity;
        this.socketFactory = socketFactory;
    }

    public void run() {

        HttpResponse response = null;
        BenchmarkConnection conn = new BenchmarkConnection(this.stats);

        String scheme = targetHost.getSchemeName();
        String hostname = targetHost.getHostName();
        int port = targetHost.getPort();
        if (port == -1) {
            if (scheme.equalsIgnoreCase("https")) {
                port = 443;
            } else {
                port = 80;
            }
        }

        // Populate the execution context
        this.context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        this.context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.targetHost);
        this.context.setAttribute(ExecutionContext.HTTP_REQUEST, this.request);

        stats.start();
        for (int i = 0; i < count; i++) {

            try {
                resetHeader(request);
                if (!conn.isOpen()) {
                    
                    Socket socket;
                    if (socketFactory != null) {
                        socket = socketFactory.createSocket();
                    } else {
                        socket = new Socket();
                    }
                    
                    HttpParams params = request.getParams();
                    int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
                    int soTimeout = HttpConnectionParams.getSoTimeout(params);

                    socket.setSoTimeout(soTimeout);
                    socket.connect(new InetSocketAddress(hostname, port), connTimeout);
                    
                    conn.bind(socket, params);
                }

                try {
                    // Prepare request
                    this.httpexecutor.preProcess(this.request, this.httpProcessor, this.context);
                    // Execute request and get a response
                    response = this.httpexecutor.execute(this.request, conn, this.context);
                    // Finalize response
                    this.httpexecutor.postProcess(response, this.httpProcessor, this.context);

                } catch (HttpException e) {
                    stats.incWriteErrors();
                    if (this.verbosity >= 2) {
                        System.err.println("Failed HTTP request : " + e.getMessage());
                    }
                    continue;
                }

                verboseOutput(response);

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    stats.incSuccessCount();
                } else {
                    stats.incFailureCount();
                    continue;
                }

                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    ContentType ct = ContentType.get(entity);
                    Charset charset = ct.getCharset();
                    if (charset == null) {
                        charset = HTTP.DEF_CONTENT_CHARSET;
                    }
                    long contentlen = 0;
                    InputStream instream = entity.getContent();
                    int l = 0;
                    while ((l = instream.read(this.buffer)) != -1) {
                        contentlen += l;
                        if (this.verbosity >= 4) {
                            String s = new String(this.buffer, 0, l, charset.name());
                            System.out.print(s);
                        }
                    }
                    instream.close();
                    stats.setContentLength(contentlen);
                }

                if (this.verbosity >= 4) {
                    System.out.println();
                    System.out.println();
                }

                if (!keepalive || !this.connstrategy.keepAlive(response, this.context)) {
                    conn.close();
                } else {
                    stats.incKeepAliveCount();
                }

            } catch (IOException ex) {
                stats.incFailureCount();
                if (this.verbosity >= 2) {
                    System.err.println("I/O error: " + ex.getMessage());
                }
            } catch (Exception ex) {
                stats.incFailureCount();
                if (this.verbosity >= 2) {
                    System.err.println("Generic error: " + ex.getMessage());
                }
            }

        }
        stats.finish();

        if (response != null) {
            Header header = response.getFirstHeader("Server");
            if (header != null) {
                stats.setServerName(header.getValue());
            }
        }

        try {
            conn.close();
        } catch (IOException ex) {
            stats.incFailureCount();
            if (this.verbosity >= 2) {
                System.err.println("I/O error: " + ex.getMessage());
            }
        }
    }

    private void verboseOutput(HttpResponse response) {
        if (this.verbosity >= 3) {
            System.out.println(">> " + request.getRequestLine().toString());
            Header[] headers = request.getAllHeaders();
            for (int h = 0; h < headers.length; h++) {
                System.out.println(">> " + headers[h].toString());
            }
            System.out.println();
        }
        if (this.verbosity >= 2) {
            System.out.println(response.getStatusLine().getStatusCode());
        }
        if (this.verbosity >= 3) {
            System.out.println("<< " + response.getStatusLine().toString());
            Header[] headers = response.getAllHeaders();
            for (int h = 0; h < headers.length; h++) {
                System.out.println("<< " + headers[h].toString());
            }
            System.out.println();
        }
    }

    private static void resetHeader(final HttpRequest request) {
        for (HeaderIterator it = request.headerIterator(); it.hasNext();) {
            Header header = it.nextHeader();
            if (!(header instanceof DefaultHeader)) {
                it.remove();
            }
        }
    }

    public Stats getStats() {
        return stats;
    }
}
