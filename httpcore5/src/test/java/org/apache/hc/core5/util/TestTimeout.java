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

import org.junit.Assert;
import org.junit.Test;

public class TestTimeout {

    private void checkToDays(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toDays(value), Timeout.of(value, timeUnit).toDays());
    }

    private void checkToHours(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toHours(value), Timeout.of(value, timeUnit).toHours());
    }

    private void checkToMicroseconds(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toMicros(value), Timeout.of(value, timeUnit).toMicroseconds());
    }

    private void checkToMilliseconds(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toMillis(value), Timeout.of(value, timeUnit).toMilliseconds());
    }

    private void checkToMinutes(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toMinutes(value), Timeout.of(value, timeUnit).toMinutes());
    }

    private void checkToNanoseconds(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toNanos(value), Timeout.of(value, timeUnit).toNanoseconds());
    }

    private void checkToSeconds(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toSeconds(value), Timeout.of(value, timeUnit).toSeconds());
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
    public void testDisabled() {
        Assert.assertTrue(Timeout.DISABLED.isDisabled());
        Assert.assertFalse(Timeout.DISABLED.isEnabled());
    }

    private void testFactory(final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit, Timeout.of(1, timeUnit).getTimeUnit());
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
        Assert.assertEquals(duration, Timeout.of(duration).toDuration());
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
    public void testFactoryForMillisseconds() {
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
    public void testMaxInt() {
        test(Integer.MAX_VALUE);
    }

    @Test
    public void testMaxLong() {
        test(Long.MAX_VALUE);
    }

    @Test
    public void testNegative1() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                test(-1));
    }

    @Test
    public void testToString() {
        Assert.assertEquals("9223372036854775807 SECONDS", Timeout.ofSeconds(Long.MAX_VALUE).toString());
        Assert.assertEquals("0 MILLISECONDS", Timeout.ZERO_MILLISECONDS.toString());
    }

    @Test
    public void testFromString() throws ParseException {
        Assert.assertEquals(Timeout.ofSeconds(Long.MAX_VALUE), Timeout.parse("9223372036854775807 SECONDS"));
        Assert.assertEquals(Timeout.ofSeconds(Long.MAX_VALUE), Timeout.parse("9223372036854775807 Seconds"));
        Assert.assertEquals(Timeout.ofSeconds(Long.MAX_VALUE), Timeout.parse("9223372036854775807  Seconds"));
        Assert.assertEquals(Timeout.ofSeconds(Long.MAX_VALUE), Timeout.parse("9223372036854775807\tSeconds"));
        Assert.assertEquals(Timeout.ZERO_MILLISECONDS, Timeout.parse("0 MILLISECONDS"));
    }

}
