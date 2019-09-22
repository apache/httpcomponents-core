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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.nio.BufferedData;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpDateGenerator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolStats;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of asynchronous embedded  HTTP/1.1 reverse proxy with full content streaming.
 */
public class AsyncReverseProxyExample {

    private static boolean quiet;

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: <hostname[:port]> [listener port] [--quiet]");
            System.exit(1);
        }
        // Target host
        final HttpHost targetHost = HttpHost.create(args[0]);
        int port = 8080;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        for (final String s : args) {
            if ("--quiet".equalsIgnoreCase(s)) {
                quiet = true;
                break;
            }
        }

        println("Reverse proxy to " + targetHost);

        final IOReactorConfig config = IOReactorConfig.custom()
            .setSoTimeout(1, TimeUnit.MINUTES)
            .build();

        final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setConnPoolListener(new ConnPoolListener<HttpHost>() {

                    @Override
                    public void onLease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                        final StringBuilder buf = new StringBuilder();
                        buf.append("[proxy->origin] connection leased ").append(route);
                        println(buf.toString());
                    }

                    @Override
                    public void onRelease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                        final StringBuilder buf = new StringBuilder();
                        buf.append("[proxy->origin] connection released ").append(route);
                        final PoolStats totals = connPoolStats.getTotalStats();
                        buf.append("; total kept alive: ").append(totals.getAvailable()).append("; ");
                        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
                        buf.append(" of ").append(totals.getMax());
                        println(buf.toString());
                    }

                })
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        // empty
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        // empty
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        println("[proxy<-origin] connection " +
                                connection.getLocalAddress() + "->" + connection.getRemoteAddress() +
                                (keepAlive ? " kept alive" : " cannot be kept alive"));
                    }

                })
                .setMaxTotal(100)
                .setDefaultMaxPerRoute(20)
                .create();

        final HttpAsyncServer server = AsyncServerBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        // empty
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        // empty
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        println("[client<-proxy] connection " +
                                connection.getLocalAddress() + "->" + connection.getRemoteAddress() +
                                (keepAlive ? " kept alive" : " cannot be kept alive"));
                    }

                })
                .register("*", new Supplier<AsyncServerExchangeHandler>() {

                    @Override
                    public AsyncServerExchangeHandler get() {
                        return new IncomingExchangeHandler(targetHost, requester);
                    }

                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                println("Reverse proxy shutting down");
                server.close(CloseMode.GRACEFUL);
                requester.close(CloseMode.GRACEFUL);
            }
        });

        requester.start();
        server.start();
        server.listen(new InetSocketAddress(port));
        println("Listening on port " + port);

        server.awaitShutdown(TimeValue.MAX_VALUE);
    }

    private static class ProxyBuffer extends BufferedData {

        ProxyBuffer(final int bufferSize) {
            super(bufferSize);
        }

        int write(final DataStreamChannel channel) throws IOException {
            setOutputMode();
            if (buffer().hasRemaining()) {
                return channel.write(buffer());
            }
            return 0;
        }

    }

    private static final AtomicLong COUNT = new AtomicLong(0);

    private static class ProxyExchangeState {

        final String id;

        HttpRequest request;
        EntityDetails requestEntityDetails;
        DataStreamChannel requestDataChannel;
        CapacityChannel requestCapacityChannel;
        ProxyBuffer inBuf;
        boolean inputEnd;

        HttpResponse response;
        EntityDetails responseEntityDetails;
        ResponseChannel responseMessageChannel;
        DataStreamChannel responseDataChannel;
        CapacityChannel responseCapacityChannel;
        ProxyBuffer outBuf;
        boolean outputEnd;

        AsyncClientEndpoint clientEndpoint;

        ProxyExchangeState() {
            this.id = String.format("%08X", COUNT.getAndIncrement());
        }

    }

    private static final int INIT_BUFFER_SIZE = 4096;

    private static class IncomingExchangeHandler implements AsyncServerExchangeHandler {

        private final HttpHost targetHost;
        private final HttpAsyncRequester requester;
        private final ProxyExchangeState exchangeState;

        IncomingExchangeHandler(final HttpHost targetHost, final HttpAsyncRequester requester) {
            super();
            this.targetHost = targetHost;
            this.requester = requester;
            this.exchangeState = new ProxyExchangeState();
        }

        @Override
        public void handleRequest(
                final HttpRequest incomingRequest,
                final EntityDetails entityDetails,
                final ResponseChannel responseChannel,
                final HttpContext httpContext) throws HttpException, IOException {

            synchronized (exchangeState) {
                println("[client->proxy] " + exchangeState.id + " " +
                        incomingRequest.getMethod() + " " + incomingRequest.getRequestUri());
                exchangeState.request = incomingRequest;
                exchangeState.requestEntityDetails = entityDetails;
                exchangeState.inputEnd = entityDetails == null;
                exchangeState.responseMessageChannel = responseChannel;

                if (entityDetails != null) {
                    final Header h = incomingRequest.getFirstHeader(HttpHeaders.EXPECT);
                    if (h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue())) {
                        responseChannel.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE), httpContext);
                    }
                }
            }

            println("[proxy->origin] " + exchangeState.id + " request connection to " + targetHost);

            requester.connect(targetHost, Timeout.ofSeconds(30), null, new FutureCallback<AsyncClientEndpoint>() {

                @Override
                public void completed(final AsyncClientEndpoint clientEndpoint) {
                    println("[proxy->origin] " + exchangeState.id + " connection leased");
                    synchronized (exchangeState) {
                        exchangeState.clientEndpoint = clientEndpoint;
                    }
                    clientEndpoint.execute(
                            new OutgoingExchangeHandler(targetHost, clientEndpoint, exchangeState),
                            HttpCoreContext.create());
                }

                @Override
                public void failed(final Exception cause) {
                    final HttpResponse outgoingResponse = new BasicHttpResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
                    outgoingResponse.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    final ByteBuffer msg = StandardCharsets.US_ASCII.encode(CharBuffer.wrap(cause.getMessage()));
                    final EntityDetails exEntityDetails = new BasicEntityDetails(msg.remaining(),
                                    ContentType.TEXT_PLAIN);
                    synchronized (exchangeState) {
                        exchangeState.response = outgoingResponse;
                        exchangeState.responseEntityDetails = exEntityDetails;
                        exchangeState.outBuf = new ProxyBuffer(1024);
                        exchangeState.outBuf.put(msg);
                        exchangeState.outputEnd = true;
                    }
                    println("[client<-proxy] " + exchangeState.id + " status " + outgoingResponse.getCode());

                    try {
                        responseChannel.sendResponse(outgoingResponse, exEntityDetails, httpContext);
                    } catch (final HttpException | IOException ignore) {
                        // ignore
                    }
                }

                @Override
                public void cancelled() {
                    failed(new InterruptedIOException());
                }

            });

        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            synchronized (exchangeState) {
                exchangeState.requestCapacityChannel = capacityChannel;
                final int capacity = exchangeState.inBuf != null ? exchangeState.inBuf.capacity() : INIT_BUFFER_SIZE;
                if (capacity > 0) {
                    println("[client<-proxy] " + exchangeState.id + " input capacity: " + capacity);
                    capacityChannel.update(capacity);
                }
            }
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            synchronized (exchangeState) {
                println("[client->proxy] " + exchangeState.id + " " + src.remaining() + " bytes received");
                final DataStreamChannel dataChannel = exchangeState.requestDataChannel;
                if (dataChannel != null && exchangeState.inBuf != null) {
                    if (exchangeState.inBuf.hasData()) {
                        final int bytesWritten = exchangeState.inBuf.write(dataChannel);
                        println("[proxy->origin] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (!exchangeState.inBuf.hasData()) {
                        final int bytesWritten = dataChannel.write(src);
                        println("[proxy->origin] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                }
                if (src.hasRemaining()) {
                    if (exchangeState.inBuf == null) {
                        exchangeState.inBuf = new ProxyBuffer(INIT_BUFFER_SIZE);
                    }
                    exchangeState.inBuf.put(src);
                }
                final int capacity = exchangeState.inBuf != null ? exchangeState.inBuf.capacity() : INIT_BUFFER_SIZE;
                println("[client<-proxy] " + exchangeState.id + " input capacity: " + capacity);
                if (dataChannel != null) {
                    dataChannel.requestOutput();
                }
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            synchronized (exchangeState) {
                println("[client->proxy] " + exchangeState.id + " end of input");
                exchangeState.inputEnd = true;
                final DataStreamChannel dataChannel = exchangeState.requestDataChannel;
                if (dataChannel != null && (exchangeState.inBuf == null || !exchangeState.inBuf.hasData())) {
                    println("[proxy->origin] " + exchangeState.id + " end of output");
                    dataChannel.endStream();
                }
            }
        }

        @Override
        public int available() {
            synchronized (exchangeState) {
                final int available = exchangeState.outBuf != null ? exchangeState.outBuf.length() : 0;
                println("[client<-proxy] " + exchangeState.id + " output available: " + available);
                return available;
            }
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            synchronized (exchangeState) {
                println("[client<-proxy] " + exchangeState.id + " produce output");
                exchangeState.responseDataChannel = channel;

                if (exchangeState.outBuf != null) {
                    if (exchangeState.outBuf.hasData()) {
                        final int bytesWritten = exchangeState.outBuf.write(channel);
                        println("[client<-proxy] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (exchangeState.outputEnd && !exchangeState.outBuf.hasData()) {
                        channel.endStream();
                        println("[client<-proxy] " + exchangeState.id + " end of output");
                    }
                    if (!exchangeState.outputEnd) {
                        final CapacityChannel capacityChannel = exchangeState.responseCapacityChannel;
                        if (capacityChannel != null) {
                            final int capacity = exchangeState.outBuf.capacity();
                            if (capacity > 0) {
                                println("[proxy->origin] " + exchangeState.id + " input capacity: " + capacity);
                                capacityChannel.update(capacity);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void failed(final Exception cause) {
            println("[client<-proxy] " + exchangeState.id + " " + cause.getMessage());
            if (!(cause instanceof ConnectionClosedException)) {
                cause.printStackTrace(System.out);
            }
            synchronized (exchangeState) {
                if (exchangeState.clientEndpoint != null) {
                    exchangeState.clientEndpoint.releaseAndDiscard();
                }
            }
        }

        @Override
        public void releaseResources() {
            synchronized (exchangeState) {
                exchangeState.responseMessageChannel = null;
                exchangeState.responseDataChannel = null;
                exchangeState.requestCapacityChannel = null;
            }
        }

    }

    private static class OutgoingExchangeHandler implements AsyncClientExchangeHandler {

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

        private final HttpHost targetHost;
        private final AsyncClientEndpoint clientEndpoint;
        private final ProxyExchangeState exchangeState;

        OutgoingExchangeHandler(
                final HttpHost targetHost,
                final AsyncClientEndpoint clientEndpoint,
                final ProxyExchangeState exchangeState) {
            this.targetHost = targetHost;
            this.clientEndpoint = clientEndpoint;
            this.exchangeState = exchangeState;
        }

        @Override
        public void produceRequest(
                final RequestChannel channel, final HttpContext httpContext) throws HttpException, IOException {
            synchronized (exchangeState) {
                final HttpRequest incomingRequest = exchangeState.request;
                final EntityDetails entityDetails = exchangeState.requestEntityDetails;
                final HttpRequest outgoingRequest = new BasicHttpRequest(
                        incomingRequest.getMethod(),
                        targetHost,
                        incomingRequest.getPath());
                for (final Iterator<Header> it = incomingRequest.headerIterator(); it.hasNext(); ) {
                    final Header header = it.next();
                    if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                        outgoingRequest.addHeader(header);
                    }
                }

                println("[proxy->origin] " + exchangeState.id + " " +
                        outgoingRequest.getMethod() + " " + outgoingRequest.getRequestUri());

                channel.sendRequest(outgoingRequest, entityDetails, httpContext);
            }
        }

        @Override
        public int available() {
            synchronized (exchangeState) {
                final int available = exchangeState.inBuf != null ? exchangeState.inBuf.length() : 0;
                println("[proxy->origin] " + exchangeState.id + " output available: " + available);
                return available;
            }
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            synchronized (exchangeState) {
                println("[proxy->origin] " + exchangeState.id + " produce output");
                exchangeState.requestDataChannel = channel;
                if (exchangeState.inBuf != null) {
                    if (exchangeState.inBuf.hasData()) {
                        final int bytesWritten = exchangeState.inBuf.write(channel);
                        println("[proxy->origin] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (exchangeState.inputEnd && !exchangeState.inBuf.hasData()) {
                        channel.endStream();
                        println("[proxy->origin] " + exchangeState.id + " end of output");
                    }
                    if (!exchangeState.inputEnd) {
                        final CapacityChannel capacityChannel = exchangeState.requestCapacityChannel;
                        if (capacityChannel != null) {
                            final int capacity = exchangeState.inBuf.capacity();
                            if (capacity > 0) {
                                println("[client<-proxy] " + exchangeState.id + " input capacity: " + capacity);
                                capacityChannel.update(capacity);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext httpContext) throws HttpException, IOException {
            // ignore
        }

        @Override
        public void consumeResponse(
                final HttpResponse incomingResponse,
                final EntityDetails entityDetails, final HttpContext httpContext) throws HttpException, IOException {
            synchronized (exchangeState) {
                println("[proxy<-origin] " + exchangeState.id + " status " + incomingResponse.getCode());
                if (entityDetails == null) {
                    println("[proxy<-origin] " + exchangeState.id + " end of input");
                }

                final HttpResponse outgoingResponse = new BasicHttpResponse(incomingResponse.getCode());
                for (final Iterator<Header> it = incomingResponse.headerIterator(); it.hasNext(); ) {
                    final Header header = it.next();
                    if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                        outgoingResponse.addHeader(header);
                    }
                }

                exchangeState.response = outgoingResponse;
                exchangeState.responseEntityDetails = entityDetails;
                exchangeState.outputEnd = entityDetails == null;

                final ResponseChannel responseChannel = exchangeState.responseMessageChannel;
                if (responseChannel != null) {
                    // responseChannel can be null under load.
                    responseChannel.sendResponse(outgoingResponse, entityDetails, httpContext);
                }

                println("[client<-proxy] " + exchangeState.id + " status " + outgoingResponse.getCode());
                if (entityDetails == null) {
                    println("[client<-proxy] " + exchangeState.id + " end of output");
                    clientEndpoint.releaseAndReuse();
                }
            }
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            synchronized (exchangeState) {
                exchangeState.responseCapacityChannel = capacityChannel;
                final int capacity = exchangeState.outBuf != null ? exchangeState.outBuf.capacity() : INIT_BUFFER_SIZE;
                if (capacity > 0) {
                    println("[proxy->origin] " + exchangeState.id + " input capacity: " + capacity);
                    capacityChannel.update(capacity);
                }
            }
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            synchronized (exchangeState) {
                println("[proxy<-origin] " + exchangeState.id + " " + src.remaining() + " bytes received");
                final DataStreamChannel dataChannel = exchangeState.responseDataChannel;
                if (dataChannel != null && exchangeState.outBuf != null) {
                    if (exchangeState.outBuf.hasData()) {
                        final int bytesWritten = exchangeState.outBuf.write(dataChannel);
                        println("[client<-proxy] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (!exchangeState.outBuf.hasData()) {
                        final int bytesWritten = dataChannel.write(src);
                        println("[client<-proxy] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                }
                if (src.hasRemaining()) {
                    if (exchangeState.outBuf == null) {
                        exchangeState.outBuf = new ProxyBuffer(INIT_BUFFER_SIZE);
                    }
                    exchangeState.outBuf.put(src);
                }
                final int capacity = exchangeState.outBuf != null ? exchangeState.outBuf.capacity() : INIT_BUFFER_SIZE;
                println("[proxy->origin] " + exchangeState.id + " input capacity: " + capacity);
                if (dataChannel != null) {
                    dataChannel.requestOutput();
                }
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            synchronized (exchangeState) {
                println("[proxy<-origin] " + exchangeState.id + " end of input");
                exchangeState.outputEnd = true;
                final DataStreamChannel dataChannel = exchangeState.responseDataChannel;
                if (dataChannel != null && (exchangeState.outBuf == null || !exchangeState.outBuf.hasData())) {
                    println("[client<-proxy] " + exchangeState.id + " end of output");
                    dataChannel.endStream();
                    clientEndpoint.releaseAndReuse();
                }
            }
        }

        @Override
        public void cancel() {
            clientEndpoint.releaseAndDiscard();
        }

        @Override
        public void failed(final Exception cause) {
            println("[client<-proxy] " + exchangeState.id + " " + cause.getMessage());
            if (!(cause instanceof ConnectionClosedException)) {
                cause.printStackTrace(System.out);
            }
            synchronized (exchangeState) {
                if (exchangeState.response == null) {
                    final int status = cause instanceof IOException ? HttpStatus.SC_SERVICE_UNAVAILABLE : HttpStatus.SC_INTERNAL_SERVER_ERROR;
                    final HttpResponse outgoingResponse = new BasicHttpResponse(status);
                    outgoingResponse.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    exchangeState.response = outgoingResponse;

                    final ByteBuffer msg = StandardCharsets.US_ASCII.encode(CharBuffer.wrap(cause.getMessage()));
                    final int contentLen = msg.remaining();
                    exchangeState.outBuf = new ProxyBuffer(1024);
                    exchangeState.outBuf.put(msg);
                    exchangeState.outputEnd = true;

                    println("[client<-proxy] " + exchangeState.id + " status " + outgoingResponse.getCode());

                    try {
                        final EntityDetails entityDetails = new BasicEntityDetails(contentLen, ContentType.TEXT_PLAIN);
                        exchangeState.responseMessageChannel.sendResponse(outgoingResponse, entityDetails, null);
                    } catch (final HttpException | IOException ignore) {
                        // ignore
                    }
                } else {
                    exchangeState.outputEnd = true;
                }
                clientEndpoint.releaseAndDiscard();
            }
        }

        @Override
        public void releaseResources() {
            synchronized (exchangeState) {
                exchangeState.requestDataChannel = null;
                exchangeState.responseCapacityChannel = null;
                clientEndpoint.releaseAndDiscard();
            }
        }

    }

    static final void println(final String msg) {
        if (!quiet) {
            System.out.println(HttpDateGenerator.INSTANCE.getCurrentDate() + " | " + msg);
        }
    }
}
