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

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.DefaultListeningIOReactor;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Basic tests for {@link DefaultListeningIOReactor}.
 */
public class TestDefaultListeningIOReactor {

    private DefaultListeningIOReactor ioReactor;

    @BeforeEach
    public void setup() throws Exception {
        final IOReactorConfig reactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(1)
                .build();
        this.ioReactor = new DefaultListeningIOReactor(new NoopIOEventHandlerFactory(), reactorConfig, null);
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (this.ioReactor != null) {
            this.ioReactor.close(CloseMode.IMMEDIATE);
        }
    }

    @Test
    public void testEndpointUpAndDown() throws Exception {
        ioReactor.start();

        Set<ListenerEndpoint> endpoints = ioReactor.getEndpoints();
        Assertions.assertNotNull(endpoints);
        Assertions.assertEquals(0, endpoints.size());

        final Future<ListenerEndpoint> future1 = ioReactor.listen(new InetSocketAddress(0));
        final ListenerEndpoint endpoint1 = future1.get();

        final Future<ListenerEndpoint> future2 = ioReactor.listen(new InetSocketAddress(0));
        final ListenerEndpoint endpoint2 = future2.get();
        final int port = ((InetSocketAddress) endpoint2.getAddress()).getPort();

        endpoints = ioReactor.getEndpoints();
        Assertions.assertNotNull(endpoints);
        Assertions.assertEquals(2, endpoints.size());

        endpoint1.close();

        endpoints = ioReactor.getEndpoints();
        Assertions.assertNotNull(endpoints);
        Assertions.assertEquals(1, endpoints.size());

        final ListenerEndpoint endpoint = endpoints.iterator().next();

        Assertions.assertEquals(port, ((InetSocketAddress) endpoint.getAddress()).getPort());

        ioReactor.close(CloseMode.GRACEFUL);
        ioReactor.awaitShutdown(TimeValue.ofSeconds(5));
        Assertions.assertEquals(IOReactorStatus.SHUT_DOWN, ioReactor.getStatus());
    }

    @Test
    public void testEndpointAlreadyBound() throws Exception {
        ioReactor.start();

        final Future<ListenerEndpoint> future1 = ioReactor.listen(new InetSocketAddress(0));
        final ListenerEndpoint endpoint1 = future1.get();
        final int port = ((InetSocketAddress) endpoint1.getAddress()).getPort();

        final Future<ListenerEndpoint> future2 = ioReactor.listen(new InetSocketAddress(port));
        Assertions.assertThrows(ExecutionException.class, () -> future2.get());
        ioReactor.close(CloseMode.GRACEFUL);
        ioReactor.awaitShutdown(TimeValue.ofSeconds(5));

        Assertions.assertEquals(IOReactorStatus.SHUT_DOWN, ioReactor.getStatus());
    }

}
