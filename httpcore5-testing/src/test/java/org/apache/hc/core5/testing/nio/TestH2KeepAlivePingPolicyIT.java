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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2PingPolicy;
import org.apache.hc.core5.http2.nio.AsyncPingHandler;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.testing.extension.nio.H2TestResources;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestH2KeepAlivePingPolicyIT {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    @RegisterExtension
    private final H2TestResources resources = new H2TestResources(URIScheme.HTTP, TIMEOUT);

    @Test
    void keepAlivePing_keepsConnectionOpenPastIdleTimeout() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        server.register("/hello", () -> new MessageExchangeHandler<Void>(new DiscardingEntityConsumer<Void>()) {
            @Override
            protected void handle(
                    final Message<HttpRequest, Void> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(
                        AsyncResponseBuilder.create(HttpStatus.SC_OK)
                                .setEntity("OK", ContentType.TEXT_PLAIN)
                                .build(),
                        context);
            }
        });

        final Timeout idleTime = Timeout.ofMilliseconds(200);
        final Timeout ackTimeout = Timeout.ofSeconds(2);

        final H2PingPolicy pingPolicy = H2PingPolicy.custom()
                .setIdleTime(idleTime)
                .setAckTimeout(ackTimeout)
                .build();

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setPingPolicy(pingPolicy)
                .build();

        server.configure(h2Config);
        final InetSocketAddress serverEndpoint = server.start();

        client.configure(h2Config);
        client.start();

        final IOSession ioSession = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()),
                TIMEOUT,
                null).get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        // Make the inactivity timeout aggressive; keep-alive must prevent it from killing the session.
        ioSession.setSocketTimeout(idleTime);

        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession)) {
            final HttpHost target = new HttpHost(URIScheme.HTTP.id, "localhost", serverEndpoint.getPort());

            final Message<HttpResponse, String> r1 = executeHello(streamEndpoint, target);
            Assertions.assertEquals(200, r1.getHead().getCode());
            Assertions.assertEquals("OK", r1.getBody());

            parkAtLeast(idleTime.toMilliseconds() * 6L);

            Assertions.assertTrue(ioSession.isOpen(), "Expected session to stay open with keep-alive enabled");

            final Message<HttpResponse, String> r2 = executeHello(streamEndpoint, target);
            Assertions.assertEquals(200, r2.getHead().getCode());
            Assertions.assertEquals("OK", r2.getBody());
        }
    }

    @Test
    void keepAlivePing_disabled_connectionClosesOnIdleTimeout() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        server.register("/hello", () -> new MessageExchangeHandler<Void>(new DiscardingEntityConsumer<Void>()) {
            @Override
            protected void handle(
                    final Message<HttpRequest, Void> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(
                        AsyncResponseBuilder.create(HttpStatus.SC_OK)
                                .setEntity("OK", ContentType.TEXT_PLAIN)
                                .build(),
                        context);
            }
        });

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                // pingPolicy is intentionally not set (disabled)
                .build();

        server.configure(h2Config);
        final InetSocketAddress serverEndpoint = server.start();

        client.configure(h2Config);
        client.start();

        final IOSession ioSession = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()),
                TIMEOUT,
                null).get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        final Timeout idleTimeout = Timeout.ofMilliseconds(200);
        ioSession.setSocketTimeout(idleTimeout);

        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession)) {
            final HttpHost target = new HttpHost(URIScheme.HTTP.id, "localhost", serverEndpoint.getPort());

            final Message<HttpResponse, String> r1 = executeHello(streamEndpoint, target);
            Assertions.assertEquals(200, r1.getHead().getCode());
            Assertions.assertEquals("OK", r1.getBody());

            awaitTrue(() -> !ioSession.isOpen(), Timeout.ofSeconds(5), "Expected session to close without keep-alive");

            Assertions.assertFalse(ioSession.isOpen(), "Expected session to close without keep-alive");

            final Future<Message<HttpResponse, String>> f = executeHelloAsync(streamEndpoint, target);
            Assertions.assertThrows(ExecutionException.class, () -> f.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        }
    }

    @Test
    void keepAlivePing_enabled_doesNotStealAckFromExplicitPingCommand() throws Exception {
        final H2TestServer server = resources.server();
        final H2TestClient client = resources.client();

        server.register("/hello", () -> new MessageExchangeHandler<Void>(new DiscardingEntityConsumer<Void>()) {
            @Override
            protected void handle(
                    final Message<HttpRequest, Void> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(
                        AsyncResponseBuilder.create(HttpStatus.SC_OK)
                                .setEntity("OK", ContentType.TEXT_PLAIN)
                                .build(),
                        context);
            }
        });

        final Timeout idleTime = Timeout.ofMilliseconds(100);
        final Timeout ackTimeout = Timeout.ofSeconds(2);

        final H2PingPolicy pingPolicy = H2PingPolicy.custom()
                .setIdleTime(idleTime)
                .setAckTimeout(ackTimeout)
                .build();

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setPingPolicy(pingPolicy)
                .build();

        server.configure(h2Config);
        final InetSocketAddress serverEndpoint = server.start();

        client.configure(h2Config);
        client.start();

        final IOSession ioSession = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()),
                TIMEOUT,
                null).get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        ioSession.setSocketTimeout(idleTime);

        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession)) {
            final HttpHost target = new HttpHost(URIScheme.HTTP.id, "localhost", serverEndpoint.getPort());

            // Warm-up to complete the HTTP/2 session & SETTINGS handshake.
            final Message<HttpResponse, String> r1 = executeHello(streamEndpoint, target);
            Assertions.assertEquals(200, r1.getHead().getCode());
            Assertions.assertEquals("OK", r1.getBody());

            // Give the keep-alive logic a chance to become active (no hard assumptions about socket timeout changes).
            parkAtLeast(idleTime.toMilliseconds() * 3L);

            final byte[] expected = new byte[]{0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55};
            final CompletableFuture<Void> acked = new CompletableFuture<>();

            final AsyncPingHandler handler = new AsyncPingHandler() {

                @Override
                public ByteBuffer getData() {
                    return ByteBuffer.wrap(expected).asReadOnlyBuffer();
                }

                @Override
                public void consumeResponse(final ByteBuffer feedback) throws IOException, HttpException {
                    if (feedback == null || feedback.remaining() != expected.length) {
                        acked.completeExceptionally(new AssertionError("Unexpected ping ACK payload"));
                        return;
                    }
                    final ByteBuffer dup = feedback.slice();
                    final byte[] actual = new byte[expected.length];
                    dup.get(actual);
                    for (int i = 0; i < expected.length; i++) {
                        if (actual[i] != expected[i]) {
                            acked.completeExceptionally(new AssertionError("Ping ACK payload mismatch"));
                            return;
                        }
                    }
                    acked.complete(null);
                }

                @Override
                public void failed(final Exception cause) {
                    acked.completeExceptionally(cause);
                }

                @Override
                public void cancel() {
                    acked.cancel(false);
                }
            };

            ioSession.enqueue(new PingCommand(handler), Command.Priority.NORMAL);
            ioSession.setEvent(SelectionKey.OP_WRITE);

            try {
                acked.get(5, TimeUnit.SECONDS);
            } catch (final TimeoutException ex) {
                Assertions.fail("Timed out waiting for explicit PING ACK");
            }

            // Still usable.
            Assertions.assertTrue(ioSession.isOpen(), "Expected session to stay open");
            final Message<HttpResponse, String> r2 = executeHello(streamEndpoint, target);
            Assertions.assertEquals(200, r2.getHead().getCode());
            Assertions.assertEquals("OK", r2.getBody());
        }
    }

    @Test
    void keepAlivePingPolicy_rejectsDisabledAckTimeoutWhenIdleEnabled() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> H2PingPolicy.custom()
                .setIdleTime(Timeout.ofSeconds(1))
                .setAckTimeout(Timeout.DISABLED)
                .build());
    }

    private static Message<HttpResponse, String> executeHello(
            final ClientSessionEndpoint endpoint,
            final HttpHost target) throws Exception {
        final Future<Message<HttpResponse, String>> f = executeHelloAsync(endpoint, target);
        return f.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
    }

    private static Future<Message<HttpResponse, String>> executeHelloAsync(
            final ClientSessionEndpoint endpoint,
            final HttpHost target) {

        final org.apache.hc.core5.http.message.BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();

        return endpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<String>(new StringAsyncEntityConsumer()),
                null);
    }

    private static void parkAtLeast(final long millis) {
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < deadlineNanos) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private interface Condition {
        boolean get();
    }

    private static void awaitTrue(final Condition condition, final Timeout timeout, final String message) {
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout.toMilliseconds());
        while (System.nanoTime() < deadlineNanos) {
            if (condition.get()) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        Assertions.fail(message);
    }

}
