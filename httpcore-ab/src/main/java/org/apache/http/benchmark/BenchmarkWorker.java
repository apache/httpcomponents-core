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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;

import javax.net.SocketFactory;
import javax.net.ssl.*;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

/**
 * Worker thread for the {@link HttpBenchmark HttpBenchmark}.
 *
 *
 * @since 4.0
 */
public class BenchmarkWorker implements Runnable {

    private byte[] buffer = new byte[4096];
    private final int verbosity;
    private final HttpParams params;
    private final HttpContext context;
    private final BasicHttpProcessor httpProcessor;
    private final HttpRequestExecutor httpexecutor;
    private final ConnectionReuseStrategy connstrategy;
    private final HttpRequest request;
    private final HttpHost targetHost;
    private final int count;
    private final boolean keepalive;
    private final boolean disableSSLVerification;
    private final Stats stats = new Stats();
    private final TrustManager[] trustAllCerts;
    private final String trustStorePath;
    private final String trustStorePassword;
    private final String identityStorePath;
    private final String identityStorePassword;

    public BenchmarkWorker(
            final HttpParams params,
            int verbosity,
            final HttpRequest request,
            final HttpHost targetHost,
            int count,
            boolean keepalive,
            boolean disableSSLVerification,
            String trustStorePath,
            String trustStorePassword,
            String identityStorePath,
            String identityStorePassword) {

        super();
        this.params = params;
        this.context = new BasicHttpContext(null);
        this.request = request;
        this.targetHost = targetHost;
        this.count = count;
        this.keepalive = keepalive;

        this.httpProcessor = new BasicHttpProcessor();
        this.httpexecutor = new HttpRequestExecutor();

        // Required request interceptors
        this.httpProcessor.addInterceptor(new RequestContent());
        this.httpProcessor.addInterceptor(new RequestTargetHost());
        // Recommended request interceptors
        this.httpProcessor.addInterceptor(new RequestConnControl());
        this.httpProcessor.addInterceptor(new RequestUserAgent());
        this.httpProcessor.addInterceptor(new RequestExpectContinue());

        this.connstrategy = new DefaultConnectionReuseStrategy();
        this.verbosity = verbosity;
        this.disableSSLVerification = disableSSLVerification;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.identityStorePath = identityStorePath;
        this.identityStorePassword = identityStorePassword;

        // Create a trust manager that does not validate certificate chains
        trustAllCerts = new TrustManager[]{
            new X509TrustManager() {

                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
        };
    }

    public void run() {

        HttpResponse response = null;
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();

        String hostname = targetHost.getHostName();
        int port = targetHost.getPort();
        if (port == -1) {
            port = 80;
        }

        // Populate the execution context
        this.context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        this.context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.targetHost);
        this.context.setAttribute(ExecutionContext.HTTP_REQUEST, this.request);

        stats.start();
        request.setParams(new DefaultedHttpParams(new BasicHttpParams(), this.params));
        for (int i = 0; i < count; i++) {

            try {
                resetHeader(request);
                if (!conn.isOpen()) {
                    Socket socket = null;
                    if ("https".equals(targetHost.getSchemeName())) {
                        if (disableSSLVerification) {
                            SSLContext sc = SSLContext.getInstance("SSL");
                            if (identityStorePath != null) {
                                KeyStore identityStore = KeyStore.getInstance(KeyStore.getDefaultType());
                                FileInputStream instream = new FileInputStream(identityStorePath);
                                try {
                                    identityStore.load(instream, identityStorePassword.toCharArray());
                                } finally {
                                    try { instream.close(); } catch (IOException ignore) {}
                                }
                                KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                                    KeyManagerFactory.getDefaultAlgorithm());
                                kmf.init(identityStore, identityStorePassword.toCharArray());
                                sc.init(kmf.getKeyManagers(), trustAllCerts, null);
                            } else {
                                sc.init(null, trustAllCerts, null);
                            }

                            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                            socket = sc.getSocketFactory().createSocket(hostname, port);
                        } else {
                            if (trustStorePath != null) {
                                System.setProperty("javax.net.ssl.trustStore", trustStorePath);
                            }
                            if (trustStorePassword != null) {
                                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
                            }
                            SocketFactory socketFactory = SSLSocketFactory.getDefault();
                            socket = socketFactory.createSocket(hostname, port);
                        }
                    } else {
                        socket = new Socket(hostname, port);
                    }
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
                    String charset = EntityUtils.getContentCharSet(entity);
                    if (charset == null) {
                        charset = HTTP.DEFAULT_CONTENT_CHARSET;
                    }
                    long contentlen = 0;
                    InputStream instream = entity.getContent();
                    int l = 0;
                    while ((l = instream.read(this.buffer)) != -1) {
                        stats.incTotalBytesRecv(l);
                        contentlen += l;
                        if (this.verbosity >= 4) {
                            String s = new String(this.buffer, 0, l, charset);
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
                ex.printStackTrace();
                stats.incFailureCount();
                if (this.verbosity >= 2) {
                    System.err.println("I/O error: " + ex.getMessage());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
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
            ex.printStackTrace();
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
