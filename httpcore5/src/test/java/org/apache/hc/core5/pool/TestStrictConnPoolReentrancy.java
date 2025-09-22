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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

final class TestStrictConnPoolRecursion {

    static final class Dummy implements ModalCloseable {
        @Override
        public void close(final CloseMode closeMode) {
        }

        @Override
        public void close() throws IOException {
        }
    }

    /**
     * Regression test for HTTPCLIENT-2398:
     * Previously this pattern caused fireCallbacks()->release()->fireCallbacks() recursion
     * and a StackOverflowError. With the fix, we should iterate many times without error.
     */
    @Test
    @org.junit.jupiter.api.Timeout(10)
    void leaseFromCallback_no_recursion_after_fix() throws Exception {
        final StrictConnPool<String, Dummy> pool = new StrictConnPool<>(1, 1);
        final String route = "r";

        final int LIMIT = 10_000; // high enough to catch recursion, low enough to be fast
        final AtomicLong seen = new AtomicLong();
        final CountDownLatch done = new CountDownLatch(1);

        final FutureCallback<PoolEntry<String, Dummy>> cb = new FutureCallback<PoolEntry<String, Dummy>>() {
            @Override
            public void completed(final PoolEntry<String, Dummy> entry) {
                pool.release(entry, true);
                final long v = seen.incrementAndGet();
                if (v < LIMIT) {
                    pool.lease(route, null, Timeout.ZERO_MILLISECONDS, this);
                } else {
                    done.countDown();
                }
            }

            @Override
            public void failed(final Exception ex) { /* not used */ }

            @Override
            public void cancelled() { /* not used */ }
        };

        // Seed the first lease; the callback keeps re-leasing synchronously.
        pool.lease(route, null, Timeout.ZERO_MILLISECONDS, cb);

        // We should finish LIMIT iterations within the timeout and without StackOverflowError.
        assertTrue(done.await(5, TimeUnit.SECONDS), "Did not complete iterations in time");
        assertEquals(LIMIT, seen.get(), "Unexpected number of lease iterations");
    }
}
