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
package org.apache.hc.core5.reactor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Failures of accept() are injected, so the tests do not depend on the file descriptor limit.
 */
class TestListeningIOReactorAcceptBackOff {

    static final class Accepted {

        final String name;
        final long time;

        Accepted(final String name, final long time) {
            this.name = name;
            this.time = time;
        }

    }

    static final class FailingReactor extends SingleCoreListeningIOReactor {

        private final Map<Integer, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
        private final Map<Integer, List<Long>> attempts = new ConcurrentHashMap<>();

        FailingReactor(
                final Consumer<Exception> exceptions,
                final IOReactorConfig config,
                final Consumer<ChannelEntry> entries) {
            super(exceptions::accept, config, entries::accept);
        }

        void failNext(final int port, final int count) {
            failuresLeft.put(port, new AtomicInteger(count));
        }

        List<Long> attempts(final int port) {
            final List<Long> list = attempts.get(port);
            return list != null ? list : new ArrayList<Long>();
        }

        @Override
        SocketChannel accept(final ServerSocketChannel serverChannel) throws IOException {
            final int port = serverChannel.socket().getLocalPort();
            attempts.computeIfAbsent(port, k -> new CopyOnWriteArrayList<>()).add(System.nanoTime());
            final AtomicInteger left = failuresLeft.get(port);
            if (left != null && left.getAndDecrement() > 0) {
                throw new IOException("Too many open files");
            }
            return super.accept(serverChannel);
        }

    }

    private final List<Exception> exceptions = new CopyOnWriteArrayList<>();
    private final List<Accepted> accepted = new CopyOnWriteArrayList<>();
    private final List<SocketChannel> clients = new CopyOnWriteArrayList<>();
    private FailingReactor reactor;
    private Thread reactorThread;

    @BeforeEach
    void setUp() {
        start(IOReactorConfig.DEFAULT);
    }

    private void start(final IOReactorConfig config) {
        reactor = new FailingReactor(exceptions::add, config, entry -> {
            accepted.add(new Accepted(String.valueOf(entry.attachment), System.nanoTime()));
            Closer.closeQuietly(entry.channel);
        });
        reactorThread = new Thread(reactor::execute, "test-reactor");
        reactorThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (final SocketChannel client : clients) {
            Closer.closeQuietly(client);
        }
        reactor.initiateShutdown();
        reactorThread.join(5000);
        Assertions.assertFalse(reactorThread.isAlive(), "Reactor thread did not terminate");
        reactor.close(CloseMode.IMMEDIATE);
    }

