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
import java.net.SocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestAbstractIOReactorBase {

    private static class StubSingleCoreIOReactor extends SingleCoreIOReactor {
        private final Future<IOSession> future;
        private boolean connectCalled;

        StubSingleCoreIOReactor(final Future<IOSession> future) {
            super(null, Mockito.mock(IOEventHandlerFactory.class), IOReactorConfig.DEFAULT, null, null, null, null);
            this.future = future;
        }

        @Override
        public Future<IOSession> connect(final NamedEndpoint remoteEndpoint, final SocketAddress remoteAddress,
                                         final SocketAddress localAddress, final Timeout timeout, final Object attachment,
                                         final org.apache.hc.core5.concurrent.FutureCallback<IOSession> callback) {
            connectCalled = true;
            return future;
        }

        boolean isConnectCalled() {
            return connectCalled;
        }
    }

    private static class TestReactor extends AbstractIOReactorBase {
        private IOReactorStatus status;
        private boolean shutdownCalled;
        private final SingleCoreIOReactor worker;

        TestReactor(final IOReactorStatus status, final SingleCoreIOReactor worker) {
            this.status = status;
            this.worker = worker;
        }

        @Override
        SingleCoreIOReactor selectWorker() {
            return worker;
        }

        @Override
        public IOReactorStatus getStatus() {
            return status;
        }

        @Override
        public void initiateShutdown() {
            shutdownCalled = true;
            status = IOReactorStatus.SHUTTING_DOWN;
        }

        @Override
        public void start() {
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) {
        }

        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public void close() {
        }
    }

    private static NamedEndpoint endpoint() {
        return new NamedEndpoint() {
            @Override
            public String getHostName() {
                return "localhost";
            }

            @Override
            public int getPort() {
                return 80;
            }
        };
    }

    @Test
    void connectFailsWhenReactorShuttingDown() {
        try (StubSingleCoreIOReactor worker = new StubSingleCoreIOReactor(new BasicFuture<>(null))) {
            final TestReactor reactor = new TestReactor(IOReactorStatus.SHUTTING_DOWN, worker);
        try {
            Assertions.assertThrows(IOReactorShutdownException.class, () ->
                    reactor.connect(endpoint(), new InetSocketAddress("localhost", 80), null, Timeout.ofSeconds(1), null, null));
        } finally {
            reactor.close();
        }
        }
    }

    @Test
    void connectFailsWhenWorkerShutdownTriggersShutdown() {
        try (StubSingleCoreIOReactor worker = new StubSingleCoreIOReactor(new BasicFuture<>(null))) {
            worker.initiateShutdown();
            final TestReactor reactor = new TestReactor(IOReactorStatus.ACTIVE, worker);
            try {
                Assertions.assertThrows(IOReactorShutdownException.class, () ->
                        reactor.connect(endpoint(), new InetSocketAddress("localhost", 80), null, Timeout.ofSeconds(1), null, null));
                Assertions.assertTrue(reactor.shutdownCalled);
                Assertions.assertFalse(worker.isConnectCalled());
            } finally {
                reactor.close();
            }
        }
    }

    @Test
    void connectDelegatesToWorker() throws Exception {
        final BasicFuture<IOSession> future = new BasicFuture<>(null);
        try (StubSingleCoreIOReactor worker = new StubSingleCoreIOReactor(future)) {
            final TestReactor reactor = new TestReactor(IOReactorStatus.INACTIVE, worker);
            try {
                final Future<IOSession> result = reactor.connect(
                        endpoint(),
                        new InetSocketAddress("localhost", 80),
                        null,
                        Timeout.ofSeconds(1),
                        null,
                        null);

                Assertions.assertSame(future, result);
                Assertions.assertTrue(worker.isConnectCalled());
            } finally {
                reactor.close();
            }
        }
    }

}
