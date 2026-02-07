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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestMultiCoreIOReactor {

    private static class TestReactor implements IOReactor {
        final AtomicBoolean shutdown = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();
        volatile IOReactorStatus status = IOReactorStatus.INACTIVE;

        @Override
        public void close() {
            close(CloseMode.GRACEFUL);
        }

        @Override
        public void close(final CloseMode closeMode) {
            closed.set(true);
            status = IOReactorStatus.SHUT_DOWN;
        }

        @Override
        public IOReactorStatus getStatus() {
            return status;
        }

        @Override
        public void initiateShutdown() {
            shutdown.set(true);
            status = IOReactorStatus.SHUTTING_DOWN;
        }

        @Override
        public void awaitShutdown(final TimeValue waitTime) {
            status = IOReactorStatus.SHUT_DOWN;
        }

    }

    @Test
    void startAndShutdown() throws Exception {
        final TestReactor r1 = new TestReactor();
        final TestReactor r2 = new TestReactor();
        final Thread t1 = new Thread(() -> {
        });
        final Thread t2 = new Thread(() -> {
        });

        try (MultiCoreIOReactor reactor = new MultiCoreIOReactor(
                new IOReactor[] { r1, r2 }, new Thread[] { t1, t2 })) {
            reactor.start();
            Assertions.assertEquals(IOReactorStatus.ACTIVE, reactor.getStatus());

            reactor.initiateShutdown();
            Assertions.assertTrue(r1.shutdown.get());
            Assertions.assertTrue(r2.shutdown.get());

            reactor.close(CloseMode.IMMEDIATE);
            Assertions.assertTrue(r1.closed.get());
            Assertions.assertTrue(r2.closed.get());
            Assertions.assertEquals(IOReactorStatus.SHUT_DOWN, reactor.getStatus());
        }
    }

    @Test
    void toStringIncludesStatus() {
        try (MultiCoreIOReactor reactor = new MultiCoreIOReactor(new IOReactor[] {}, new Thread[] {})) {
            Assertions.assertTrue(reactor.toString().contains("status"));
        }
    }

}
