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
package org.apache.hc.core5.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;

final class FakeConnection implements ModalCloseable {
    private final long closeSleepMs;
    private final AtomicInteger closes = new AtomicInteger(0);
    private final CountDownLatch closedLatch = new CountDownLatch(1);

    FakeConnection() {
        this(0);
    }

    FakeConnection(final long closeSleepMs) {
        this.closeSleepMs = closeSleepMs;
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (closeSleepMs > 0) {
            try {
                Thread.sleep(closeSleepMs);
            } catch (final InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
        closes.incrementAndGet();
        closedLatch.countDown();
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    int closeCount() {
        return closes.get();
    }

    boolean awaitClosed(final long ms) throws InterruptedException {
        return closedLatch.await(ms, TimeUnit.MILLISECONDS);
    }
}
