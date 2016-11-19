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

import java.io.File;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;

/**
 * Main program of the HTTP benchmark.
 *
 *
 * @since 4.0
 */
public class HttpBenchmark {

    private final Config config;

    public static void main(final String[] args) throws Exception {

        final Options options = CommandLineUtils.getOptions();
        final CommandLineParser parser = new PosixParser();
        final CommandLine cmd = parser.parse(options, args);

        if (args.length == 0 || cmd.hasOption('h') || cmd.getArgs().length != 1) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        final Config config = new Config();
        CommandLineUtils.parseCommandLine(cmd, config);

        if (config.getUrl() == null) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        final HttpBenchmark httpBenchmark = new HttpBenchmark(config);
        httpBenchmark.execute();
    }

    public HttpBenchmark(final Config config) {
        super();
        this.config = config != null ? config : new Config();
    }

    private ClassicHttpRequest createRequest(final HttpHost host) {
        final URL url = config.getUrl();
        HttpEntity entity = null;

        // Prepare requests for each thread
        if (config.getPayloadFile() != null) {
            final FileEntity fe = new FileEntity(config.getPayloadFile());
            fe.setContentType(config.getContentType());
            fe.setChunked(config.isUseChunking());
            entity = fe;
        } else if (config.getPayloadText() != null) {
            final StringEntity se = new StringEntity(config.getPayloadText(),
                    ContentType.parse(config.getContentType()));
            se.setChunked(config.isUseChunking());
            entity = se;
        }
        final ClassicHttpRequest request;
        if ("POST".equals(config.getMethod())) {
            final ClassicHttpRequest httppost = new BasicClassicHttpRequest("POST", url.getPath());
            httppost.setEntity(entity);
            request = httppost;
        } else if ("PUT".equals(config.getMethod())) {
            final ClassicHttpRequest httpput = new BasicClassicHttpRequest("PUT", url.getPath());
            httpput.setEntity(entity);
            request = httpput;
        } else {
            String path = url.getPath();
            if (url.getQuery() != null && url.getQuery().length() > 0) {
                path += "?" + url.getQuery();
            } else if (path.trim().length() == 0) {
                path = "/";
            }
            request = new BasicClassicHttpRequest(config.getMethod(), path);
        }
        request.setVersion(config.isUseHttp1_0() ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1);

        if (!config.isKeepAlive()) {
            request.addHeader(new DefaultHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE));
        }

        final String[] headers = config.getHeaders();
        if (headers != null) {
            for (final String s : headers) {
                final int pos = s.indexOf(':');
                if (pos != -1) {
                    final Header header = new DefaultHeader(s.substring(0, pos).trim(), s.substring(pos + 1));
                    request.addHeader(header);
                }
            }
        }

        if (config.isUseAcceptGZip()) {
            request.addHeader(new DefaultHeader("Accept-Encoding", "gzip"));
        }

        if (config.getSoapAction() != null && config.getSoapAction().length() > 0) {
            request.addHeader(new DefaultHeader("SOAPAction", config.getSoapAction()));
        }
        request.setScheme(host.getSchemeName());
        request.setAuthority(new URIAuthority(host));
        return request;
    }

    public String execute() throws Exception {
        final Results results = doExecute();
        ResultProcessor.printResults(results);
        return "";
    }

    public Results doExecute() throws Exception {

        final URL url = config.getUrl();
        final HttpHost host = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

        final ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
                config.getThreads(), config.getThreads(), 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {

                @Override
                public Thread newThread(final Runnable r) {
                    return new Thread(r, "ClientPool");
                }

            });
        workerPool.prestartAllCoreThreads();

        SocketFactory socketFactory = null;
        if ("https".equals(host.getSchemeName())) {
            final SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.useProtocol("SSL");
            if (config.isDisableSSLVerification()) {
                sslContextBuilder.loadTrustMaterial(null, new TrustStrategy() {

                    @Override
                    public boolean isTrusted(
                            final X509Certificate[] chain, final String authType) throws CertificateException {
                        return true;
                    }

                });
            } else if (config.getTrustStorePath() != null) {
                sslContextBuilder.loadTrustMaterial(
                        new File(config.getTrustStorePath()),
                        config.getTrustStorePassword() != null ? config.getTrustStorePassword().toCharArray() : null);
            }
            if (config.getIdentityStorePath() != null) {
                sslContextBuilder.loadKeyMaterial(
                        new File(config.getIdentityStorePath()),
                        config.getIdentityStorePassword() != null ? config.getIdentityStorePassword().toCharArray() : null,
                        config.getIdentityStorePassword() != null ? config.getIdentityStorePassword().toCharArray() : null);
            }
            final SSLContext sslContext = sslContextBuilder.build();
            socketFactory = sslContext.getSocketFactory();
        }

        final BenchmarkWorker[] workers = new BenchmarkWorker[config.getThreads()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new BenchmarkWorker(
                    host,
                    createRequest(host),
                    socketFactory,
                    config);
            workerPool.execute(workers[i]);
        }

        while (workerPool.getCompletedTaskCount() < config.getThreads()) {
            Thread.yield();
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException ignore) {
            }
        }

        workerPool.shutdown();
        return ResultProcessor.collectResults(workers, host, config.getUrl().toString());
    }

}
