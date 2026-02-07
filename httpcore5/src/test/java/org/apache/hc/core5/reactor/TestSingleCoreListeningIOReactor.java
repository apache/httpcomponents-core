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

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestSingleCoreListeningIOReactor {

    @Test
    void listenAcceptsConnectionPauseResume() throws Exception {
        final CountDownLatch accepted = new CountDownLatch(1);
        final AtomicReference<ChannelEntry> entryRef = new AtomicReference<>();
        final IOReactorConfig config = IOReactorConfig.custom()
                .setSelectInterval(TimeValue.ofMilliseconds(10))
                .build();
        try (SingleCoreListeningIOReactor reactor = new SingleCoreListeningIOReactor(null, config, entry -> {
            entryRef.set(entry);
            accepted.countDown();
            try {
                entry.channel.close();
            } catch (final Exception ignore) {
            }
        })) {
            final Thread reactorThread = new Thread(reactor::execute, "test-reactor");
            reactorThread.start();

            final AtomicReference<ListenerEndpoint> endpointRef = new AtomicReference<>();
            final FutureCallback<ListenerEndpoint> callback = new FutureCallback<ListenerEndpoint>() {
                @Override
                public void completed(final ListenerEndpoint result) {
                    endpointRef.set(result);
                }

                @Override
                public void failed(final Exception ex) {
                }

                @Override
                public void cancelled() {
                }
            };

            final ListenerEndpoint endpoint = reactor.listen(new InetSocketAddress("localhost", 0), "att", callback)
                    .get(1, TimeUnit.SECONDS);
            Assertions.assertNotNull(endpoint);
            Assertions.assertNotNull(endpoint.getAddress());

            try (SocketChannel client = SocketChannel.open()) {
                client.connect(endpoint.getAddress());
            }

            Assertions.assertTrue(accepted.await(1, TimeUnit.SECONDS));
            Assertions.assertNotNull(entryRef.get());
            Assertions.assertEquals("att", entryRef.get().attachment);

            final Set<ListenerEndpoint> endpointsBeforePause = reactor.getEndpoints();
            Assertions.assertFalse(endpointsBeforePause.isEmpty());

            reactor.pause();
            Assertions.assertTrue(reactor.getEndpoints().isEmpty());

            reactor.resume();

            reactor.initiateShutdown();
            reactorThread.join(1000);
        }
    }

    @Test
    void listenAfterShutdownThrows() {
        try (SingleCoreListeningIOReactor reactor = new SingleCoreListeningIOReactor(null, IOReactorConfig.DEFAULT, entry -> {
        })) {
            reactor.initiateShutdown();

            Assertions.assertThrows(IOReactorShutdownException.class, () ->
                    reactor.listen(new InetSocketAddress("localhost", 0), null));
        }
    }

}
