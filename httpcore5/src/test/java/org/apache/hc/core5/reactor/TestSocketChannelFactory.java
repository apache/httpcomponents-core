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
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestSocketChannelFactory {

    @Test
    void testCustomSocketChannelFactory() throws Exception {
        final AtomicReference<SocketAddress> requestedAddress = new AtomicReference<>();
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            final InetSocketAddress remoteAddress = (InetSocketAddress) serverChannel.getLocalAddress();
            final SocketChannelFactory socketChannelFactory = address -> {
                requestedAddress.set(address);
                return SocketChannel.open();
            };
            final IOEventHandler ioEventHandler = Mockito.mock(IOEventHandler.class);
            final IOEventHandlerFactory eventHandlerFactory = (ioSession, attachment) -> ioEventHandler;
            final IOReactorConfig config = IOReactorConfig.custom().setIoThreadCount(1).build();
            final DefaultConnectingIOReactor reactor = new DefaultConnectingIOReactor(
                    eventHandlerFactory, config, null, socketChannelFactory);
            try {
                reactor.start();
                final Future<IOSession> future = reactor.connect(
                        endpoint(remoteAddress),
                        remoteAddress,
                        null,
                        Timeout.ofSeconds(1),
                        null,
                        null);
                final IOSession ioSession = future.get(5, TimeUnit.SECONDS);
                Assertions.assertNotNull(ioSession);
                Assertions.assertEquals(remoteAddress, requestedAddress.get());
            } finally {
                reactor.close();
            }
        }
    }

    @Test
    void testIOExceptionFromCustomSocketChannelFactory() throws Exception {
        final IOException expected = new IOException("custom transport");
        final SocketChannelFactory socketChannelFactory = remoteAddress -> {
            throw expected;
        };
        assertFactoryFailure(socketChannelFactory, expected);
    }

    @Test
    void testRuntimeExceptionFromCustomSocketChannelFactory() throws Exception {
        final RuntimeException expected = new IllegalStateException("custom transport");
        final SocketChannelFactory socketChannelFactory = remoteAddress -> {
            throw expected;
        };
        assertFactoryFailure(socketChannelFactory, expected);
    }

    private static void assertFactoryFailure(
            final SocketChannelFactory socketChannelFactory,
            final Exception expected) throws Exception {
        final IOEventHandlerFactory eventHandlerFactory = Mockito.mock(IOEventHandlerFactory.class);
        final IOReactorConfig config = IOReactorConfig.custom().setIoThreadCount(1).build();
        final DefaultConnectingIOReactor reactor = new DefaultConnectingIOReactor(
                eventHandlerFactory, config, null, socketChannelFactory);
        try {
            reactor.start();
            final InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 80);
            final Future<IOSession> future = reactor.connect(
                    endpoint(remoteAddress),
                    remoteAddress,
                    null,
                    Timeout.ofSeconds(1),
                    null,
                    null);
            final ExecutionException ex = Assertions.assertThrows(
                    ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            Assertions.assertSame(expected, ex.getCause());
        } finally {
            reactor.close();
        }
    }

    private static NamedEndpoint endpoint(final InetSocketAddress address) {
        return new NamedEndpoint() {

            @Override
            public String getHostName() {
                return address.getHostString();
            }

            @Override
            public int getPort() {
                return address.getPort();
            }

        };
    }

}
