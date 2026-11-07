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
package org.apache.hc.core5.http2.impl.nio;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2ControlFrameRateLimiter {

    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    private static void assertAccepted(final H2ControlFrameRateLimiter limiter, final int count, final long nowNanos) {
        for (int i = 0; i < count; i++) {
            Assertions.assertTrue(limiter.accept(nowNanos), "frame " + (i + 1) + " of " + count);
        }
    }

    @Test
    void testInitialCapacity() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);

        assertAccepted(limiter, 4, 0L);
    }

    @Test
    void testExhaustion() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);

        assertAccepted(limiter, 4, 0L);
        Assertions.assertFalse(limiter.accept(0L));
    }

    @Test
    void testProportionalRefill() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);
        assertAccepted(limiter, 4, 0L);

        // 2 frames per second: one second brings back 2 frames, not 4
        assertAccepted(limiter, 2, SECOND);
        Assertions.assertFalse(limiter.accept(SECOND));

        assertAccepted(limiter, 2, 2 * SECOND);
        Assertions.assertFalse(limiter.accept(2 * SECOND));
    }

    @Test
    void testFractionalRefill() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);
        assertAccepted(limiter, 4, 0L);

        // 0.25 s at 2 frames per second is half a frame
        Assertions.assertFalse(limiter.accept(SECOND / 4));
        // another 0.25 s completes the frame
        Assertions.assertTrue(limiter.accept(SECOND / 2));
        Assertions.assertFalse(limiter.accept(SECOND / 2));
    }

    @Test
    void testRecoveryAfterRejection() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);
        assertAccepted(limiter, 4, 0L);
        Assertions.assertFalse(limiter.accept(0L));

        Assertions.assertTrue(limiter.accept(SECOND));
    }

    @Test
    void testRefillDoesNotExceedCapacity() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);
        assertAccepted(limiter, 1, 0L);

        // 3 frames left, one second adds 2: 5 would exceed the capacity of 4
        assertAccepted(limiter, 4, SECOND);
        Assertions.assertFalse(limiter.accept(SECOND));
    }

    @Test
    void testIdleTimeRestoresCapacityOnly() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);
        assertAccepted(limiter, 4, 0L);

        assertAccepted(limiter, 4, 10 * SECOND);
        Assertions.assertFalse(limiter.accept(10 * SECOND));
    }

    @Test
    void testCustomParameters() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(10, 5);

        assertAccepted(limiter, 10, 0L);
        Assertions.assertFalse(limiter.accept(0L));

        assertAccepted(limiter, 5, SECOND);
        Assertions.assertFalse(limiter.accept(SECOND));
    }

    @Test
    void testDefaultParameters() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter();

        assertAccepted(limiter, H2ControlFrameRateLimiter.CAPACITY, 0L);
        Assertions.assertFalse(limiter.accept(0L));

        assertAccepted(limiter, H2ControlFrameRateLimiter.REFILL_PER_SECOND, SECOND);
        Assertions.assertFalse(limiter.accept(SECOND));
    }

    @Test
    void testClockRollbackDoesNotRefill() {
        final H2ControlFrameRateLimiter limiter = new H2ControlFrameRateLimiter(4, 2);
        assertAccepted(limiter, 4, 100L);

        Assertions.assertFalse(limiter.accept(99L));
    }

    @Test
    void testInvalidParameters() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new H2ControlFrameRateLimiter(0, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new H2ControlFrameRateLimiter(-1, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new H2ControlFrameRateLimiter(1, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new H2ControlFrameRateLimiter(1, -1));
    }

}
