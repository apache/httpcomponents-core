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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests {@link Deadline}.
 */
public class TestDeadline {

    @Test
    public void testFormat() throws ParseException {
        final Deadline deadline = Deadline.fromUnixMilliseconds(1000);
        final Deadline deadline2 = Deadline.parse(deadline.toString());
        Assert.assertEquals(1000, deadline2.getValue());
    }

    @Test
    public void testIsBefore() {
        final long nowPlusOneMin = System.currentTimeMillis() + 60000;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneMin);
        Assert.assertTrue(deadline.isBefore(nowPlusOneMin + 1));
    }

    @Test
    public void testIsExpired() {
        Assert.assertTrue(Deadline.fromUnixMilliseconds(0).isExpired());
        Assert.assertTrue(Deadline.fromUnixMilliseconds(1).isExpired());
        Assert.assertFalse(Deadline.MAX_VALUE.isExpired());
        Assert.assertTrue(Deadline.MIN_VALUE.isExpired());
    }

    @Test
    public void testIsMax() {
        Assert.assertFalse(Deadline.fromUnixMilliseconds(0).isMax());
        Assert.assertFalse(Deadline.fromUnixMilliseconds(1000).isMax());
        Assert.assertFalse(Deadline.MIN_VALUE.isMax());
        Assert.assertTrue(Deadline.MAX_VALUE.isMax());
    }

    @Test
    public void testIsMin() {
        Assert.assertTrue(Deadline.fromUnixMilliseconds(0).isMin());
        Assert.assertFalse(Deadline.fromUnixMilliseconds(1000).isMin());
        Assert.assertFalse(Deadline.MAX_VALUE.isMin());
        Assert.assertTrue(Deadline.MIN_VALUE.isMin());
    }

    @Test
    public void testIsNotExpired() {
        Assert.assertFalse(Deadline.fromUnixMilliseconds(0).isNotExpired());
        Assert.assertFalse(Deadline.fromUnixMilliseconds(1).isNotExpired());
        Assert.assertTrue(Deadline.MAX_VALUE.isNotExpired());
        Assert.assertFalse(Deadline.MIN_VALUE.isNotExpired());
    }

    @Test
    public void testMin() {
        Assert.assertEquals(Deadline.MIN_VALUE, Deadline.MIN_VALUE.min(Deadline.MAX_VALUE));
        Assert.assertEquals(Deadline.MIN_VALUE, Deadline.MAX_VALUE.min(Deadline.MIN_VALUE));
        //
        final Deadline deadline0 = Deadline.fromUnixMilliseconds(0);
        Assert.assertEquals(Deadline.MIN_VALUE, deadline0.min(Deadline.MIN_VALUE));
        Assert.assertEquals(deadline0, deadline0.min(Deadline.MAX_VALUE));
        //
        final Deadline deadline1 = Deadline.fromUnixMilliseconds(0);
        Assert.assertEquals(Deadline.MIN_VALUE, deadline1.min(Deadline.MIN_VALUE));
        Assert.assertEquals(deadline0, deadline1.min(Deadline.MAX_VALUE));
    }

    @Test
    public void testParse() throws ParseException {
        final Deadline deadline = Deadline.parse("1969-12-31T17:00:01.000 MST");
        Assert.assertEquals(1000, deadline.getValue());
    }

    @Test
    public void testRemaining() {
        final int oneHourInMillis = 60_000 * 60;
        final long nowPlusOneHour = System.currentTimeMillis() + oneHourInMillis;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneHour);
        Assert.assertEquals(nowPlusOneHour, deadline.getValue());
        Assert.assertTrue(deadline.remaining() > 0);
        Assert.assertTrue(deadline.remaining() <= oneHourInMillis);
    }

    @Test
    public void testRemainingTimeValue() {
        final int oneHourInMillis = 60_000 * 60;
        final long nowPlusOneHour = System.currentTimeMillis() + oneHourInMillis;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneHour);
        Assert.assertEquals(nowPlusOneHour, deadline.getValue());
        Assert.assertTrue(deadline.remainingTimeValue().toNanoseconds() > 0);
        Assert.assertTrue(deadline.remainingTimeValue().toMicroseconds() > 0);
        Assert.assertTrue(deadline.remainingTimeValue().toMilliseconds() > 0);
    }

    @Test
    public void testValue() {
        final long nowPlusOneMin = System.currentTimeMillis() + 60000;
        final Deadline deadline = Deadline.fromUnixMilliseconds(nowPlusOneMin);
        Assert.assertEquals(nowPlusOneMin, deadline.getValue());
    }
}
