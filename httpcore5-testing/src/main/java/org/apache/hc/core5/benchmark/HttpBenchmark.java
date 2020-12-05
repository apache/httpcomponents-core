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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import javax.net.ssl.SSLContext;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.FramePrinter;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

/**
 * Main program of the HTTP benchmark.
 *
 * @since 4.0
 */
public class HttpBenchmark {

    private final BenchmarkConfig config;

    public static void main(final String[] args) throws Exception {

        final Options options = CommandLineUtils.getOptions();
        final CommandLineParser parser = new DefaultParser();
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
        System.out.println();
        ResultFormatter.print(System.out, results);
    }

    public HttpBenchmark(final BenchmarkConfig config) {
        super();
        this.config = config != null ? config : BenchmarkConfig.custom().build();
    }

    public Results execute() throws Exception {
        final HttpProcessorBuilder builder = HttpProcessorBuilder.create()
                .addAll(
                        new H2RequestContent(),
                        new H2RequestTargetHost(),
                        new H2RequestConnControl(),
                        new RequestUserAgent("HttpCore-AB/5.0"));
        if (this.config.isUseExpectContinue()) {
            builder.add(new RequestExpectContinue());
        }

        final SSLContext sslContext;
        if ("https".equals(config.getUri().getScheme())) {
            final SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.setProtocol("SSL");
            if (config.isDisableSSLVerification()) {
                sslContextBuilder.loadTrustMaterial(null, (chain, authType) -> true);
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

        final HttpVersionPolicy versionPolicy;
        if (config.isForceHttp2()) {
            versionPolicy = HttpVersionPolicy.FORCE_HTTP_2;
        } else {
            if (sslContext != null) {
                versionPolicy = HttpVersionPolicy.NEGOTIATE;
            } else {
                versionPolicy = HttpVersionPolicy.FORCE_HTTP_1;
            }
        }

        final Stats stats = new Stats();
        try (final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setHttpProcessor(builder.build())
                .setTlsStrategy(new BasicClientTlsStrategy(sslContext))
                .setVersionPolicy(versionPolicy)
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .build())
                .setIOSessionDecorator(ioSession -> new IOSession() {

                    @Override
                    public String getId() {
                        return ioSession.getId();
                    }

                    @Override
                    public Lock getLock() {
                        return ioSession.getLock();
                    }

                    @Override
                    public void enqueue(final Command command, final Command.Priority priority) {
                        ioSession.enqueue(command, priority);
                    }

                    @Override
                    public boolean hasCommands() {
                        return ioSession.hasCommands();
                    }

                    @Override
                    public Command poll() {
                        return ioSession.poll();
                    }

                    @Override
                    public ByteChannel channel() {
                        return ioSession.channel();
                    }

                    @Override
                    public SocketAddress getRemoteAddress() {
                        return ioSession.getRemoteAddress();
                    }

                    @Override
                    public SocketAddress getLocalAddress() {
                        return ioSession.getLocalAddress();
                    }

                    @Override
                    public int getEventMask() {
                        return ioSession.getEventMask();
                    }

                    @Override
                    public void setEventMask(final int ops) {
                        ioSession.setEventMask(ops);
                    }

                    @Override
                    public void setEvent(final int op) {
                        ioSession.setEvent(op);
                    }

                    @Override
                    public void clearEvent(final int op) {
                        ioSession.clearEvent(op);
                    }

                    @Override
                    public void close() {
                        ioSession.close();
                    }

                    @Override
                    public Status getStatus() {
                        return ioSession.getStatus();
                    }

                    @Override
                    public int read(final ByteBuffer dst) throws IOException {
                        final int bytesRead = ioSession.read(dst);
                        if (bytesRead > 0) {
                            stats.incTotalBytesRecv(bytesRead);
                        }
                        return bytesRead;
                    }

                    @Override
                    public int write(final ByteBuffer src) throws IOException {
                        final int bytesWritten = ioSession.write(src);
                        if (bytesWritten > 0) {
                            stats.incTotalBytesSent(bytesWritten);
                        }
                        return bytesWritten;
                    }

                    @Override
                    public boolean isOpen() {
                        return ioSession.isOpen();
                    }

                    @Override
                    public Timeout getSocketTimeout() {
                        return ioSession.getSocketTimeout();
                    }

                    @Override
                    public void setSocketTimeout(final Timeout timeout) {
                        ioSession.setSocketTimeout(timeout);
                    }

                    @Override
                    public long getLastReadTime() {
                        return ioSession.getLastReadTime();
                    }

                    @Override
                    public long getLastWriteTime() {
                        return ioSession.getLastWriteTime();
                    }

                    @Override
                    public long getLastEventTime() {
                        return ioSession.getLastEventTime();
                    }

                    @Override
                    public void updateReadTime() {
                        ioSession.updateReadTime();
                    }

                    @Override
                    public void updateWriteTime() {
                        ioSession.updateWriteTime();
                    }

                    @Override
                    public void close(final CloseMode closeMode) {
                        ioSession.close(closeMode);
                    }

                    @Override
                    public IOEventHandler getHandler() {
                        return ioSession.getHandler();
                    }

                    @Override
                    public void upgrade(final IOEventHandler handler) {
                        ioSession.upgrade(handler);
                    }

                })
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        if (config.getVerbosity() >= 3) {
                            System.out.println(">> " + request.getMethod() + " " + request.getRequestUri());
                            final Header[] headers = request.getHeaders();
                            for (final Header header : headers) {
                                System.out.println(">> " + header);
                            }
                            System.out.println();
                        }
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        if (config.getVerbosity() >= 3) {
                            System.out.println("<< " + response.getCode() + " " + response.getReasonPhrase());
                            final Header[] headers = response.getHeaders();
                            for (final Header header : headers) {
                                System.out.println("<< " + header);
                            }
                            System.out.println();
                        }
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                    }

                })
                .setStreamListener(new H2StreamListener() {

                    private final FramePrinter framePrinter = new FramePrinter();

                    @Override
                    public void onHeaderInput(
                            final HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                        if (config.getVerbosity() >= 3) {
                            for (final Header header : headers) {
                                System.out.println("<< " + header);
                            }
                            System.out.println();
                        }
                    }

                    @Override
                    public void onHeaderOutput(
                            final HttpConnection connection,
                            final int streamId,
                            final List<? extends Header> headers) {
                        if (config.getVerbosity() >= 3) {
                            for (final Header header : headers) {
                                System.out.println(">> " + header);
                            }
                            System.out.println();
                        }
                    }

                    @Override
                    public void onFrameInput(
                            final HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {
                        if (config.getVerbosity() >= 4) {
                            System.out.print("<< ");
                            try {
                                framePrinter.printFrameInfo(frame, System.out);
                                System.out.println();
                                if (!frame.isType(FrameType.DATA)) {
                                    framePrinter.printPayload(frame, System.out);
                                    System.out.println();
                                }
                            } catch (final IOException ignore) {
                            }
                        }
                    }

                    @Override
                    public void onFrameOutput(
                            final HttpConnection connection,
                            final int streamId,
                            final RawFrame frame) {
                        if (config.getVerbosity() >= 4) {
                            System.out.print(">> ");
                            try {
                                framePrinter.printFrameInfo(frame, System.out);
                                System.out.println();
                                if (!frame.isType(FrameType.DATA)) {
                                    framePrinter.printPayload(frame, System.out);
                                    System.out.println();
                                }
                            } catch (final IOException ignore) {
                            }
                        }
                    }

                    @Override
                    public void onInputFlowControl(
                            final HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                        if (config.getVerbosity() >= 5) {
                            System.out.println("<< stream " + streamId + ": " + actualSize + " " + delta);
                        }
                    }

                    @Override
                    public void onOutputFlowControl(
                            final HttpConnection connection,
                            final int streamId,
                            final int delta,
                            final int actualSize) {
                        if (config.getVerbosity() >= 5) {
                            System.out.println(">> stream " + streamId + ": " + actualSize + " " + delta);
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
        final HttpHost host = new HttpHost(requestUri.getScheme(), requestUri.getHost(), requestUri.getPort());

        final AtomicLong requestCount = new AtomicLong(config.getRequests());

        final HttpVersion version = HttpVersion.HTTP_1_1;

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

        final long deadline = config.getTimeLimit() != null ? config.getTimeLimit().toMilliseconds() : Long.MAX_VALUE;

        final long startTime = System.currentTimeMillis();

        for (int i = 0; i < workers.length; i++) {
            workers[i].execute();
        }

        completionLatch.await(deadline, TimeUnit.MILLISECONDS);

        if (config.getVerbosity() >= 3) {
            System.out.println("...done");
        }

        final long endTime = System.currentTimeMillis();

        for (int i = 0; i < workers.length; i++) {
            workers[i].releaseResources();
        }

        return new Results(
                stats.getServerName(),
                stats.getVersion(),
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
