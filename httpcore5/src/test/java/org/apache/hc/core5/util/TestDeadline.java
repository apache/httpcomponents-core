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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Deadline}.
 */
class TestDeadline {

    @Test
    void testFormat() throws ParseException {
        final Deadline deadline = Deadline.fromUnixMilliseconds(1000);
        final Deadline deadline2 = Deadline.parse(deadline.toString());
        Assertions.assertThat(deadline2.getValue()).isEqualTo(1000);
    }

    @Test
    void testIsBefore() {
        final long nowPlusOneMin = System.currentTimeMillis() + 60000;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneMin);
        Assertions.assertThat(deadline.isBefore(nowPlusOneMin + 1)).isTrue();
    }

    @Test
    void testIsExpired() {
        Assertions.assertThat(Deadline.fromUnixMilliseconds(0).isExpired()).isTrue();
        Assertions.assertThat(Deadline.fromUnixMilliseconds(1).isExpired()).isTrue();
        Assertions.assertThat(Deadline.MAX_VALUE.isExpired()).isFalse();
        Assertions.assertThat(Deadline.MIN_VALUE.isExpired()).isTrue();
    }

    @Test
    void testIsMax() {
        Assertions.assertThat(Deadline.fromUnixMilliseconds(0).isMax()).isFalse();
        Assertions.assertThat(Deadline.fromUnixMilliseconds(1000).isMax()).isFalse();
        Assertions.assertThat(Deadline.MIN_VALUE.isMax()).isFalse();
        Assertions.assertThat(Deadline.MAX_VALUE.isMax()).isTrue();
    }

    @Test
    void testIsMin() {
        Assertions.assertThat(Deadline.fromUnixMilliseconds(0).isMin()).isTrue();
        Assertions.assertThat(Deadline.fromUnixMilliseconds(1000).isMin()).isFalse();
        Assertions.assertThat(Deadline.MAX_VALUE.isMin()).isFalse();
        Assertions.assertThat(Deadline.MIN_VALUE.isMin()).isTrue();
    }

    @Test
    void testIsNotExpired() {
        Assertions.assertThat(Deadline.fromUnixMilliseconds(0).isNotExpired()).isFalse();
        Assertions.assertThat(Deadline.fromUnixMilliseconds(1).isNotExpired()).isFalse();
        Assertions.assertThat(Deadline.MAX_VALUE.isNotExpired()).isTrue();
        Assertions.assertThat(Deadline.MIN_VALUE.isNotExpired()).isFalse();
    }

    @Test
    void testMin() {
        Assertions.assertThat(Deadline.MIN_VALUE.min(Deadline.MAX_VALUE)).isEqualTo(Deadline.MIN_VALUE);
        Assertions.assertThat(Deadline.MAX_VALUE.min(Deadline.MIN_VALUE)).isEqualTo(Deadline.MIN_VALUE);
        //
        final Deadline deadline0 = Deadline.fromUnixMilliseconds(0);
        Assertions.assertThat(deadline0.min(Deadline.MIN_VALUE)).isEqualTo(Deadline.MIN_VALUE);
        Assertions.assertThat(deadline0.min(Deadline.MAX_VALUE)).isEqualTo(deadline0);
        //
        final Deadline deadline1 = Deadline.fromUnixMilliseconds(0);
        Assertions.assertThat(deadline1.min(Deadline.MIN_VALUE)).isEqualTo(Deadline.MIN_VALUE);
        Assertions.assertThat(deadline1.min(Deadline.MAX_VALUE)).isEqualTo(deadline0);
    }

    @Test
    void testParse() throws ParseException {
        final Deadline deadline = Deadline.parse("1969-12-31T17:00:01.000-0700");
        Assertions.assertThat(deadline.getValue()).isEqualTo(1000);
    }

    @Test
    void testRemaining() {
        final int oneHourInMillis = 60_000 * 60;
        final long nowPlusOneHour = System.currentTimeMillis() + oneHourInMillis;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneHour);
        Assertions.assertThat(deadline.getValue()).isEqualTo(nowPlusOneHour);
        Assertions.assertThat(deadline.remaining() > 0).isTrue();
        Assertions.assertThat(deadline.remaining() <= oneHourInMillis).isTrue();
    }

    @Test
    void testRemainingTimeValue() {
        final int oneHourInMillis = 60_000 * 60;
        final long nowPlusOneHour = System.currentTimeMillis() + oneHourInMillis;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneHour);
        Assertions.assertThat(deadline.getValue()).isEqualTo(nowPlusOneHour);
        Assertions.assertThat(deadline.remainingTimeValue().toNanoseconds() > 0).isTrue();
        Assertions.assertThat(deadline.remainingTimeValue().toMicroseconds() > 0).isTrue();
        Assertions.assertThat(deadline.remainingTimeValue().toMilliseconds() > 0).isTrue();
    }

    @Test
    void testValue() {
        final long nowPlusOneMin = System.currentTimeMillis() + 60000;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneMin);
        Assertions.assertThat(deadline.getValue()).isEqualTo(nowPlusOneMin);
    }

    @Test
    void testOverflowHandling() {
        final long currentTime = Long.MAX_VALUE - 5000; // Simulate close to overflow
        final TimeValue tenSeconds = TimeValue.ofMilliseconds(10000); // 10 seconds
        final Deadline deadline = Deadline.calculate(currentTime, tenSeconds);

        Assertions.assertThat(deadline).withFailMessage("Overflow should result in the maximum deadline value.").isEqualTo(Deadline.MAX_VALUE);
    }
}
