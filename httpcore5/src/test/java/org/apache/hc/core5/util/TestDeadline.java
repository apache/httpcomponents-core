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

package org.apache.hc.core5.util;

import java.text.ParseException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Deadline}.
 */
class TestDeadline {

    @Test
    void testFormat() throws ParseException {
        final Deadline deadline = Deadline.fromUnixMilliseconds(1000);
        final Deadline deadline2 = Deadline.parse(deadline.toString());
        Assertions.assertEquals(1000, deadline2.getValue());
    }

    @Test
    void testIsBefore() {
        final long nowPlusOneMin = System.currentTimeMillis() + 60000;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneMin);
        Assertions.assertTrue(deadline.isBefore(nowPlusOneMin + 1));
    }

    @Test
    void testIsExpired() {
        Assertions.assertTrue(Deadline.fromUnixMilliseconds(0).isExpired());
        Assertions.assertTrue(Deadline.fromUnixMilliseconds(1).isExpired());
        Assertions.assertFalse(Deadline.MAX_VALUE.isExpired());
        Assertions.assertTrue(Deadline.MIN_VALUE.isExpired());
    }

    @Test
    void testIsMax() {
        Assertions.assertFalse(Deadline.fromUnixMilliseconds(0).isMax());
        Assertions.assertFalse(Deadline.fromUnixMilliseconds(1000).isMax());
        Assertions.assertFalse(Deadline.MIN_VALUE.isMax());
        Assertions.assertTrue(Deadline.MAX_VALUE.isMax());
    }

    @Test
    void testIsMin() {
        Assertions.assertTrue(Deadline.fromUnixMilliseconds(0).isMin());
        Assertions.assertFalse(Deadline.fromUnixMilliseconds(1000).isMin());
        Assertions.assertFalse(Deadline.MAX_VALUE.isMin());
        Assertions.assertTrue(Deadline.MIN_VALUE.isMin());
    }

    @Test
    void testIsNotExpired() {
        Assertions.assertFalse(Deadline.fromUnixMilliseconds(0).isNotExpired());
        Assertions.assertFalse(Deadline.fromUnixMilliseconds(1).isNotExpired());
        Assertions.assertTrue(Deadline.MAX_VALUE.isNotExpired());
        Assertions.assertFalse(Deadline.MIN_VALUE.isNotExpired());
    }

    @Test
    void testMin() {
        Assertions.assertEquals(Deadline.MIN_VALUE, Deadline.MIN_VALUE.min(Deadline.MAX_VALUE));
        Assertions.assertEquals(Deadline.MIN_VALUE, Deadline.MAX_VALUE.min(Deadline.MIN_VALUE));
        //
        final Deadline deadline0 = Deadline.fromUnixMilliseconds(0);
        Assertions.assertEquals(Deadline.MIN_VALUE, deadline0.min(Deadline.MIN_VALUE));
        Assertions.assertEquals(deadline0, deadline0.min(Deadline.MAX_VALUE));
        //
        final Deadline deadline1 = Deadline.fromUnixMilliseconds(0);
        Assertions.assertEquals(Deadline.MIN_VALUE, deadline1.min(Deadline.MIN_VALUE));
        Assertions.assertEquals(deadline0, deadline1.min(Deadline.MAX_VALUE));
    }

    @Test
    void testParse() throws ParseException {
        final Deadline deadline = Deadline.parse("1969-12-31T17:00:01.000-0700");
        Assertions.assertEquals(1000, deadline.getValue());
    }

    @Test
    void testRemaining() {
        final int oneHourInMillis = 60_000 * 60;
        final long nowPlusOneHour = System.currentTimeMillis() + oneHourInMillis;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneHour);
        Assertions.assertEquals(nowPlusOneHour, deadline.getValue());
        Assertions.assertTrue(deadline.remaining() > 0);
        Assertions.assertTrue(deadline.remaining() <= oneHourInMillis);
    }

    @Test
    void testRemainingTimeValue() {
        final int oneHourInMillis = 60_000 * 60;
        final long nowPlusOneHour = System.currentTimeMillis() + oneHourInMillis;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneHour);
        Assertions.assertEquals(nowPlusOneHour, deadline.getValue());
        Assertions.assertTrue(deadline.remainingTimeValue().toNanoseconds() > 0);
        Assertions.assertTrue(deadline.remainingTimeValue().toMicroseconds() > 0);
        Assertions.assertTrue(deadline.remainingTimeValue().toMilliseconds() > 0);
    }

    @Test
    void testValue() {
        final long nowPlusOneMin = System.currentTimeMillis() + 60000;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneMin);
        Assertions.assertEquals(nowPlusOneMin, deadline.getValue());
    }

    @Test
    void testOverflowHandling() {
        final long currentTime = Long.MAX_VALUE - 5000; // Simulate close to overflow
        final TimeValue tenSeconds = TimeValue.ofMilliseconds(10000); // 10 seconds
        final Deadline deadline = Deadline.calculate(currentTime, tenSeconds);

        Assertions.assertEquals(Deadline.MAX_VALUE, deadline,
                "Overflow should result in the maximum deadline value.");
    }
}
