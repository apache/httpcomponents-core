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

import static org.hamcrest.MatcherAssert.assertThat;

import java.text.ParseException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestTimeValue {

    private void checkToDays(final long value, final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit.toDays(value), TimeValue.of(value, timeUnit).toDays());
    }

    private void checkToHours(final long value, final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit.toHours(value), TimeValue.of(value, timeUnit).toHours());
    }

    private void checkToMicroseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit.toMicros(value), TimeValue.of(value, timeUnit).toMicroseconds());
    }

    private void checkToMilliseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit.toMillis(value), TimeValue.of(value, timeUnit).toMilliseconds());
    }

    private void checkToMinutes(final long value, final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit.toMinutes(value), TimeValue.of(value, timeUnit).toMinutes());
    }

    private void checkToNanoseconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit.toNanos(value), TimeValue.of(value, timeUnit).toNanoseconds());
    }

    private void checkToSeconds(final long value, final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit.toSeconds(value), TimeValue.of(value, timeUnit).toSeconds());
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
    public void test0() {
        test(0);
    }

    @Test
    public void test1() {
        test(1);
    }

    @Test
    public void testConvert() {
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).convert(TimeUnit.DAYS));
        Assertions.assertEquals(1000, TimeValue.ofSeconds(1).convert(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDivide() {
        // nominator is 0, result should be 0.
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toDays());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toHours());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toMicroseconds());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toMilliseconds());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toMinutes());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toNanoseconds());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toSeconds());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toMillisecondsIntBound());
        Assertions.assertEquals(0, TimeValue.ofMilliseconds(0).divide(2).toSecondsIntBound());
        //
        Assertions.assertEquals(50, TimeValue.ofMilliseconds(100).divide(2).toMilliseconds());
        Assertions.assertEquals(0, TimeValue.ofMinutes(1).divide(2).toSeconds());
        Assertions.assertEquals(30, TimeValue.ofMinutes(1).divide(2, TimeUnit.SECONDS).toSeconds());
        Assertions.assertEquals(30000, TimeValue.ofMinutes(1).divide(2, TimeUnit.MILLISECONDS).toMilliseconds());
    }

    @Test
    public void testDivideBy0() {
        Assertions.assertThrows(ArithmeticException.class, () ->
                TimeValue.ofMilliseconds(0).divide(0));
    }

    private void testFactory(final TimeUnit timeUnit) {
        Assertions.assertEquals(timeUnit, TimeValue.of(1, timeUnit).getTimeUnit());
        //
        final Duration duration = Duration.of(1, TimeValue.toChronoUnit(timeUnit));
        assertConvertion(duration);
    }

    @Test
    public void testFactoryForDays() {
        testFactory(TimeUnit.DAYS);
    }

    @Test
    public void testFactoryForDuration() {
        assertConvertion(Duration.ZERO);
        assertConvertion(Duration.ofDays(1));
        assertConvertion(Duration.ofHours(1));
        assertConvertion(Duration.ofMillis(1));
        assertConvertion(Duration.ofNanos(1));
        assertConvertion(Duration.ofSeconds(1));
        assertConvertion(Duration.ofSeconds(1, 1));
    }

    private void assertConvertion(final Duration duration) {
        Assertions.assertEquals(duration, TimeValue.of(duration).toDuration());
    }

    @Test
    public void testFactoryForHours() {
        testFactory(TimeUnit.HOURS);
    }

    @Test
    public void testFactoryForMicroseconds() {
        testFactory(TimeUnit.MICROSECONDS);
    }

    @Test
    public void testFactoryForMilliseconds() {
        testFactory(TimeUnit.MILLISECONDS);
    }

    @Test
    public void testFactoryForMinutes() {
        testFactory(TimeUnit.MINUTES);
    }

    @Test
    public void testFactoryForNanoseconds() {
        testFactory(TimeUnit.NANOSECONDS);
    }

    @Test
    public void testFactoryForSeconds() {
        testFactory(TimeUnit.SECONDS);
    }

    @Test
    public void testMin() {
        final TimeValue nanos1 = TimeValue.ofNanoseconds(1);
        final TimeValue micros1 = TimeValue.ofMicroseconds(1);
        final TimeValue millis1 = TimeValue.ofMilliseconds(1);
        final TimeValue seconds1 = TimeValue.ofSeconds(1);
        final TimeValue minutes1 = TimeValue.ofMinutes(1);
        final TimeValue hours1 = TimeValue.ofHours(1);
        final TimeValue days1 = TimeValue.ofDays(1);
        //
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.ZERO_MILLISECONDS.min(nanos1));
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.ZERO_MILLISECONDS.min(micros1));
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.ZERO_MILLISECONDS.min(millis1));
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.ZERO_MILLISECONDS.min(seconds1));
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.ZERO_MILLISECONDS.min(minutes1));
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.ZERO_MILLISECONDS.min(hours1));
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.ZERO_MILLISECONDS.min(days1));
        //
        Assertions.assertEquals(nanos1, nanos1.min(nanos1));
        Assertions.assertEquals(nanos1, nanos1.min(micros1));
        Assertions.assertEquals(nanos1, nanos1.min(millis1));
        Assertions.assertEquals(nanos1, nanos1.min(seconds1));
        Assertions.assertEquals(nanos1, nanos1.min(minutes1));
        Assertions.assertEquals(nanos1, nanos1.min(hours1));
        Assertions.assertEquals(nanos1, nanos1.min(days1));
        //
        Assertions.assertEquals(nanos1, micros1.min(nanos1));
        Assertions.assertEquals(micros1, micros1.min(micros1));
        Assertions.assertEquals(micros1, micros1.min(millis1));
        Assertions.assertEquals(micros1, micros1.min(seconds1));
        Assertions.assertEquals(micros1, micros1.min(minutes1));
        Assertions.assertEquals(micros1, micros1.min(hours1));
        Assertions.assertEquals(micros1, micros1.min(days1));
        //
        Assertions.assertEquals(nanos1, millis1.min(nanos1));
        Assertions.assertEquals(micros1, millis1.min(micros1));
        Assertions.assertEquals(millis1, millis1.min(millis1));
        Assertions.assertEquals(millis1, millis1.min(seconds1));
        Assertions.assertEquals(millis1, millis1.min(minutes1));
        Assertions.assertEquals(millis1, millis1.min(hours1));
        Assertions.assertEquals(millis1, millis1.min(days1));
        //
        Assertions.assertEquals(nanos1, seconds1.min(nanos1));
        Assertions.assertEquals(micros1, seconds1.min(micros1));
        Assertions.assertEquals(millis1, seconds1.min(millis1));
        Assertions.assertEquals(seconds1, seconds1.min(seconds1));
        Assertions.assertEquals(seconds1, seconds1.min(minutes1));
        Assertions.assertEquals(seconds1, seconds1.min(hours1));
        Assertions.assertEquals(seconds1, seconds1.min(days1));
        //
        Assertions.assertEquals(nanos1, minutes1.min(nanos1));
        Assertions.assertEquals(micros1, minutes1.min(micros1));
        Assertions.assertEquals(millis1, minutes1.min(millis1));
        Assertions.assertEquals(seconds1, minutes1.min(seconds1));
        Assertions.assertEquals(minutes1, minutes1.min(minutes1));
        Assertions.assertEquals(minutes1, minutes1.min(hours1));
        Assertions.assertEquals(minutes1, minutes1.min(days1));
        //
        Assertions.assertEquals(nanos1, hours1.min(nanos1));
        Assertions.assertEquals(micros1, hours1.min(micros1));
        Assertions.assertEquals(millis1, hours1.min(millis1));
        Assertions.assertEquals(seconds1, hours1.min(seconds1));
        Assertions.assertEquals(minutes1, hours1.min(minutes1));
        Assertions.assertEquals(hours1, hours1.min(hours1));
        Assertions.assertEquals(hours1, hours1.min(days1));
        //
        Assertions.assertEquals(nanos1, days1.min(nanos1));
        Assertions.assertEquals(micros1, days1.min(micros1));
        Assertions.assertEquals(millis1, days1.min(millis1));
        Assertions.assertEquals(seconds1, days1.min(seconds1));
        Assertions.assertEquals(minutes1, days1.min(minutes1));
        Assertions.assertEquals(hours1, days1.min(hours1));
        Assertions.assertEquals(days1, days1.min(days1));
    }

    @Test
    public void testMaxInt() {
        test(Integer.MAX_VALUE);
    }

    @Test
    public void testMaxLong() {
        test(Long.MAX_VALUE);
    }

    @Test
    public void testNegative1() {
        test(-1);
    }

    @Test
    public void testToString() {
        Assertions.assertEquals("9223372036854775807 SECONDS", TimeValue.ofSeconds(Long.MAX_VALUE).toString());
        Assertions.assertEquals("0 MILLISECONDS", TimeValue.ZERO_MILLISECONDS.toString());
    }

    @Test
    public void testFromString() throws ParseException {
        final TimeValue maxSeconds = TimeValue.ofSeconds(Long.MAX_VALUE);
        Assertions.assertEquals(maxSeconds, TimeValue.parse("9223372036854775807 SECONDS"));
        Assertions.assertEquals(maxSeconds, TimeValue.parse("9223372036854775807 SECONDS"));
        Assertions.assertEquals(maxSeconds, TimeValue.parse(" 9223372036854775807 SECONDS "));
        Assertions.assertEquals(maxSeconds, TimeValue.parse("9223372036854775807 Seconds"));
        Assertions.assertEquals(maxSeconds, TimeValue.parse("9223372036854775807  Seconds"));
        Assertions.assertEquals(maxSeconds, TimeValue.parse("9223372036854775807\tSeconds"));
        Assertions.assertEquals(TimeValue.ZERO_MILLISECONDS, TimeValue.parse("0 MILLISECONDS"));
        Assertions.assertEquals(TimeValue.ofMilliseconds(1), TimeValue.parse("1 MILLISECOND"));
    }

    @Test
    public void testToDuration() throws ParseException {
        Assertions.assertEquals(Long.MAX_VALUE, TimeValue.parse("9223372036854775807 SECONDS").toDuration().getSeconds());
    }

    @Test
    public void testEqualsAndHashCode() {
        final TimeValue tv1 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv2 = TimeValue.ofMilliseconds(1001L);
        final TimeValue tv3 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv4 = TimeValue.ofSeconds(1L);
        final TimeValue tv5 = TimeValue.ofSeconds(1000L);

        assertThat(tv1.equals(tv1), CoreMatchers.equalTo(true));
        assertThat(tv1.equals(null), CoreMatchers.equalTo(false));
        assertThat(tv1.equals(tv2), CoreMatchers.equalTo(false));
        assertThat(tv1.equals(tv3), CoreMatchers.equalTo(true));
        assertThat(tv1.equals(tv4), CoreMatchers.equalTo(true));
        assertThat(tv4.equals(tv1), CoreMatchers.equalTo(true));
        assertThat(tv1.equals(tv5), CoreMatchers.equalTo(false));

        assertThat(tv1.hashCode() == tv2.hashCode(), CoreMatchers.equalTo(false));
        assertThat(tv1.hashCode() == tv3.hashCode(), CoreMatchers.equalTo(true));
        assertThat(tv1.hashCode() == tv4.hashCode(), CoreMatchers.equalTo(true));
        assertThat(tv4.hashCode() == tv1.hashCode(), CoreMatchers.equalTo(true));
        assertThat(tv1.hashCode() == tv5.hashCode(), CoreMatchers.equalTo(false));
    }

    @Test
    public void testCompareTo() {
        final TimeValue tv1 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv2 = TimeValue.ofMilliseconds(1001L);
        final TimeValue tv3 = TimeValue.ofMilliseconds(1000L);
        final TimeValue tv4 = TimeValue.ofSeconds(1L);
        final TimeValue tv5 = TimeValue.ofSeconds(60L);
        final TimeValue tv6 = TimeValue.ofMinutes(1L);

        assertThat(tv1.compareTo(tv1) == 0, CoreMatchers.equalTo(true));
        assertThat(tv1.compareTo(tv2) < 0, CoreMatchers.equalTo(true));
        assertThat(tv1.compareTo(tv3) == 0, CoreMatchers.equalTo(true));
        assertThat(tv1.compareTo(tv4) == 0, CoreMatchers.equalTo(true));
        assertThat(tv1.compareTo(tv5) < 0, CoreMatchers.equalTo(true));
        assertThat(tv6.compareTo(tv5) == 0, CoreMatchers.equalTo(true));
        assertThat(tv6.compareTo(tv4) > 0, CoreMatchers.equalTo(true));
        Assertions.assertThrows(NullPointerException.class, () -> tv1.compareTo(null));
    }

}
