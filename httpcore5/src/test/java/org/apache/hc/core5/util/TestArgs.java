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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Args}.
 */
class TestArgs {

    @Test
    void testArgCheckPass() {
        Args.check(true, "All is well");
    }

    @Test
    void testArgCheckFail() {
        Assertions.assertThatThrownBy(() ->
                Args.check(false, "Oopsie")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testArgNotNullPass() {
        final String stuff = "stuff";
        Assertions.assertThat(Args.notNull(stuff, "Stuff")).isSameAs(stuff);
    }

    @Test
    void testArgNotNullFail() {
        Assertions.assertThatThrownBy(() ->
                Args.notNull(null, "Stuff")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testArgNotEmptyPass() {
        final String stuff = "stuff";
        Assertions.assertThat(Args.notEmpty(stuff, "Stuff")).isSameAs(stuff);
    }

    @Test
    void testArgNotEmptyFail1() {
        Assertions.assertThatThrownBy(() ->
                Args.notEmpty((String) null, "Stuff")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testArgNotEmptyFail2() {
        Assertions.assertThatThrownBy(() ->
                Args.notEmpty("", "Stuff")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testArgNotBlankFail1() {
        Assertions.assertThatThrownBy(() ->
                Args.notBlank((String) null, "Stuff")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testArgNotBlankFail2() {
        Assertions.assertThatThrownBy(() ->
                Args.notBlank("", "Stuff")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testArgNotBlankFail3() {
        Assertions.assertThatThrownBy(() ->
                Args.notBlank(" \t \n\r", "Stuff")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testArgCollectionNotEmptyPass() {
        final List<String> list = Collections.singletonList("stuff");
        Assertions.assertThat(Args.notEmpty(list, "List")).isSameAs(list);
    }

    @Test
    void testArgCollectionNotEmptyFail1() {
        Assertions.assertThatThrownBy(() ->
                Args.notEmpty((List<?>) null, "List")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testArgCollectionNotEmptyFail2() {
        Assertions.assertThatThrownBy(() ->
                Args.notEmpty(Collections.emptyList(), "List")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPositiveIntPass() {
        Assertions.assertThat(Args.positive(1, "Number")).isEqualTo(1);
    }

    @Test
    void testPositiveIntFail1() {
        Assertions.assertThatThrownBy(() ->
                Args.positive(-1, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPositiveIntFail2() {
        Assertions.assertThatThrownBy(() ->
                Args.positive(0, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPositiveLongPass() {
        Assertions.assertThat(Args.positive(1L, "Number")).isEqualTo(1L);
    }

    @Test
    void testPositiveTimeValuePass() throws ParseException {
        final Timeout timeout = Timeout.parse("1200 MILLISECONDS");
        Assertions.assertThat(Args.positive(timeout, "No Error")).isEqualTo(timeout);
    }
    @Test
    void testPositiveLongFail1() {
        Assertions.assertThatThrownBy(() ->
                Args.positive(-1L, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPositiveLongFail2() {
        Assertions.assertThatThrownBy(() ->
                Args.positive(0L, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNotNegativeIntPass1() {
        Assertions.assertThat(Args.notNegative(1, "Number")).isEqualTo(1);
    }

    @Test
    void testNotNegativeIntPass2() {
        Assertions.assertThat(Args.notNegative(0, "Number")).isEqualTo(0);
    }

    @Test
    void testNotNegativeIntFail1() {
        Assertions.assertThatThrownBy(() ->
                Args.notNegative(-1, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNotNegativeLongPass1() {
        Assertions.assertThat(Args.notNegative(1L, "Number")).isEqualTo(1L);
    }

    @Test
    void testNotNegativeLongPass2() {
        Assertions.assertThat(Args.notNegative(0L, "Number")).isEqualTo(0L);
    }

    @Test
    void testNotNegativeLongFail1() {
        Assertions.assertThatThrownBy(() ->
                Args.notNegative(-1L, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIntSmallestRangeOK() {
        Args.checkRange(0, 0, 0, "Number");
    }

    @Test
    void testIntSmallestRangeFailLow() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(-1, 0, 0, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIntRangeFailLow() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(-101, -100, 100, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIntRangeFailHigh() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(101, -100, 100, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIntSmallestRangeFailHigh() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(1, 0, 0, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIntFullRangeOK() {
        Args.checkRange(0, Integer.MIN_VALUE, Integer.MAX_VALUE, "Number");
    }

    @Test
    void testLongSmallestRangeOK() {
        Args.checkRange(0L, 0L, 0L, "Number");
    }

    @Test
    void testLongSmallestRangeFailLow() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(-1L, 0L, 0L, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testLongRangeFailLow() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(-101L, -100L, 100L, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testLongRangeFailHigh() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(101L, -100L, 100L, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testLongSmallestRangeFailHigh() {
        Assertions.assertThatThrownBy(() ->
                Args.checkRange(1L, 0L, 0L, "Number")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testLongFullRangeOK() {
        Args.checkRange(0L, Long.MIN_VALUE, Long.MAX_VALUE, "Number");
    }

    @Test
    void testIsEmpty() {

        final String[] NON_EMPTY_ARRAY = new String[] { "ABG", "NML", };

        final List<String> NON_EMPTY_LIST = Arrays.asList(NON_EMPTY_ARRAY);

        final Set<String> NON_EMPTY_SET = new HashSet<>(NON_EMPTY_LIST);

        final Map<String, String> NON_EMPTY_MAP = new HashMap<>();
        NON_EMPTY_MAP.put("ABG", "MNL");

        Assertions.assertThat(Args.isEmpty(null)).isTrue();
        Assertions.assertThat(Args.isEmpty("")).isTrue();
        Assertions.assertThat(Args.isEmpty(new int[]{})).isTrue();
        Assertions.assertThat(Args.isEmpty(Collections.emptyList())).isTrue();
        Assertions.assertThat(Args.isEmpty(Collections.emptySet())).isTrue();
        Assertions.assertThat(Args.isEmpty(Collections.emptyMap())).isTrue();

        Assertions.assertThat(Args.isEmpty("  ")).isFalse();
        Assertions.assertThat(Args.isEmpty("ab")).isFalse();
        Assertions.assertThat(Args.isEmpty(NON_EMPTY_ARRAY)).isFalse();
        Assertions.assertThat(Args.isEmpty(NON_EMPTY_LIST)).isFalse();
        Assertions.assertThat(Args.isEmpty(NON_EMPTY_SET)).isFalse();
        Assertions.assertThat(Args.isEmpty(NON_EMPTY_MAP)).isFalse();
    }

    @Test
    void testContainsNoBlanks() {
        final String stuff = "abg";
        Assertions.assertThat(Args.containsNoBlanks(stuff, "abg")).isSameAs(stuff);
    }

    @Test
    void check() {
        Assertions.assertThatThrownBy(() ->
                Args.check(false, "Error,", "ABG")).isInstanceOf(IllegalArgumentException.class);
    }

}