    private ListenerEndpoint listen(final String name) throws Exception {
        return reactor.listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), name, null)
                .get(5, TimeUnit.SECONDS);
    }

    private static int port(final ListenerEndpoint endpoint) {
        return ((InetSocketAddress) endpoint.getAddress()).getPort();
    }

    private void connect(final ListenerEndpoint endpoint) throws IOException {
        final SocketChannel client = SocketChannel.open();
        clients.add(client);
        client.connect(endpoint.getAddress());
    }

    private int acceptedBy(final String name) {
        int n = 0;
        for (final Accepted a : accepted) {
            if (a.name.equals(name)) {
                n++;
            }
        }
        return n;
    }

    private static boolean await(final BooleanSupplier condition, final long timeoutMillis) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }

    @Test
    void acceptWithoutFailureIsNotAffected() throws Exception {
        final ListenerEndpoint endpoint = listen("a");
        connect(endpoint);
        Assertions.assertTrue(await(() -> acceptedBy("a") == 1, 5000));
        Assertions.assertTrue(exceptions.isEmpty());
    }

    @Test
    void listenerRecoversAfterRepeatedFailures() throws Exception {
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), 3);
        connect(endpoint);

        Assertions.assertTrue(await(() -> acceptedBy("a") == 1, 5000), "Listener did not recover: " + exceptions);
        Assertions.assertEquals(3, exceptions.size());
        Assertions.assertEquals(IOReactorStatus.ACTIVE, reactor.getStatus());
        // Every failure is followed by a pause, not an immediate retry.
        final List<Long> attempts = reactor.attempts(port(endpoint));
        for (int i = 1; i < 4; i++) {
            final long gap = TimeUnit.NANOSECONDS.toMillis(attempts.get(i) - attempts.get(i - 1));
            Assertions.assertTrue(gap >= 80, "Retry after " + gap + " ms");
        }
    }

    @Test
    void failingAcceptDoesNotSpin() throws Exception {
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), Integer.MAX_VALUE);
        connect(endpoint);

        Thread.sleep(1000);

        // About ten attempts per second; a busy loop would make thousands.
        final int attempts = reactor.attempts(port(endpoint)).size();
        Assertions.assertTrue(attempts >= 5 && attempts <= 15, "Attempts: " + attempts);
        Assertions.assertEquals(attempts, exceptions.size());
        Assertions.assertEquals(IOReactorStatus.ACTIVE, reactor.getStatus());
        Assertions.assertEquals(0, acceptedBy("a"));
    }

    @Test
    void otherListenerIsNotDelayedDuringBackOff() throws Exception {
        final ListenerEndpoint failing = listen("failing");
        final ListenerEndpoint healthy = listen("healthy");
        reactor.failNext(port(failing), Integer.MAX_VALUE);
        connect(failing);

        final List<Long> latencies = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            final int expected = i;
            final long start = System.nanoTime();
            connect(healthy);
            Assertions.assertTrue(await(() -> acceptedBy("healthy") == expected, 5000));
            latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            Thread.sleep(17);
        }
        latencies.sort(null);
        // A reactor thread sleeping through the back-off would give a median of about 50 ms.
        Assertions.assertTrue(latencies.get(latencies.size() / 2) < 30, "Latencies: " + latencies);
    }

    @Test
    void listenRequestIsServedDuringBackOff() throws Exception {
        final ListenerEndpoint failing = listen("failing");
        reactor.failNext(port(failing), Integer.MAX_VALUE);
        connect(failing);
        Assertions.assertTrue(await(() -> !exceptions.isEmpty(), 5000));

        final long start = System.nanoTime();
        final ListenerEndpoint another = listen("another");
        Assertions.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
        connect(another);
        Assertions.assertTrue(await(() -> acceptedBy("another") == 1, 5000));
    }

    @Test
    void listenersRecoverIndependently() throws Exception {
        final ListenerEndpoint first = listen("first");
        final ListenerEndpoint second = listen("second");
        reactor.failNext(port(first), 1);
        reactor.failNext(port(second), 1);

        connect(first);
        Assertions.assertTrue(await(() -> !reactor.attempts(port(first)).isEmpty(), 5000));
        Thread.sleep(60);
        connect(second);

        Assertions.assertTrue(await(() -> acceptedBy("first") == 1 && acceptedBy("second") == 1, 5000));
        long firstRecovered = 0;
        long secondRecovered = 0;
        for (final Accepted a : accepted) {
            if (a.name.equals("first")) {
                firstRecovered = a.time;
            } else {
                secondRecovered = a.time;
            }
        }
        // The second failure must not postpone the recovery of the first listener.
        final long gap = TimeUnit.NANOSECONDS.toMillis(secondRecovered - firstRecovered);
        Assertions.assertTrue(gap >= 30, "Recovery gap: " + gap + " ms");
    }

    @Test
    void closedEndpointIsNotRetried() throws Exception {
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), Integer.MAX_VALUE);
        connect(endpoint);
        // Wait for the failure to be reported, as a channel closed meanwhile is not a failure.
        Assertions.assertTrue(await(() -> exceptions.size() == 1, 5000));

        endpoint.close();
        Thread.sleep(400);

        Assertions.assertEquals(1, reactor.attempts(port(endpoint)).size());
        Assertions.assertEquals(1, exceptions.size());
        Assertions.assertEquals(IOReactorStatus.ACTIVE, reactor.getStatus());
        final ListenerEndpoint another = listen("another");
        connect(another);
        Assertions.assertTrue(await(() -> acceptedBy("another") == 1, 5000));
    }

    @Test
    void shutdownWhileSuspendedIsPrompt() throws Exception {
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), Integer.MAX_VALUE);
        connect(endpoint);
        Assertions.assertTrue(await(() -> !exceptions.isEmpty(), 5000));

        final long start = System.nanoTime();
        reactor.initiateShutdown();
        reactorThread.join(5000);
        Assertions.assertFalse(reactorThread.isAlive());
        Assertions.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
    }

    @Test
    void backOffIsShorterThanLongSelectInterval() throws Exception {
        tearDownStarted();
        start(IOReactorConfig.custom().setSelectInterval(TimeValue.ofSeconds(30)).build());
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), 1);
        connect(endpoint);
        Assertions.assertTrue(await(() -> acceptedBy("a") == 1, 3000), "Select interval postponed the retry");
    }

    @Test
    void backOffHonoursShortSelectInterval() throws Exception {
        tearDownStarted();
        start(IOReactorConfig.custom().setSelectInterval(TimeValue.ofMilliseconds(10)).build());
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), Integer.MAX_VALUE);
        connect(endpoint);

        Thread.sleep(1000);

        final int attempts = reactor.attempts(port(endpoint)).size();
        Assertions.assertTrue(attempts >= 5 && attempts <= 15, "Attempts: " + attempts);
    }

    @Test
    void backOffWorksWithIndefiniteSelect() throws Exception {
        tearDownStarted();
        start(IOReactorConfig.custom().setSelectInterval(TimeValue.ZERO_MILLISECONDS).build());
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), 2);
        connect(endpoint);
        Assertions.assertTrue(await(() -> acceptedBy("a") == 1, 3000), "Select(0) blocked the retry");
        Assertions.assertEquals(2, exceptions.size());
    }

    @Test
    void acceptsMoreThanOneBatchAfterFailure() throws Exception {
        final ListenerEndpoint endpoint = listen("a");
        reactor.failNext(port(endpoint), 1);
        // More than the accept() limit per selection event, which are picked up by further events.
        final int count = 120;
        for (int i = 0; i < count; i++) {
            connect(endpoint);
        }
        Assertions.assertTrue(await(() -> acceptedBy("a") == count, 5000), "Accepted: " + acceptedBy("a"));
        Assertions.assertEquals(1, exceptions.size());
    }

    @Test
    void pauseAndResumeWhileRetryIsPending() throws Exception {
        final ListenerEndpoint endpoint = listen("a");
        final int port = port(endpoint);
        reactor.failNext(port, Integer.MAX_VALUE);
        connect(endpoint);
        Assertions.assertTrue(await(() -> reactor.attempts(port).size() == 1, 5000));

        reactor.pause();
        Thread.sleep(300);
        // Paused endpoints are closed, so the pending retry must not fire.
        Assertions.assertEquals(1, reactor.attempts(port).size());
        Assertions.assertEquals(IOReactorStatus.ACTIVE, reactor.getStatus());

        reactor.failNext(port, 0);
        reactor.resume();
        Assertions.assertTrue(await(() -> !reactor.getEndpoints().isEmpty(), 5000), "Endpoint not restored");
        connect(endpoint);
        Assertions.assertTrue(await(() -> acceptedBy("a") == 1, 5000), "Listener did not accept after resume");
    }

    private void tearDownStarted() throws Exception {
        reactor.initiateShutdown();
        reactorThread.join(5000);
        reactor.close(CloseMode.IMMEDIATE);
    }

}
