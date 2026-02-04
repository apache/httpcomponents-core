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
package org.apache.hc.core5.websocket.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.server.WebSocketServer;
import org.apache.hc.core5.websocket.server.WebSocketServerBootstrap;

/**
 * Simple WebSocket echo server built on httpcore5-websocket.
 * <p>
 * Usage:
 * java -cp ... org.apache.hc.core5.websocket.example.WebSocketEchoServer [port]
 */
public final class WebSocketEchoServer {

    private WebSocketEchoServer() {
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        final CountDownLatch shutdown = new CountDownLatch(1);

        final WebSocketServer server = WebSocketServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setCanonicalHostName("localhost")
                .register("/echo", () -> new WebSocketHandler() {
                    @Override
                    public void onOpen(final WebSocketSession session) {
                        System.out.println("WebSocket open: " + session.getRemoteAddress());
                    }

                    @Override
                    public void onText(final WebSocketSession session, final String text) {
                        try {
                            session.sendText(text);
                        } catch (final IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    @Override
                    public void onBinary(final WebSocketSession session, final ByteBuffer data) {
                        try {
                            session.sendBinary(data);
                        } catch (final IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    @Override
                    public void onPing(final WebSocketSession session, final ByteBuffer data) {
                        try {
                            session.sendPong(data);
                        } catch (final IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    @Override
                    public void onClose(final WebSocketSession session, final int statusCode, final String reason) {
                        System.out.println("WebSocket close: " + statusCode + " " + reason);
                    }

                    @Override
                    public void onError(final WebSocketSession session, final Exception cause) {
                        System.err.println("WebSocket error: " + cause.getMessage());
                        cause.printStackTrace(System.err);
                    }

                    @Override
                    public String selectSubprotocol(final java.util.List<String> protocols) {
                        return protocols.isEmpty() ? null : protocols.get(0);
                    }
                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.initiateShutdown();
            server.stop();
            shutdown.countDown();
        }));

        server.start();
        System.out.println("WebSocket echo server listening on ws://localhost:" + port + "/echo");
        shutdown.await();
    }
}
