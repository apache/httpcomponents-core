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

class TestTimeValue {

    private void checkToDays(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(value, timeUnit).toDays()).isEqualTo(timeUnit.toDays(value));
    }

    private void checkToHours(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(value, timeUnit).toHours()).isEqualTo(timeUnit.toHours(value));
    }

    private void checkToMicroseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(value, timeUnit).toMicroseconds()).isEqualTo(timeUnit.toMicros(value));
    }

    private void checkToMilliseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(value, timeUnit).toMilliseconds()).isEqualTo(timeUnit.toMillis(value));
    }

    private void checkToMinutes(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(value, timeUnit).toMinutes()).isEqualTo(timeUnit.toMinutes(value));
    }

    private void checkToNanoseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(value, timeUnit).toNanoseconds()).isEqualTo(timeUnit.toNanos(value));
    }

    private void checkToSeconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(value, timeUnit).toSeconds()).isEqualTo(timeUnit.toSeconds(value));
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
    void testConvert() {
        Assertions.assertThat(TimeValue.ofMilliseconds(0).convert(TimeUnit.DAYS)).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofSeconds(1).convert(TimeUnit.MILLISECONDS)).isEqualTo(1000);
    }

    @Test
    void testDivide() {
        // nominator is 0, result should be 0.
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toDays()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toHours()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toMicroseconds()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toMilliseconds()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toMinutes()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toNanoseconds()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toSeconds()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toMillisecondsIntBound()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMilliseconds(0).divide(2).toSecondsIntBound()).isEqualTo(0);
        //
        Assertions.assertThat(TimeValue.ofMilliseconds(100).divide(2).toMilliseconds()).isEqualTo(50);
        Assertions.assertThat(TimeValue.ofMinutes(1).divide(2).toSeconds()).isEqualTo(0);
        Assertions.assertThat(TimeValue.ofMinutes(1).divide(2, TimeUnit.SECONDS).toSeconds()).isEqualTo(30);
        Assertions.assertThat(TimeValue.ofMinutes(1).divide(2, TimeUnit.MILLISECONDS).toMilliseconds()).isEqualTo(30000);
    }

    @Test
    void testDivideBy0() {
        Assertions.assertThatThrownBy(() ->
                TimeValue.ofMilliseconds(0).divide(0)).isInstanceOf(ArithmeticException.class);
    }

    private void testFactory(final TimeUnit timeUnit) {
        Assertions.assertThat(TimeValue.of(1, timeUnit).getTimeUnit()).isEqualTo(timeUnit);
        //
        final Duration duration = Duration.of(1, TimeValue.toChronoUnit(timeUnit));
        assertConvertion(duration);
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
        Assertions.assertThat(TimeValue.of(duration).toDuration()).isEqualTo(duration);
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
    void testFactoryForMilliseconds() {
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
    void testMin() {
        final TimeValue nanos1 = TimeValue.ofNanoseconds(1);
        final TimeValue micros1 = TimeValue.ofMicroseconds(1);
        final TimeValue millis1 = TimeValue.ofMilliseconds(1);
        final TimeValue seconds1 = TimeValue.ofSeconds(1);
        final TimeValue minutes1 = TimeValue.ofMinutes(1);
        final TimeValue hours1 = TimeValue.ofHours(1);
        final TimeValue days1 = TimeValue.ofDays(1);
        //
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.min(nanos1)).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.min(micros1)).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.min(millis1)).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.min(seconds1)).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.min(minutes1)).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.min(hours1)).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.min(days1)).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        //
        Assertions.assertThat(nanos1.min(nanos1)).isEqualTo(nanos1);
        Assertions.assertThat(nanos1.min(micros1)).isEqualTo(nanos1);
        Assertions.assertThat(nanos1.min(millis1)).isEqualTo(nanos1);
        Assertions.assertThat(nanos1.min(seconds1)).isEqualTo(nanos1);
        Assertions.assertThat(nanos1.min(minutes1)).isEqualTo(nanos1);
        Assertions.assertThat(nanos1.min(hours1)).isEqualTo(nanos1);
        Assertions.assertThat(nanos1.min(days1)).isEqualTo(nanos1);
        //
        Assertions.assertThat(micros1.min(nanos1)).isEqualTo(nanos1);
        Assertions.assertThat(micros1.min(micros1)).isEqualTo(micros1);
        Assertions.assertThat(micros1.min(millis1)).isEqualTo(micros1);
        Assertions.assertThat(micros1.min(seconds1)).isEqualTo(micros1);
        Assertions.assertThat(micros1.min(minutes1)).isEqualTo(micros1);
        Assertions.assertThat(micros1.min(hours1)).isEqualTo(micros1);
        Assertions.assertThat(micros1.min(days1)).isEqualTo(micros1);
        //
        Assertions.assertThat(millis1.min(nanos1)).isEqualTo(nanos1);
        Assertions.assertThat(millis1.min(micros1)).isEqualTo(micros1);
        Assertions.assertThat(millis1.min(millis1)).isEqualTo(millis1);
        Assertions.assertThat(millis1.min(seconds1)).isEqualTo(millis1);
        Assertions.assertThat(millis1.min(minutes1)).isEqualTo(millis1);
        Assertions.assertThat(millis1.min(hours1)).isEqualTo(millis1);
        Assertions.assertThat(millis1.min(days1)).isEqualTo(millis1);
        //
        Assertions.assertThat(seconds1.min(nanos1)).isEqualTo(nanos1);
        Assertions.assertThat(seconds1.min(micros1)).isEqualTo(micros1);
        Assertions.assertThat(seconds1.min(millis1)).isEqualTo(millis1);
        Assertions.assertThat(seconds1.min(seconds1)).isEqualTo(seconds1);
        Assertions.assertThat(seconds1.min(minutes1)).isEqualTo(seconds1);
        Assertions.assertThat(seconds1.min(hours1)).isEqualTo(seconds1);
        Assertions.assertThat(seconds1.min(days1)).isEqualTo(seconds1);
        //
        Assertions.assertThat(minutes1.min(nanos1)).isEqualTo(nanos1);
        Assertions.assertThat(minutes1.min(micros1)).isEqualTo(micros1);
        Assertions.assertThat(minutes1.min(millis1)).isEqualTo(millis1);
        Assertions.assertThat(minutes1.min(seconds1)).isEqualTo(seconds1);
        Assertions.assertThat(minutes1.min(minutes1)).isEqualTo(minutes1);
        Assertions.assertThat(minutes1.min(hours1)).isEqualTo(minutes1);
        Assertions.assertThat(minutes1.min(days1)).isEqualTo(minutes1);
        //
        Assertions.assertThat(hours1.min(nanos1)).isEqualTo(nanos1);
        Assertions.assertThat(hours1.min(micros1)).isEqualTo(micros1);
        Assertions.assertThat(hours1.min(millis1)).isEqualTo(millis1);
        Assertions.assertThat(hours1.min(seconds1)).isEqualTo(seconds1);
        Assertions.assertThat(hours1.min(minutes1)).isEqualTo(minutes1);
        Assertions.assertThat(hours1.min(hours1)).isEqualTo(hours1);
        Assertions.assertThat(hours1.min(days1)).isEqualTo(hours1);
        //
        Assertions.assertThat(days1.min(nanos1)).isEqualTo(nanos1);
        Assertions.assertThat(days1.min(micros1)).isEqualTo(micros1);
        Assertions.assertThat(days1.min(millis1)).isEqualTo(millis1);
        Assertions.assertThat(days1.min(seconds1)).isEqualTo(seconds1);
        Assertions.assertThat(days1.min(minutes1)).isEqualTo(minutes1);
        Assertions.assertThat(days1.min(hours1)).isEqualTo(hours1);
        Assertions.assertThat(days1.min(days1)).isEqualTo(days1);
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
        test(-1);
    }

    @Test
    void testToString() {
        Assertions.assertThat(TimeValue.ofSeconds(Long.MAX_VALUE).toString()).isEqualTo("9223372036854775807 SECONDS");
        Assertions.assertThat(TimeValue.ZERO_MILLISECONDS.toString()).isEqualTo("0 MILLISECONDS");
    }

    @Test
    void testFromString() throws ParseException {
        final TimeValue maxSeconds = TimeValue.ofSeconds(Long.MAX_VALUE);
        Assertions.assertThat(TimeValue.parse("9223372036854775807 SECONDS")).isEqualTo(maxSeconds);
        Assertions.assertThat(TimeValue.parse("9223372036854775807 SECONDS")).isEqualTo(maxSeconds);
        Assertions.assertThat(TimeValue.parse(" 9223372036854775807 SECONDS ")).isEqualTo(maxSeconds);
        Assertions.assertThat(TimeValue.parse("9223372036854775807 Seconds")).isEqualTo(maxSeconds);
        Assertions.assertThat(TimeValue.parse("9223372036854775807  Seconds")).isEqualTo(maxSeconds);
        Assertions.assertThat(TimeValue.parse("9223372036854775807\tSeconds")).isEqualTo(maxSeconds);
        Assertions.assertThat(TimeValue.parse("0 MILLISECONDS")).isEqualTo(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(TimeValue.parse("1 MILLISECOND")).isEqualTo(TimeValue.ofMilliseconds(1));
    }

    @Test
    void testToDuration() throws ParseException {
        Assertions.assertThat(TimeValue.parse("9223372036854775807 SECONDS").toDuration().getSeconds()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void testEqualsAndHashCode() {
        final TimeValue tv1 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv2 = TimeValue.ofMilliseconds(1001L);
        final TimeValue tv3 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv4 = TimeValue.ofSeconds(1L);
        final TimeValue tv5 = TimeValue.ofSeconds(1000L);

        Assertions.assertThat(tv1).isEqualTo(tv1);
        Assertions.assertThat(tv1).isNotEqualTo(null);
        Assertions.assertThat(tv2).isNotEqualTo(tv1);
        Assertions.assertThat(tv3).isEqualTo(tv1);
        Assertions.assertThat(tv4).isEqualTo(tv1);
        Assertions.assertThat(tv1).isEqualTo(tv4);
        Assertions.assertThat(tv5).isNotEqualTo(tv1);

        Assertions.assertThat(tv2.hashCode()).isNotEqualTo(tv1.hashCode());
        Assertions.assertThat(tv3.hashCode()).isEqualTo(tv1.hashCode());
        Assertions.assertThat(tv4.hashCode()).isEqualTo(tv1.hashCode());
        Assertions.assertThat(tv1.hashCode()).isEqualTo(tv4.hashCode());
        Assertions.assertThat(tv5.hashCode()).isNotEqualTo(tv1.hashCode());
    }

    @Test
    void testCompareTo() {
        final TimeValue tv1 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv2 = TimeValue.ofMilliseconds(1001L);
        final TimeValue tv3 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv4 = TimeValue.ofSeconds(1L);
        final TimeValue tv5 = TimeValue.ofSeconds(60L);
        final TimeValue tv6 = TimeValue.ofMinutes(1L);

        Assertions.assertThat(tv1.compareTo(tv1) == 0).isTrue();
        Assertions.assertThat(tv1.compareTo(tv2) < 0).isTrue();
        Assertions.assertThat(tv1.compareTo(tv3) == 0).isTrue();
        Assertions.assertThat(tv1.compareTo(tv4) == 0).isTrue();
        Assertions.assertThat(tv1.compareTo(tv5) < 0).isTrue();
        Assertions.assertThat(tv6.compareTo(tv5) == 0).isTrue();
        Assertions.assertThat(tv6.compareTo(tv4) > 0).isTrue();
        Assertions.assertThatThrownBy(() ->
                        tv1.compareTo(null))
                .isInstanceOf(NullPointerException.class);
    }

}
