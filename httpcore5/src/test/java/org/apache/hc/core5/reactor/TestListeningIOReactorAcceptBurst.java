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

import org.apache.hc.core5.io.Closer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TestListeningIOReactorAcceptBurst {

    // More than the number of connections accepted per selection event
    private static final int CONNECTIONS = 150;

    @Test
    void testBurstOfConnectionsIsAcceptedInFull() throws Exception {
        final CountDownLatch accepted = new CountDownLatch(CONNECTIONS);
        final List<Exception> exceptions = new CopyOnWriteArrayList<>();
        final List<SocketChannel> clients = new ArrayList<>();
        try (SingleCoreListeningIOReactor reactor = new SingleCoreListeningIOReactor(exceptions::add, IOReactorConfig.DEFAULT, entry -> {
            Closer.closeQuietly(entry.channel);
            accepted.countDown();
        })) {
            final Thread reactorThread = new Thread(reactor::execute, "test-reactor");
            reactorThread.start();
            try {
                final ListenerEndpoint endpoint = reactor.listen(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), null)
                        .get(5, TimeUnit.SECONDS);
                final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
                try {
                    for (int i = 0; i < CONNECTIONS; i++) {
                        final SocketChannel client = SocketChannel.open();
                        clients.add(client);
                        client.connect(address);
                    }
                } catch (final IOException ex) {
                    Assumptions.assumeTrue(false, "Not enough file descriptors: " + ex);
                }
                if (!accepted.await(10, TimeUnit.SECONDS)) {
                    Assumptions.assumeTrue(exceptions.isEmpty(), "Accept failed: " + exceptions);
                    Assertions.fail("Accepted " + (CONNECTIONS - accepted.getCount()) + " of " + CONNECTIONS);
                }
            } finally {
                for (final SocketChannel client : clients) {
                    Closer.closeQuietly(client);
                }
                reactor.initiateShutdown();
                reactorThread.join(5000);
            }
        }
    }

}
