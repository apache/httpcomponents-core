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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    @Test(expected=IllegalArgumentException.class)
    public void testArgCheckFail() {
        Args.check(false, "Oopsie");
    }

    @Test
    public void testArgNotNullPass() {
        final String stuff = "stuff";
        Assert.assertSame(stuff, Args.notNull(stuff, "Stuff"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgNotNullFail() {
        Args.notNull(null, "Stuff");
    }

    @Test
    public void testArgNotEmptyPass() {
        final String stuff = "stuff";
        Assert.assertSame(stuff, Args.notEmpty(stuff, "Stuff"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgNotEmptyFail1() {
        Args.notEmpty((String) null, "Stuff");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgNotEmptyFail2() {
        Args.notEmpty("", "Stuff");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgNotBlankFail1() {
        Args.notBlank((String) null, "Stuff");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgNotBlankFail2() {
        Args.notBlank("", "Stuff");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgNotBlankFail3() {
        Args.notBlank(" \t \n\r", "Stuff");
    }

    @Test
    public void testArgCollectionNotEmptyPass() {
        final List<String> list = Arrays.asList("stuff");
        Assert.assertSame(list, Args.notEmpty(list, "List"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgCollectionNotEmptyFail1() {
        Args.notEmpty((List<?>) null, "List");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testArgCollectionNotEmptyFail2() {
        Args.notEmpty(Collections.emptyList(), "List");
    }

    @Test
    public void testPositiveIntPass() {
        Assert.assertEquals(1, Args.positive(1, "Number"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPositiveIntFail1() {
        Args.positive(-1, "Number");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPositiveIntFail2() {
        Args.positive(0, "Number");
    }

    @Test
    public void testPositiveLongPass() {
        Assert.assertEquals(1L, Args.positive(1L, "Number"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPositiveLongFail1() {
        Args.positive(-1L, "Number");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPositiveLongFail2() {
        Args.positive(0L, "Number");
    }

    @Test
    public void testNotNegativeIntPass1() {
        Assert.assertEquals(1, Args.notNegative(1, "Number"));
    }

    @Test
    public void testNotNegativeIntPass2() {
        Assert.assertEquals(0, Args.notNegative(0, "Number"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNotNegativeIntFail1() {
        Args.notNegative(-1, "Number");
    }

    @Test
    public void testNotNegativeLongPass1() {
        Assert.assertEquals(1L, Args.notNegative(1L, "Number"));
    }

    @Test
    public void testNotNegativeLongPass2() {
        Assert.assertEquals(0L, Args.notNegative(0L, "Number"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNotNegativeLongFail1() {
        Args.notNegative(-1L, "Number");
    }

}
