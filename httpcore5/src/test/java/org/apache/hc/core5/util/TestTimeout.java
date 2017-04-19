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

    private void checkToMicros(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toMicros(value), Timeout.of(value, timeUnit).toMicros());
    }

    private void checkToMillis(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toMillis(value), Timeout.of(value, timeUnit).toMillis());
    }

    private void checkToMinutes(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toMinutes(value), Timeout.of(value, timeUnit).toMinutes());
    }

    private void checkToNanos(final long value, final TimeUnit timeUnit) {
        Assert.assertEquals(timeUnit.toNanos(value), Timeout.of(value, timeUnit).toNanos());
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
            checkToMillis(value, timeUnit);
            checkToMicros(value, timeUnit);
            checkToNanos(value, timeUnit);
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
        Assert.assertFalse(Timeout.DISABLED.isUndefinedMilliseconds());
        Assert.assertFalse(Timeout.DISABLED.isUndefinedSeconds());
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

    @Test(expected = IllegalArgumentException.class)
    public void testNegative2() {
        test(-2);
    }

    @Test
    public void testUndefinedMilliseconds() {
        Assert.assertTrue(Timeout.UNDEFINED_MILLISECONDS.isUndefinedMilliseconds());
        Assert.assertFalse(Timeout.UNDEFINED_MILLISECONDS.isEnabled());
        Assert.assertFalse(Timeout.UNDEFINED_MILLISECONDS.isDisabled());
    }

    @Test
    public void testUndefinedSeconds() {
        Assert.assertTrue(Timeout.UNDEFINED_SECONDS.isUndefinedSeconds());
        Assert.assertFalse(Timeout.UNDEFINED_SECONDS.isEnabled());
        Assert.assertFalse(Timeout.UNDEFINED_SECONDS.isDisabled());
    }

}
