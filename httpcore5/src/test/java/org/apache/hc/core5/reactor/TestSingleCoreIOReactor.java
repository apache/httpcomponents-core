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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestSingleCoreIOReactor {

    private SingleCoreIOReactor newReactor() {
        final IOEventHandlerFactory factory = Mockito.mock(IOEventHandlerFactory.class);
        Mockito.when(factory.createHandler(Mockito.any(), Mockito.any())).thenReturn(Mockito.mock(IOEventHandler.class));
        @SuppressWarnings("unchecked")
        final Decorator<IOSession> decorator = (Decorator<IOSession>) Mockito.mock(Decorator.class);
        return new SingleCoreIOReactor(null, factory, IOReactorConfig.DEFAULT, decorator, null, null, null);
    }

    @Test
    void enqueueChannelAndPendingCounts() throws Exception {
        try (SingleCoreIOReactor reactor = newReactor();
             SocketChannel channel = SocketChannel.open()) {
            Assertions.assertEquals(0, reactor.pendingChannelCount());

            reactor.enqueueChannel(new ChannelEntry(channel, "att"));

            Assertions.assertEquals(1, reactor.pendingChannelCount());
            Assertions.assertEquals(0, reactor.totalChannelCount());
        }
    }

    @Test
    void connectEnqueuesRequest() throws Exception {
        try (SingleCoreIOReactor reactor = newReactor()) {
            final NamedEndpoint endpoint = new NamedEndpoint() {
                @Override
                public String getHostName() {
                    return "localhost";
                }

                @Override
                public int getPort() {
                    return 80;
                }
            };

            reactor.connect(endpoint, new InetSocketAddress("localhost", 80), null, Timeout.ofSeconds(1), null, null);

            Assertions.assertEquals(1, reactor.pendingChannelCount());
        }
    }

    @Test
    void enqueueAfterShutdownThrows() throws Exception {
        try (SingleCoreIOReactor reactor = newReactor();
             SocketChannel channel = SocketChannel.open()) {
            reactor.initiateShutdown();

            Assertions.assertThrows(IOReactorShutdownException.class, () ->
                    reactor.enqueueChannel(new ChannelEntry(channel, null)));
        }
    }

    @Test
    void terminatesWhenSelectorClosedByAnotherThread() throws Exception {
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch reactorClosed = new CountDownLatch(1);
        final IOEventHandler handler = Mockito.mock(IOEventHandler.class);
        Mockito.doAnswer(invocation -> {
            connected.countDown();
            reactorClosed.await();
            return null;
        }).when(handler).connected(Mockito.any());
        final List<Exception> exceptions = new CopyOnWriteArrayList<>();
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            try (SocketChannel client = SocketChannel.open(server.getLocalAddress());
                 SocketChannel channel = server.accept()) {
                final SingleCoreIOReactor reactor = new SingleCoreIOReactor(
                        exceptions::add, (session, attachment) -> handler, IOReactorConfig.DEFAULT, null, null, null, null);
                final Thread worker = new Thread(reactor::execute);
                worker.start();
                reactor.enqueueChannel(new ChannelEntry(channel, null));
                Assertions.assertTrue(connected.await(5, TimeUnit.SECONDS));

                reactor.close(CloseMode.IMMEDIATE);
                reactorClosed.countDown();
                worker.join(TimeUnit.SECONDS.toMillis(5));

                Assertions.assertFalse(worker.isAlive());
                Assertions.assertTrue(exceptions.isEmpty(), exceptions::toString);
                Mockito.verify(handler).disconnected(Mockito.any());
            }
        }
    }

}
