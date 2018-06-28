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
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.Http2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of full-duplex, streaming HTTP message exchanges with an asynchronous embedded HTTP/2 server.
 */
public class Http2FullDuplexServerExample {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        IOReactorConfig config = IOReactorConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        H2Config h2Config = H2Config.custom()
                .setPushEnabled(true)
                .setMaxConcurrentStreams(100)
                .build();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setH2Config(h2Config)
                .setStreamListener(new Http2StreamListener() {

                    @Override
                    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection + " (" + streamId + ") << " + headers.get(i));
                        }
                    }

                    @Override
                    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection + " (" + streamId + ") >> " + headers.get(i));
                        }
                    }

                    @Override
                    public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                    @Override
                    public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                })
                .register("/echo", new Supplier<AsyncServerExchangeHandler>() {

                    @Override
                    public AsyncServerExchangeHandler get() {
                        return new AsyncServerExchangeHandler() {

                            ByteBuffer buffer = ByteBuffer.allocate(2048);
                            CapacityChannel inputCapacityChannel;
                            DataStreamChannel outputDataChannel;
                            boolean endStream;

                            private void ensureCapacity(final int chunk) {
                                if (buffer.remaining() < chunk) {
                                    final ByteBuffer oldBuffer = buffer;
                                    oldBuffer.flip();
                                    buffer = ByteBuffer.allocate(oldBuffer.remaining() + (chunk > 2048 ? chunk : 2048));
                                    buffer.put(oldBuffer);
                                }
                            }

                            @Override
                            public void handleRequest(
                                    final HttpRequest request,
                                    final EntityDetails entityDetails,
                                    final ResponseChannel responseChannel,
                                    final HttpContext context) throws HttpException, IOException {
                                final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
                                responseChannel.sendResponse(response, entityDetails);
                            }

                            @Override
                            public int consume(final ByteBuffer src) throws IOException {
                                if (buffer.position() == 0) {
                                    if (outputDataChannel != null) {
                                        outputDataChannel.write(src);
                                    }
                                }
                                if (src.hasRemaining()) {
                                    ensureCapacity(src.remaining());
                                    buffer.put(src);
                                    if (outputDataChannel != null) {
                                        outputDataChannel.requestOutput();
                                    }
                                }
                                return buffer.remaining();
                            }

                            @Override
                            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                                if (buffer.hasRemaining()) {
                                    capacityChannel.update(buffer.remaining());
                                    inputCapacityChannel = null;
                                } else {
                                    inputCapacityChannel = capacityChannel;
                                }
                            }

                            @Override
                            public void streamEnd(final List<? extends Header> trailers) throws IOException {
                                endStream = true;
                                if (buffer.position() == 0) {
                                    if (outputDataChannel != null) {
                                        outputDataChannel.endStream();
                                    }
                                } else {
                                    if (outputDataChannel != null) {
                                        outputDataChannel.requestOutput();
                                    }
                                }
                            }

                            @Override
                            public int available() {
                                return buffer.position();
                            }

                            @Override
                            public void produce(final DataStreamChannel channel) throws IOException {
                                outputDataChannel = channel;
                                buffer.flip();
                                if (buffer.hasRemaining()) {
                                    channel.write(buffer);
                                }
                                buffer.compact();
                                if (buffer.position() == 0 && endStream) {
                                    channel.endStream();
                                }
                                final CapacityChannel capacityChannel = inputCapacityChannel;
                                if (capacityChannel != null && buffer.hasRemaining()) {
                                    capacityChannel.update(buffer.remaining());
                                }
                            }

                            @Override
                            public void failed(final Exception cause) {
                                if (!(cause instanceof SocketException)) {
                                    cause.printStackTrace(System.out);
                                }
                            }

                            @Override
                            public void releaseResources() {
                            }

                        };
                    }

                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("HTTP server shutting down");
                server.shutdown(ShutdownType.GRACEFUL);
            }
        });

        server.start();
        Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port));
        ListenerEndpoint listenerEndpoint = future.get();
        System.out.print("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }

}
