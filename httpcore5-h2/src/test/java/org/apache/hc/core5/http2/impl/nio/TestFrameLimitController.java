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

class TestFrameLimitController {

    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    // A generous bucket for the frame kind a given test is not exercising, so it never
    // interferes with the one under test.
    private static final int GENEROUS_CAPACITY = 1_000_000;
    private static final int GENEROUS_REFILL = 1_000_000;

    private static FrameLimitController withControlFrame(final int capacity, final int refillPerSecond) {
        return new FrameLimitController(capacity, refillPerSecond, GENEROUS_CAPACITY, GENEROUS_REFILL, SECOND);
    }

    private static FrameLimitController withRapidReset(final int capacity, final int refillPerSecond, final long maxAgeNanos) {
        return new FrameLimitController(GENEROUS_CAPACITY, GENEROUS_REFILL, capacity, refillPerSecond, maxAgeNanos);
    }

    // -------------------------------------------------------------------------
    // Control frame bucket (PING and SETTINGS share this one, exactly as before
    // this class existed: ported from the former TestH2ControlFrameRateLimiter)
    // -------------------------------------------------------------------------

    private static void assertAccepted(final FrameLimitController controller, final int count, final long nowNanos) {
        for (int i = 0; i < count; i++) {
            Assertions.assertTrue(controller.acceptControlFrame(nowNanos), "frame " + (i + 1) + " of " + count);
        }
    }

    @Test
    void testInitialCapacity() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 4, 0L);
    }

    @Test
    void testExhaustion() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 4, 0L);
        Assertions.assertFalse(controller.acceptControlFrame(0L));
    }

    @Test
    void testProportionalRefill() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 4, 0L);

        // 2 frames per second: one second brings back 2 frames, not 4
        assertAccepted(controller, 2, SECOND);
        Assertions.assertFalse(controller.acceptControlFrame(SECOND));

        assertAccepted(controller, 2, 2 * SECOND);
        Assertions.assertFalse(controller.acceptControlFrame(2 * SECOND));
    }

    @Test
    void testFractionalRefill() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 4, 0L);

        // 0.25 s at 2 frames per second is half a frame
        Assertions.assertFalse(controller.acceptControlFrame(SECOND / 4));
        // another 0.25 s completes the frame
        Assertions.assertTrue(controller.acceptControlFrame(SECOND / 2));
        Assertions.assertFalse(controller.acceptControlFrame(SECOND / 2));
    }

    @Test
    void testRecoveryAfterRejection() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 4, 0L);
        Assertions.assertFalse(controller.acceptControlFrame(0L));

        Assertions.assertTrue(controller.acceptControlFrame(SECOND));
    }

    @Test
    void testRefillDoesNotExceedCapacity() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 1, 0L);

        // 3 frames left, one second adds 2: 5 would exceed the capacity of 4
        assertAccepted(controller, 4, SECOND);
        Assertions.assertFalse(controller.acceptControlFrame(SECOND));
    }

    @Test
    void testIdleTimeRestoresCapacityOnly() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 4, 0L);

        assertAccepted(controller, 4, 10 * SECOND);
        Assertions.assertFalse(controller.acceptControlFrame(10 * SECOND));
    }

    @Test
    void testCustomParameters() {
        final FrameLimitController controller = withControlFrame(10, 5);

        assertAccepted(controller, 10, 0L);
        Assertions.assertFalse(controller.acceptControlFrame(0L));

        assertAccepted(controller, 5, SECOND);
        Assertions.assertFalse(controller.acceptControlFrame(SECOND));
    }

    @Test
    void testDefaultParameters() {
        final FrameLimitController controller = new FrameLimitController();

        assertAccepted(controller, FrameLimitController.CONTROL_FRAME_CAPACITY, 0L);
        Assertions.assertFalse(controller.acceptControlFrame(0L));

        assertAccepted(controller, FrameLimitController.CONTROL_FRAME_REFILL_PER_SECOND, SECOND);
        Assertions.assertFalse(controller.acceptControlFrame(SECOND));
    }

    @Test
    void testClockRollbackDoesNotRefill() {
        final FrameLimitController controller = withControlFrame(4, 2);
        assertAccepted(controller, 4, 100L);
        Assertions.assertFalse(controller.acceptControlFrame(99L));
    }

    @Test
    void testInvalidParameters() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> withControlFrame(0, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> withControlFrame(-1, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> withControlFrame(1, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> withControlFrame(1, -1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> withRapidReset(1, 1, 0L));
        Assertions.assertThrows(IllegalArgumentException.class, () -> withRapidReset(1, 1, -1L));
    }

    // -------------------------------------------------------------------------
    // Rapid reset has its own, independent bucket
    // -------------------------------------------------------------------------

    @Test
    void testRapidResetHasItsOwnBudgetIndependentOfControlFrames() {
        final FrameLimitController controller = new FrameLimitController(1, 1, 1, 1, SECOND);

        Assertions.assertTrue(controller.acceptRapidReset(0L, 0L));
        Assertions.assertFalse(controller.acceptRapidReset(0L, 0L));
        // Exhausting RST_STREAM must not affect PING / SETTINGS.
        Assertions.assertTrue(controller.acceptControlFrame(0L));
    }

    @Test
    void testShortLivedResetsAreLimited() {
        final FrameLimitController controller = withRapidReset(2, 1, SECOND);
        Assertions.assertTrue(controller.acceptRapidReset(0, 0));
        Assertions.assertTrue(controller.acceptRapidReset(0, 0));
        Assertions.assertFalse(controller.acceptRapidReset(0, 0));
    }

    @Test
    void testLongLivedStreamResetIsNotCharged() {
        final FrameLimitController controller = withRapidReset(1, 1, SECOND);
        Assertions.assertTrue(controller.acceptRapidReset(0, 0));
        Assertions.assertFalse(controller.acceptRapidReset(0, 0));
        Assertions.assertTrue(controller.acceptRapidReset(0, SECOND));
        Assertions.assertFalse(controller.acceptRapidReset(0, 0));
    }

    @Test
    void testRapidResetTokensRecoverOverTime() {
        final FrameLimitController controller = withRapidReset(2, 1, SECOND);
        Assertions.assertTrue(controller.acceptRapidReset(0, 0));
        Assertions.assertTrue(controller.acceptRapidReset(0, 0));
        Assertions.assertFalse(controller.acceptRapidReset(0, 0));
        Assertions.assertTrue(controller.acceptRapidReset(SECOND, SECOND));
        Assertions.assertFalse(controller.acceptRapidReset(SECOND, SECOND));
    }

    @Test
    void testIndependentConnectionsHaveIndependentBudgets() {
        final FrameLimitController first = withRapidReset(1, 1, SECOND);
        final FrameLimitController second = withRapidReset(1, 1, SECOND);
        Assertions.assertTrue(first.acceptRapidReset(0, 0));
        Assertions.assertFalse(first.acceptRapidReset(0, 0));
        Assertions.assertTrue(second.acceptRapidReset(0, 0));
    }

    @Test
    void testClockMovingBackwardsDoesNotRejectRapidReset() {
        final FrameLimitController controller = withRapidReset(1, 1, SECOND);
        Assertions.assertTrue(controller.acceptRapidReset(100, 99));
    }
}
