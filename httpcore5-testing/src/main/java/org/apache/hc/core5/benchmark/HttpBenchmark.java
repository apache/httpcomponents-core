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
package org.apache.hc.core5.benchmark;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import javax.net.ssl.SSLContext;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;

/**
 * Main program of the HTTP benchmark.
 *
 * @since 4.0
 */
public class HttpBenchmark {

    private final BenchmarkConfig config;

    public static void main(final String[] args) throws Exception {

        final Options options = CommandLineUtils.getOptions();
        final CommandLineParser parser = new PosixParser();
        final CommandLine cmd = parser.parse(options, args);

        if (args.length == 0 || cmd.hasOption('h') || cmd.getArgs().length != 1) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        final BenchmarkConfig config = CommandLineUtils.parseCommandLine(cmd);

        if (config.getUri() == null) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        final HttpBenchmark httpBenchmark = new HttpBenchmark(config);
        final Results results = httpBenchmark.execute();
        ResultFormatter.print(System.out, results);
    }

    public HttpBenchmark(final BenchmarkConfig config) {
        super();
        this.config = config != null ? config : BenchmarkConfig.custom().build();
    }

    public Results execute() throws Exception {
        final HttpProcessorBuilder builder = HttpProcessorBuilder.create()
                .addAll(
                        new RequestContent(),
                        new RequestTargetHost(),
                        new RequestConnControl(),
                        new RequestUserAgent("HttpCore-AB/1.1"));
        if (this.config.isUseExpectContinue()) {
            builder.add(new RequestExpectContinue());
        }

        final SSLContext sslContext;
        if ("https".equals(config.getUri().getScheme())) {
            final SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.setProtocol("SSL");
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
            sslContext = sslContextBuilder.build();
        } else {
            sslContext = SSLContexts.createSystemDefault();
        }

        final Stats stats = new Stats();
        try (final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setHttpProcessor(builder.build())
                .setTlsStrategy(new BasicClientTlsStrategy(sslContext))
                .setIOSessionDecorator(new Decorator<IOSession>() {

                    @Override
                    public IOSession decorate(final IOSession ioSession) {
                        return new IOSession() {

                            public String getId() {
                                return ioSession.getId();
                            }

                            public Lock lock() {
                                return ioSession.lock();
                            }

                            public void enqueue(final Command command, final Command.Priority priority) {
                                ioSession.enqueue(command, priority);
                            }

                            public boolean hasCommands() {
                                return ioSession.hasCommands();
                            }

                            public Command poll() {
                                return ioSession.poll();
                            }

                            public ByteChannel channel() {
                                return new ByteChannel() {

                                    @Override
                                    public int read(final ByteBuffer dst) throws IOException {
                                        final int bytesRead = ioSession.channel().read(dst);
                                        if (bytesRead > 0) {
                                            stats.incTotalBytesRecv(bytesRead);
                                        }
                                        return bytesRead;
                                    }

                                    @Override
                                    public int write(final ByteBuffer src) throws IOException {
                                        final int bytesWritten = ioSession.channel().write(src);
                                        if (bytesWritten > 0) {
                                            stats.incTotalBytesSent(bytesWritten);
                                        }
                                        return bytesWritten;
                                    }

                                    @Override
                                    public boolean isOpen() {
                                        return ioSession.channel().isOpen();
                                    }

                                    @Override
                                    public void close() throws IOException {
                                        ioSession.channel().close();
                                    }
                                };
                            }

                            public SocketAddress getRemoteAddress() {
                                return ioSession.getRemoteAddress();
                            }

                            public SocketAddress getLocalAddress() {
                                return ioSession.getLocalAddress();
                            }

                            public int getEventMask() {
                                return ioSession.getEventMask();
                            }

                            public void setEventMask(final int ops) {
                                ioSession.setEventMask(ops);
                            }

                            public void setEvent(final int op) {
                                ioSession.setEvent(op);
                            }

                            public void clearEvent(final int op) {
                                ioSession.clearEvent(op);
                            }

                            public void close() {
                                ioSession.close();
                            }

                            public int getStatus() {
                                return ioSession.getStatus();
                            }

                            public boolean isClosed() {
                                return ioSession.isClosed();
                            }

                            public int getSocketTimeoutMillis() {
                                return ioSession.getSocketTimeoutMillis();
                            }

                            public void setSocketTimeoutMillis(final int timeout) {
                                ioSession.setSocketTimeoutMillis(timeout);
                            }

                            public long getLastReadTimeMillis() {
                                return ioSession.getLastReadTimeMillis();
                            }

                            public long getLastWriteTime() {
                                return ioSession.getLastWriteTime();
                            }

                            public void updateReadTime() {
                                ioSession.updateReadTime();
                            }

                            public void updateWriteTime() {
                                ioSession.updateWriteTime();
                            }

                            public void close(final CloseMode closeMode) {
                                ioSession.close(closeMode);
                            }

                        };
                    }

                })
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        if (config.getVerbosity() >= 3) {
                            System.out.println(">> " + request.getMethod() + " " + request.getRequestUri());
                            final Header[] headers = request.getAllHeaders();
                            for (final Header header : headers) {
                                System.out.println(">> " + header.toString());
                            }
                            System.out.println();
                        }
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        if (config.getVerbosity() >= 3) {
                            System.out.println("<< " + response.getCode() + " " + response.getReasonPhrase());
                            final Header[] headers = response.getAllHeaders();
                            for (final Header header : headers) {
                                System.out.println("<< " + header.toString());
                            }
                            System.out.println();
                        }
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        if (keepAlive) {
                            stats.incKeepAliveCount();
                        }
                    }

                })
                .setIOReactorConfig(IOReactorConfig.custom()
                    .setSoTimeout(config.getSocketTimeout())
                    .build())
                .create()) {
            requester.setDefaultMaxPerRoute(config.getConcurrencyLevel());
            requester.setMaxTotal(config.getConcurrencyLevel() * 2);
            requester.start();
            return doExecute(requester, stats);
        }
    }

    private Results doExecute(final HttpAsyncRequester requester, final Stats stats) throws Exception {

        final URI requestUri = config.getUri();
        final HttpHost host = new HttpHost(requestUri.getHost(), requestUri.getPort(), requestUri.getScheme());

        final AtomicLong requestCount = new AtomicLong(config.getRequests());

        final HttpVersion version = config.isUseHttp1_0() ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;

        final CountDownLatch completionLatch = new CountDownLatch(config.getConcurrencyLevel());
        final BenchmarkWorker[] workers = new BenchmarkWorker[config.getConcurrencyLevel()];
        for (int i = 0; i < workers.length; i++) {
            final HttpCoreContext context = HttpCoreContext.create();
            context.setProtocolVersion(version);
            final BenchmarkWorker worker = new BenchmarkWorker(
                    requester,
                    host,
                    context,
                    requestCount,
                    completionLatch,
                    stats,
                    config);
            workers[i] = worker;
        }

        final long deadline = config.getTimeLimit() > 0 ? config.getTimeLimit() : Long.MAX_VALUE;

        final long startTime = System.currentTimeMillis();

        for (int i = 0; i < workers.length; i++) {
            workers[i].execute();
        }

        completionLatch.await(deadline, TimeUnit.SECONDS);

        if (config.getVerbosity() >= 3) {
            System.out.println("...done");
        }

        final long endTime = System.currentTimeMillis();

        for (int i = 0; i < workers.length; i++) {
            workers[i].releaseResources();
        }

        return new Results(
                stats.getServerName(),
                host.getHostName(),
                host.getPort() > 0 ? host.getPort() : host.getSchemeName().equalsIgnoreCase("https") ? 443 : 80,
                requestUri.toASCIIString(),
                stats.getContentLength(),
                config.getConcurrencyLevel(),
                endTime - startTime,
                stats.getSuccessCount(),
                stats.getFailureCount(),
                stats.getKeepAliveCount(),
                stats.getTotalBytesRecv(),
                stats.getTotalBytesSent(),
                stats.getTotalContentLength());
    }

}
