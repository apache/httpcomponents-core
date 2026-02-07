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

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestDefaultConnectingIOReactor {

    private DefaultConnectingIOReactor newReactor(final IOWorkerSelector selector) {
        final IOEventHandlerFactory factory = Mockito.mock(IOEventHandlerFactory.class);
        Mockito.when(factory.createHandler(Mockito.any(), Mockito.any())).thenReturn(Mockito.mock(IOEventHandler.class));
        @SuppressWarnings("unchecked")
        final Decorator<IOSession> decorator = (Decorator<IOSession>) Mockito.mock(Decorator.class);
        final IOReactorConfig config = IOReactorConfig.custom().setIoThreadCount(1).build();
        return new DefaultConnectingIOReactor(
                factory,
                config,
                null,
                decorator,
                null,
                null,
                null,
                null,
                selector);
    }

    @Test
    void selectWorkerUsesSelector() {
        final IOWorkerSelector selector = dispatchers -> 0;
        final DefaultConnectingIOReactor reactor = newReactor(selector);
        try {
            final SingleCoreIOReactor worker = reactor.selectWorker();
            Assertions.assertNotNull(worker);
        } finally {
            reactor.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

    @Test
    void connectDelegatesToWorker() throws Exception {
        final IOWorkerSelector selector = dispatchers -> 0;
        final DefaultConnectingIOReactor reactor = newReactor(selector);
        try {
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

            Assertions.assertEquals(1, reactor.selectWorker().pendingChannelCount());
        } finally {
            reactor.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

    @Test
    void statusDelegatesToInnerReactor() {
        final DefaultConnectingIOReactor reactor = newReactor(dispatchers -> 0);
        try {
            Assertions.assertEquals(IOReactorStatus.INACTIVE, reactor.getStatus());
        } finally {
            reactor.close(org.apache.hc.core5.io.CloseMode.IMMEDIATE);
        }
    }

}
