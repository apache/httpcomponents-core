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
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestTimeout {

    private void checkToDays(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(value, timeUnit).toDays()).isEqualTo(timeUnit.toDays(value));
    }

    private void checkToHours(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(value, timeUnit).toHours()).isEqualTo(timeUnit.toHours(value));
    }

    private void checkToMicroseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(value, timeUnit).toMicroseconds()).isEqualTo(timeUnit.toMicros(value));
    }

    private void checkToMilliseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(value, timeUnit).toMilliseconds()).isEqualTo(timeUnit.toMillis(value));
    }

    private void checkToMinutes(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(value, timeUnit).toMinutes()).isEqualTo(timeUnit.toMinutes(value));
    }

    private void checkToNanoseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(value, timeUnit).toNanoseconds()).isEqualTo(timeUnit.toNanos(value));
    }

    private void checkToSeconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(value, timeUnit).toSeconds()).isEqualTo(timeUnit.toSeconds(value));
    }

    private void test(final long value) {
        for (final TimeUnit timeUnit : TimeUnit.values()) {
            checkToDays(value, timeUnit);
            checkToHours(value, timeUnit);
            checkToMinutes(value, timeUnit);
            checkToSeconds(value, timeUnit);
            checkToMilliseconds(value, timeUnit);
            checkToMicroseconds(value, timeUnit);
            checkToNanoseconds(value, timeUnit);
        }
    }

    @Test
    void test0() {
        test(0);
    }

    @Test
    void test1() {
        test(1);
    }

    @Test
    void testDisabled() {
        Assertions.assertThat(Timeout.DISABLED.isDisabled()).isTrue();
        Assertions.assertThat(Timeout.DISABLED.isEnabled()).isFalse();
    }

    private void testFactory(final TimeUnit timeUnit) {
        Assertions.assertThat(Timeout.of(1, timeUnit).getTimeUnit()).isEqualTo(timeUnit);
    }

    @Test
    void testFactoryForDays() {
        testFactory(TimeUnit.DAYS);
    }

    @Test
    void testFactoryForDuration() {
        assertConvertion(Duration.ZERO);
        assertConvertion(Duration.ofDays(1));
        assertConvertion(Duration.ofHours(1));
        assertConvertion(Duration.ofMillis(1));
        assertConvertion(Duration.ofNanos(1));
        assertConvertion(Duration.ofSeconds(1));
        assertConvertion(Duration.ofSeconds(1, 1));
    }

    private void assertConvertion(final Duration duration) {
        Assertions.assertThat(Timeout.of(duration).toDuration()).isEqualTo(duration);
    }

    @Test
    void testFactoryForHours() {
        testFactory(TimeUnit.HOURS);
    }

    @Test
    void testFactoryForMicroseconds() {
        testFactory(TimeUnit.MICROSECONDS);
    }

    @Test
    void testFactoryForMillisseconds() {
        testFactory(TimeUnit.MILLISECONDS);
    }

    @Test
    void testFactoryForMinutes() {
        testFactory(TimeUnit.MINUTES);
    }

    @Test
    void testFactoryForNanoseconds() {
        testFactory(TimeUnit.NANOSECONDS);
    }

    @Test
    void testFactoryForSeconds() {
        testFactory(TimeUnit.SECONDS);
    }

    @Test
    void testMaxInt() {
        test(Integer.MAX_VALUE);
    }

    @Test
    void testMaxLong() {
        test(Long.MAX_VALUE);
    }

    @Test
    void testNegative1() {
        Assertions.assertThatThrownBy(() ->
                test(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        Assertions.assertThat(Timeout.ofSeconds(Long.MAX_VALUE).toString()).isEqualTo("9223372036854775807 SECONDS");
        Assertions.assertThat(Timeout.ZERO_MILLISECONDS.toString()).isEqualTo("0 MILLISECONDS");
    }

    @Test
    void testFromString() throws ParseException {
        Assertions.assertThat(Timeout.parse("9223372036854775807 SECONDS")).isEqualTo(Timeout.ofSeconds(Long.MAX_VALUE));
        Assertions.assertThat(Timeout.parse("9223372036854775807 Seconds")).isEqualTo(Timeout.ofSeconds(Long.MAX_VALUE));
        Assertions.assertThat(Timeout.parse("9223372036854775807  Seconds")).isEqualTo(Timeout.ofSeconds(Long.MAX_VALUE));
        Assertions.assertThat(Timeout.parse("9223372036854775807\tSeconds")).isEqualTo(Timeout.ofSeconds(Long.MAX_VALUE));
        Assertions.assertThat(Timeout.parse("0 MILLISECONDS")).isEqualTo(Timeout.ZERO_MILLISECONDS);
    }

}
