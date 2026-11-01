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
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.io.Closer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Exhausts the process file descriptors, hence runs isolated.
 */
@Isolated
class TestListeningIOReactorAcceptFailure {

    private static final int MAX_DESCRIPTORS = 500000;

    @Test
    void listenerSurvivesAcceptFailure() throws Exception {
        final AtomicReference<CountDownLatch> acceptedRef = new AtomicReference<>(new CountDownLatch(1));
        final List<Exception> exceptions = new CopyOnWriteArrayList<>();
        final List<SocketChannel> held = new ArrayList<>();
        try (SingleCoreListeningIOReactor reactor = new SingleCoreListeningIOReactor(exceptions::add, IOReactorConfig.DEFAULT, entry -> {
            Closer.closeQuietly(entry.channel);
            acceptedRef.get().countDown();
        })) {
            final Thread reactorThread = new Thread(reactor::execute, "test-reactor");
            reactorThread.start();
            try {
                final ListenerEndpoint endpoint = reactor.listen(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), null)
                        .get(5, TimeUnit.SECONDS);
                final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
                Assertions.assertTrue(connect(address, acceptedRef));

                // The last free descriptor goes to the client, so the server side accept() has none left.
                IOException exhausted = null;
                while (exhausted == null && held.size() < MAX_DESCRIPTORS) {
                    try {
                        held.add(SocketChannel.open());
                    } catch (final IOException ex) {
                        exhausted = ex;
                    }
                }
                Assumptions.assumeTrue(exhausted != null, "File descriptor limit not reachable");
                held.remove(held.size() - 1).close();
                final SocketChannel client = SocketChannel.open();
                held.add(client);
                client.connect(address);

                final long deadline = System.currentTimeMillis() + 5000;
                while (exceptions.isEmpty() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }
                for (final SocketChannel channel : held) {
                    channel.close();
                }
                held.clear();
                Assumptions.assumeFalse(exceptions.isEmpty(), "Accept did not fail");
                Assertions.assertInstanceOf(IOException.class, exceptions.get(0));

                Assertions.assertTrue(connect(address, acceptedRef), "Listener stopped accepting: " + exceptions);
                Assertions.assertEquals(IOReactorStatus.ACTIVE, reactor.getStatus());
            } finally {
                for (final SocketChannel channel : held) {
                    Closer.closeQuietly(channel);
                }
                reactor.initiateShutdown();
                reactorThread.join(5000);
            }
        }
    }

    private static boolean connect(
            final InetSocketAddress address, final AtomicReference<CountDownLatch> acceptedRef) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        acceptedRef.set(latch);
        try (SocketChannel client = SocketChannel.open()) {
            client.connect(address);
            return latch.await(5, TimeUnit.SECONDS);
        }
    }

}
