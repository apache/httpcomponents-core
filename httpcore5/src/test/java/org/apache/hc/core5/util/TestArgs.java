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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Args}.
 */
public class TestArgs {

    @Test
    public void testArgCheckPass() {
        Args.check(true, "All is well");
    }

    @Test
    public void testArgCheckFail() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.check(false, "Oopsie"));
    }

    @Test
    public void testArgNotNullPass() {
        final String stuff = "stuff";
        Assert.assertSame(stuff, Args.notNull(stuff, "Stuff"));
    }

    @Test
    public void testArgNotNullFail() {
        Assert.assertThrows(NullPointerException.class, () ->
                Args.notNull(null, "Stuff"));
    }

    @Test
    public void testArgNotEmptyPass() {
        final String stuff = "stuff";
        Assert.assertSame(stuff, Args.notEmpty(stuff, "Stuff"));
    }

    @Test
    public void testArgNotEmptyFail1() {
        Assert.assertThrows(NullPointerException.class, () ->
                Args.notEmpty((String) null, "Stuff"));
    }

    @Test
    public void testArgNotEmptyFail2() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.notEmpty("", "Stuff"));
    }

    @Test
    public void testArgNotBlankFail1() {
        Assert.assertThrows(NullPointerException.class, () ->
                Args.notBlank((String) null, "Stuff"));
    }

    @Test
    public void testArgNotBlankFail2() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.notBlank("", "Stuff"));
    }

    @Test
    public void testArgNotBlankFail3() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.notBlank(" \t \n\r", "Stuff"));
    }

    @Test
    public void testArgCollectionNotEmptyPass() {
        final List<String> list = Collections.singletonList("stuff");
        Assert.assertSame(list, Args.notEmpty(list, "List"));
    }

    @Test
    public void testArgCollectionNotEmptyFail1() {
        Assert.assertThrows(NullPointerException.class, () ->
                Args.notEmpty((List<?>) null, "List"));
    }

    @Test
    public void testArgCollectionNotEmptyFail2() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.notEmpty(Collections.emptyList(), "List"));
    }

    @Test
    public void testPositiveIntPass() {
        Assert.assertEquals(1, Args.positive(1, "Number"));
    }

    @Test
    public void testPositiveIntFail1() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.positive(-1, "Number"));
    }

    @Test
    public void testPositiveIntFail2() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.positive(0, "Number"));
    }

    @Test
    public void testPositiveLongPass() {
        Assert.assertEquals(1L, Args.positive(1L, "Number"));
    }

    @Test
    public void testPositiveTimeValuePass() throws ParseException {
        final Timeout timeout = Timeout.parse("1200 MILLISECONDS");
        Assert.assertEquals(timeout, Args.positive(timeout, "No Error"));
    }
    @Test
    public void testPositiveLongFail1() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.positive(-1L, "Number"));
    }

    @Test
    public void testPositiveLongFail2() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.positive(0L, "Number"));
    }

    @Test
    public void testNotNegativeIntPass1() {
        Assert.assertEquals(1, Args.notNegative(1, "Number"));
    }

    @Test
    public void testNotNegativeIntPass2() {
        Assert.assertEquals(0, Args.notNegative(0, "Number"));
    }

    @Test
    public void testNotNegativeIntFail1() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.notNegative(-1, "Number"));
    }

    @Test
    public void testNotNegativeLongPass1() {
        Assert.assertEquals(1L, Args.notNegative(1L, "Number"));
    }

    @Test
    public void testNotNegativeLongPass2() {
        Assert.assertEquals(0L, Args.notNegative(0L, "Number"));
    }

    @Test
    public void testNotNegativeLongFail1() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.notNegative(-1L, "Number"));
    }

    @Test
    public void testIntSmallestRangeOK() {
        Args.checkRange(0, 0, 0, "Number");
    }

    @Test
    public void testIntSmallestRangeFailLow() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(-1, 0, 0, "Number"));
    }

    @Test
    public void testIntRangeFailLow() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(-101, -100, 100, "Number"));
    }

    @Test
    public void testIntRangeFailHigh() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(101, -100, 100, "Number"));
    }

    @Test
    public void testIntSmallestRangeFailHigh() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(1, 0, 0, "Number"));
    }

    @Test
    public void testIntFullRangeOK() {
        Args.checkRange(0, Integer.MIN_VALUE, Integer.MAX_VALUE, "Number");
    }

    @Test
    public void testLongSmallestRangeOK() {
        Args.checkRange(0L, 0L, 0L, "Number");
    }

    @Test
    public void testLongSmallestRangeFailLow() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(-1L, 0L, 0L, "Number"));
    }

    @Test
    public void testLongRangeFailLow() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(-101L, -100L, 100L, "Number"));
    }

    @Test
    public void testLongRangeFailHigh() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(101L, -100L, 100L, "Number"));
    }

    @Test
    public void testLongSmallestRangeFailHigh() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.checkRange(1L, 0L, 0L, "Number"));
    }

    @Test
    public void testLongFullRangeOK() {
        Args.checkRange(0L, Long.MIN_VALUE, Long.MAX_VALUE, "Number");
    }

    @Test
    public void testIsEmpty() {

        final String[] NON_EMPTY_ARRAY = new String[] { "ABG", "NML", };

        final List<String> NON_EMPTY_LIST = Arrays.asList(NON_EMPTY_ARRAY);

        final Set<String> NON_EMPTY_SET = new HashSet<>(NON_EMPTY_LIST);

        final Map<String, String> NON_EMPTY_MAP = new HashMap<>();
        NON_EMPTY_MAP.put("ABG", "MNL");

        Assert.assertTrue(Args.isEmpty(null));
        Assert.assertTrue(Args.isEmpty(""));
        Assert.assertTrue(Args.isEmpty(new int[] {}));
        Assert.assertTrue(Args.isEmpty(Collections.emptyList()));
        Assert.assertTrue(Args.isEmpty(Collections.emptySet()));
        Assert.assertTrue(Args.isEmpty(Collections.emptyMap()));

        Assert.assertFalse(Args.isEmpty("  "));
        Assert.assertFalse(Args.isEmpty("ab"));
        Assert.assertFalse(Args.isEmpty(NON_EMPTY_ARRAY));
        Assert.assertFalse(Args.isEmpty(NON_EMPTY_LIST));
        Assert.assertFalse(Args.isEmpty(NON_EMPTY_SET));
        Assert.assertFalse(Args.isEmpty(NON_EMPTY_MAP));
    }

    @Test
    public void testcontainsNoBlanks() {
        final String stuff = "abg";
        Assert.assertSame(stuff, Args.containsNoBlanks(stuff, "abg"));
    }

    @Test
    public void check() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                Args.check(false, "Error,", "ABG"));
    }

}
