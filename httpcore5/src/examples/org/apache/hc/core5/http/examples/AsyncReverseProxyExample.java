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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
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
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.LazyEntityDetails;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.nio.ExpandableBuffer;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolStats;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of asynchronous embedded  HTTP/1.1 reverse proxy with full content streaming.
 */
public class AsyncReverseProxyExample {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: <hostname[:port]> [listener port]");
            System.exit(1);
        }
        // Target host
        final HttpHost targetHost = HttpHost.create(args[0]);
        int port = 8080;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        System.out.println("Reverse proxy to " + targetHost);

        IOReactorConfig config = IOReactorConfig.custom()
            .setSoTimeout(3, TimeUnit.SECONDS)
            .build();

        final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setConnectionListener(new ConnectionListener() {

                    @Override
                    public void onConnect(final HttpConnection connection) {
                        System.out.println("[proxy->origin] connection open " + connection);
                    }

                    @Override
                    public void onDisconnect(final HttpConnection connection) {
                        System.out.println("[proxy->origin] connection closed " + connection);
                    }

                    @Override
                    public void onError(final HttpConnection connection, final Exception ex) {
                    }

                })
                .setConnPoolListener(new ConnPoolListener<HttpHost>() {

                    @Override
                    public void onLease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                    }

                    @Override
                    public void onRelease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                        StringBuilder buf = new StringBuilder();
                        buf.append(route).append(" ");
                        PoolStats totals = connPoolStats.getTotalStats();
                        buf.append(" total kept alive: ").append(totals.getAvailable()).append("; ");
                        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
                        buf.append(" of ").append(totals.getMax());
                        System.out.println(buf.toString());
                    }

                })
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, HttpRequest request) {
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, HttpResponse response) {
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        System.out.println("[proxy<-origin] connection " + connection +
                                (keepAlive ? " kept alive" : " cannot be kept alive"));
                    }

                })
                .setMaxTotal(100)
                .setDefaultMaxPerRoute(20)
                .create();

        final HttpAsyncServer server = AsyncServerBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setConnectionListener(new ConnectionListener() {

                    @Override
                    public void onConnect(final HttpConnection connection) {
                        System.out.println("[client->proxy] connection open " + connection);
                    }

                    @Override
                    public void onDisconnect(final HttpConnection connection) {
                        System.out.println("[client->proxy] connection closed " + connection);
                    }

                    @Override
                    public void onError(final HttpConnection connection, final Exception ex) {
                    }

                })
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, HttpRequest request) {
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, HttpResponse response) {
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        System.out.println("[client<-proxy] connection " + connection +
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
                System.out.println("Reverse proxy shutting down");
                server.shutdown(ShutdownType.GRACEFUL);
                requester.shutdown(ShutdownType.GRACEFUL);
            }
        });

        requester.start();
        server.start();
        server.listen(new InetSocketAddress(port));
        System.out.println("Listening on port " + port);

        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }

    private static class ProxyBuffer extends ExpandableBuffer {

        ProxyBuffer(int buffersize) {
            super(buffersize);
        }

        void put(final ByteBuffer src) {
            setInputMode();
            int requiredCapacity = buffer().position() + src.remaining();
            ensureCapacity(requiredCapacity);
            buffer().put(src);
        }

        int write(final DataStreamChannel channel) throws IOException {
            setOutputMode();
            if (buffer().hasRemaining()) {
                return channel.write(buffer());
            } else {
                return 0;
            }
        }

    }

    private static final AtomicLong COUNT = new AtomicLong(0);

    private static class ProxyExchangeState {

        final String id;

        ProxyBuffer inBuf;
        ProxyBuffer outBuf;
        HttpRequest request;
        HttpResponse response;
        boolean inputEnd;
        boolean outputEnd;
        ResponseChannel responseMessageChannel;
        CapacityChannel requestCapacityChannel;
        CapacityChannel responseCapacityChannel;
        DataStreamChannel requestDataChannel;
        DataStreamChannel responseDataChannel;

        ProxyExchangeState() {
            this.id = String.format("%08X", COUNT.getAndIncrement());
        }

    }

    private static final int INIT_BUFFER_SIZE = 4096;

    private static class IncomingExchangeHandler implements AsyncServerExchangeHandler {

        private final HttpHost targetHost;
        private final HttpAsyncRequester requester;
        private final AtomicBoolean consistent;
        private final ProxyExchangeState exchangeState;

        IncomingExchangeHandler(final HttpHost targetHost, final HttpAsyncRequester requester) {
            super();
            this.targetHost = targetHost;
            this.requester = requester;
            this.consistent = new AtomicBoolean(true);
            this.exchangeState = new ProxyExchangeState();
        }

        @Override
        public void handleRequest(
                final HttpRequest incomingRequest,
                final EntityDetails entityDetails,
                final ResponseChannel responseChannel) throws HttpException, IOException {

            synchronized (exchangeState) {
                System.out.println("[client->proxy] " + exchangeState.id + " " +
                        incomingRequest.getMethod() + " " + incomingRequest.getRequestUri());
                exchangeState.request = incomingRequest;
                exchangeState.inputEnd = entityDetails == null;
                exchangeState.responseMessageChannel = responseChannel;

                if (entityDetails != null) {
                    final Header h = incomingRequest.getFirstHeader(HttpHeaders.EXPECT);
                    if (h != null && "100-continue".equalsIgnoreCase(h.getValue())) {
                        responseChannel.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE));
                    }
                }
            }

            System.out.println("[proxy->origin] " + exchangeState.id + " request connection to " + targetHost);

            requester.connect(targetHost, TimeValue.ofSeconds(30), null, new FutureCallback<AsyncClientEndpoint>() {

                @Override
                public void completed(final AsyncClientEndpoint clientEndpoint) {
                    System.out.println("[proxy->origin] " + exchangeState.id + " connection leased");
                    clientEndpoint.execute(new OutgoingExchangeHandler(clientEndpoint, exchangeState), null);
                }

                @Override
                public void failed(final Exception cause) {
                    HttpResponse outgoingResponse = new BasicHttpResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
                    outgoingResponse.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    exchangeState.response = outgoingResponse;

                    ByteBuffer msg = StandardCharsets.US_ASCII.encode(CharBuffer.wrap(cause.getMessage()));
                    exchangeState.outBuf = new ProxyBuffer(1024);
                    exchangeState.outBuf.put(msg);
                    exchangeState.outputEnd = true;

                    System.out.println("[client<-proxy] " + exchangeState.id + " status " + outgoingResponse.getCode());

                    try {
                        EntityDetails entityDetails = new BasicEntityDetails(msg.remaining(), ContentType.TEXT_PLAIN);
                        responseChannel.sendResponse(outgoingResponse, entityDetails);
                    } catch (HttpException | IOException ignore) {
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
                int capacity = exchangeState.inBuf != null ? exchangeState.inBuf.capacity() : INIT_BUFFER_SIZE;
                if (capacity > 0) {
                    System.out.println("[client<-proxy] " + exchangeState.id + " input capacity: " + capacity);
                    capacityChannel.update(capacity);
                }
            }
        }

        @Override
        public int consume(final ByteBuffer src) throws IOException {
            synchronized (exchangeState) {
                System.out.println("[client->proxy] " + exchangeState.id + " " + src.remaining() + " bytes received");
                DataStreamChannel dataChannel = exchangeState.requestDataChannel;
                if (dataChannel != null && exchangeState.inBuf != null) {
                    if (exchangeState.inBuf.hasData()) {
                        int bytesWritten = exchangeState.inBuf.write(dataChannel);
                        System.out.println("[proxy->origin] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (!exchangeState.inBuf.hasData()) {
                        int bytesWritten = dataChannel.write(src);
                        System.out.println("[proxy->origin] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                }
                if (src.hasRemaining()) {
                    if (exchangeState.inBuf == null) {
                        exchangeState.inBuf = new ProxyBuffer(INIT_BUFFER_SIZE);
                    }
                    exchangeState.inBuf.put(src);
                }
                int capacity = exchangeState.inBuf != null ? exchangeState.inBuf.capacity() : INIT_BUFFER_SIZE;
                System.out.println("[client<-proxy] " + exchangeState.id + " input capacity: " + capacity);
                if (dataChannel != null) {
                    dataChannel.requestOutput();
                }
                return capacity;
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            synchronized (exchangeState) {
                System.out.println("[client->proxy] " + exchangeState.id + " end of input");
                exchangeState.inputEnd = true;
                DataStreamChannel dataChannel = exchangeState.requestDataChannel;
                if (dataChannel != null && (exchangeState.inBuf == null || !exchangeState.inBuf.hasData())) {
                    System.out.println("[proxy->origin] " + exchangeState.id + " end of output");
                    dataChannel.endStream();
                }
            }
        }

        @Override
        public int available() {
            synchronized (exchangeState) {
                int available = exchangeState.outBuf != null ? exchangeState.outBuf.length() : 0;
                System.out.println("[client<-proxy] " + exchangeState.id + " output available: " + available);
                return available;
            }
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            synchronized (exchangeState) {
                System.out.println("[client<-proxy] " + exchangeState.id + " produce output");
                exchangeState.responseDataChannel = channel;

                if (exchangeState.outBuf != null) {
                    if (exchangeState.outBuf.hasData()) {
                        int bytesWritten = exchangeState.outBuf.write(channel);
                        System.out.println("[client<-proxy] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (exchangeState.outputEnd && !exchangeState.outBuf.hasData()) {
                        channel.endStream();
                        System.out.println("[client<-proxy] " + exchangeState.id + " end of output");
                    }
                    if (!exchangeState.outputEnd) {
                        CapacityChannel capacityChannel = exchangeState.responseCapacityChannel;
                        if (capacityChannel != null) {
                            int capacity = exchangeState.outBuf.capacity();
                            if (capacity > 0) {
                                System.out.println("[proxy->origin] " + exchangeState.id + " input capacity: " + capacity);
                                capacityChannel.update(capacity);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void failed(final Exception cause) {
            System.out.println("[client<-proxy] " + exchangeState.id + " error: " + cause.getMessage());
            cause.printStackTrace(System.out);
            consistent.set(false);
            releaseResources();
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

    private final static Set<String> HOP_BY_HOP = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            "Keep-Alive".toLowerCase(Locale.ROOT),
            "Proxy-Authenticate".toLowerCase(Locale.ROOT),
            HttpHeaders.TE.toLowerCase(Locale.ROOT),
            HttpHeaders.TRAILER.toLowerCase(Locale.ROOT),
            HttpHeaders.UPGRADE.toLowerCase(Locale.ROOT))));

    private static class OutgoingExchangeHandler implements AsyncClientExchangeHandler {

        private final AsyncClientEndpoint clientEndpoint;
        private final ProxyExchangeState exchangeState;

        OutgoingExchangeHandler(final AsyncClientEndpoint clientEndpoint, final ProxyExchangeState exchangeState) {
            this.clientEndpoint = clientEndpoint;
            this.exchangeState = exchangeState;
        }

        @Override
        public void produceRequest(
                final RequestChannel channel) throws HttpException, IOException {
            synchronized (exchangeState) {
                HttpRequest incomingRequest = exchangeState.request;
                HttpRequest outgoingRequest = new BasicHttpRequest(incomingRequest.getMethod(), incomingRequest.getPath());
                for (Iterator<Header> it = incomingRequest.headerIterator(); it.hasNext(); ) {
                    Header header = it.next();
                    if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                        outgoingRequest.addHeader(header);
                    }
                }

                System.out.println("[proxy->origin] " + exchangeState.id + " " +
                        outgoingRequest.getMethod() + " " + outgoingRequest.getRequestUri());

                channel.sendRequest(
                        outgoingRequest,
                        !exchangeState.inputEnd ? new LazyEntityDetails(outgoingRequest) : null);
            }
        }

        @Override
        public int available() {
            synchronized (exchangeState) {
                int available = exchangeState.inBuf != null ? exchangeState.inBuf.length() : 0;
                System.out.println("[proxy->origin] " + exchangeState.id + " output available: " + available);
                return available;
            }
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            synchronized (exchangeState) {
                System.out.println("[proxy->origin] " + exchangeState.id + " produce output");
                exchangeState.requestDataChannel = channel;
                if (exchangeState.inBuf != null) {
                    if (exchangeState.inBuf.hasData()) {
                        int bytesWritten = exchangeState.inBuf.write(channel);
                        System.out.println("[proxy->origin] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (exchangeState.inputEnd && !exchangeState.inBuf.hasData()) {
                        channel.endStream();
                        System.out.println("[proxy->origin] " + exchangeState.id + " end of output");
                    }
                    if (!exchangeState.inputEnd) {
                        CapacityChannel capacityChannel = exchangeState.requestCapacityChannel;
                        if (capacityChannel != null) {
                            int capacity = exchangeState.inBuf.capacity();
                            if (capacity > 0) {
                                System.out.println("[client<-proxy] " + exchangeState.id + " input capacity: " + capacity);
                                capacityChannel.update(capacity);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void consumeInformation(final HttpResponse response) throws HttpException, IOException {
        }

        @Override
        public void consumeResponse(
                final HttpResponse incomingResponse,
                final EntityDetails entityDetails) throws HttpException, IOException {
            synchronized (exchangeState) {
                System.out.println("[proxy<-origin] " + exchangeState.id + " status " + incomingResponse.getCode());

                HttpResponse outgoingResponse = new BasicHttpResponse(incomingResponse.getCode());
                for (Iterator<Header> it = incomingResponse.headerIterator(); it.hasNext(); ) {
                    Header header = it.next();
                    if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                        outgoingResponse.addHeader(header);
                    }
                }

                exchangeState.response = outgoingResponse;
                exchangeState.outputEnd = entityDetails == null;

                ResponseChannel responseChannel = exchangeState.responseMessageChannel;
                responseChannel.sendResponse(
                        outgoingResponse,
                        !exchangeState.outputEnd ?  new LazyEntityDetails(outgoingResponse) : null);

                System.out.println("[client<-proxy] " + exchangeState.id + " status " + outgoingResponse.getCode());
            }
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            synchronized (exchangeState) {
                exchangeState.responseCapacityChannel = capacityChannel;
                int capacity = exchangeState.outBuf != null ? exchangeState.outBuf.capacity() : INIT_BUFFER_SIZE;
                if (capacity > 0) {
                    System.out.println("[proxy->origin] " + exchangeState.id + " input capacity: " + capacity);
                    capacityChannel.update(capacity);
                }
            }
        }

        @Override
        public int consume(final ByteBuffer src) throws IOException {
            synchronized (exchangeState) {
                System.out.println("[proxy<-origin] " + exchangeState.id + " " + src.remaining() + " bytes received");
                DataStreamChannel dataChannel = exchangeState.responseDataChannel;
                if (dataChannel != null && exchangeState.outBuf != null) {
                    if (exchangeState.outBuf.hasData()) {
                        int bytesWritten = exchangeState.outBuf.write(dataChannel);
                        System.out.println("[client<-proxy] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                    if (!exchangeState.outBuf.hasData()) {
                        int bytesWritten = dataChannel.write(src);
                        System.out.println("[client<-proxy] " + exchangeState.id + " " + bytesWritten + " bytes sent");
                    }
                }
                if (src.hasRemaining()) {
                    if (exchangeState.outBuf == null) {
                        exchangeState.outBuf = new ProxyBuffer(INIT_BUFFER_SIZE);
                    }
                    exchangeState.outBuf.put(src);
                }
                int capacity = exchangeState.outBuf != null ? exchangeState.outBuf.capacity() : INIT_BUFFER_SIZE;
                System.out.println("[proxy->origin] " + exchangeState.id + " input capacity: " + capacity);
                if (dataChannel != null) {
                    dataChannel.requestOutput();
                }
                return capacity;
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            synchronized (exchangeState) {
                System.out.println("[proxy<-origin] " + exchangeState.id + " end of input");
                exchangeState.outputEnd = true;
                DataStreamChannel dataChannel = exchangeState.responseDataChannel;
                if (dataChannel != null && (exchangeState.outBuf == null || !exchangeState.outBuf.hasData())) {
                    System.out.println("[client<-proxy] " + exchangeState.id + " end of output");
                    dataChannel.endStream();
                }
            }
        }

        @Override
        public void cancel() {
            releaseResources();
        }

        @Override
        public void failed(final Exception cause) {
            System.out.println("[client<-proxy] " + exchangeState.id + " error: " + cause.getMessage());
            cause.printStackTrace(System.out);
            synchronized (exchangeState) {
                if (exchangeState.response == null) {
                    int status = cause instanceof IOException ? HttpStatus.SC_SERVICE_UNAVAILABLE : HttpStatus.SC_INTERNAL_SERVER_ERROR;
                    HttpResponse outgoingResponse = new BasicHttpResponse(status);
                    outgoingResponse.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    exchangeState.response = outgoingResponse;

                    ByteBuffer msg = StandardCharsets.US_ASCII.encode(CharBuffer.wrap(cause.getMessage()));
                    exchangeState.outBuf = new ProxyBuffer(1024);
                    exchangeState.outBuf.put(msg);
                    exchangeState.outputEnd = true;

                    System.out.println("[client<-proxy] " + exchangeState.id + " status " + outgoingResponse.getCode());

                    try {
                        EntityDetails entityDetails = new BasicEntityDetails(msg.remaining(), ContentType.TEXT_PLAIN);
                        exchangeState.responseMessageChannel.sendResponse(outgoingResponse, entityDetails);
                    } catch (HttpException | IOException ignore) {
                    }
                } else {
                    exchangeState.outputEnd = true;
                }
                releaseResources();
            }
        }

        @Override
        public void releaseResources() {
            synchronized (exchangeState) {
                exchangeState.requestDataChannel = null;
                exchangeState.responseCapacityChannel = null;
            }
            clientEndpoint.releaseAndReuse();
        }

    }

}
