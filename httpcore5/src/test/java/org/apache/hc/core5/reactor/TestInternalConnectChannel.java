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
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestInternalConnectChannel {

    @Test
    void onTimeoutFailsRequestAndCloses() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT);
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final NamedEndpoint endpoint = new NamedEndpoint() {
            @Override
            public String getHostName() {
                return "example.com";
            }

            @Override
            public int getPort() {
                return 443;
            }
        };
        final IOSessionRequest request = new IOSessionRequest(
                endpoint,
                new InetSocketAddress("example.com", 443),
                null,
                Timeout.ofMilliseconds(1),
                null,
                new FutureCallback<IOSession>() {
                    @Override
                    public void completed(final IOSession result) {
                    }

                    @Override
                    public void failed(final Exception ex) {
                        failure.set(ex);
                    }

                    @Override
                    public void cancelled() {
                    }
                });

            try (InternalConnectChannel connectChannel = new InternalConnectChannel(
                    key,
                    channel,
                    request,
                    null,
                    null,
                    IOReactorConfig.DEFAULT)) {
                connectChannel.onTimeout(Timeout.ofMilliseconds(1));

                Assertions.assertTrue(failure.get() instanceof SocketTimeoutException);
                Assertions.assertFalse(channel.isOpen());
            }
        }
    }

    @Test
    void toStringUsesRequest() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT);
        final NamedEndpoint endpoint = new NamedEndpoint() {
            @Override
            public String getHostName() {
                return "example.com";
            }

            @Override
            public int getPort() {
                return 443;
            }
        };
        final IOSessionRequest request = new IOSessionRequest(
                endpoint,
                new InetSocketAddress("example.com", 443),
                null,
                Timeout.ofMilliseconds(1),
                "att",
                null);
            try (InternalConnectChannel connectChannel = new InternalConnectChannel(
                    key,
                    channel,
                    request,
                    null,
                    null,
                    IOReactorConfig.DEFAULT)) {
                Assertions.assertTrue(connectChannel.toString().contains("remoteEndpoint"));
            }
        }
    }

}
