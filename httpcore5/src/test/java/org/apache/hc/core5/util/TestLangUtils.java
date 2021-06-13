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

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link LangUtils}.
 *
 */
public class TestLangUtils {

    @Test
    public void testBasicHash() {
        final Integer i = Integer.valueOf(1234);
        final int h1 = LangUtils.hashCode(LangUtils.HASH_SEED, i.hashCode());
        final int h2 = LangUtils.hashCode(LangUtils.HASH_SEED, i);
        Assert.assertEquals(h1, h2);
    }

    @Test
    public void testNullObjectHash() {
        final int h1 = LangUtils.hashCode(LangUtils.HASH_SEED, null);
        final int h2 = LangUtils.hashCode(LangUtils.HASH_SEED, 0);
        Assert.assertEquals(h1, h2);
    }

    @Test
    public void testBooleanHash() {
        final int h1 = LangUtils.hashCode(LangUtils.HASH_SEED, true);
        final int h2 = LangUtils.hashCode(LangUtils.HASH_SEED, false);
        final int h3 = LangUtils.hashCode(LangUtils.HASH_SEED, true);
        final int h4 = LangUtils.hashCode(LangUtils.HASH_SEED, false);
        Assert.assertTrue(h1 != h2);
        Assert.assertEquals(h1, h3);
        Assert.assertEquals(h2, h4);
    }

    @Test
    public void testBasicEquality() {
        Assert.assertTrue(LangUtils.equals(null, null));
        Assert.assertFalse(LangUtils.equals(null, "abc"));
        Assert.assertFalse(LangUtils.equals("abc", null));
        Assert.assertTrue(LangUtils.equals("abc", "abc"));
    }

    @Test
    public void testArrayEquals() {
        Assert.assertFalse(LangUtils.equals(null, new Object[] {}));
        Assert.assertFalse(LangUtils.equals(new Object[] {}, null));
        Assert.assertTrue(LangUtils.equals(new Object[] {}, new Object[] {}));
        Assert.assertFalse(LangUtils.equals(
                new Object[] {Integer.valueOf(1), Integer.valueOf(2)},
                new Object[] {Integer.valueOf(1)}));
        Assert.assertFalse(LangUtils.equals(
                new Object[] {Integer.valueOf(1), Integer.valueOf(2)},
                new Object[] {Integer.valueOf(1), Integer.valueOf(3)}));
        Assert.assertTrue(LangUtils.equals(
                new Object[] {Integer.valueOf(1), Integer.valueOf(2)},
                new Object[] {Integer.valueOf(1), Integer.valueOf(2)}));
    }
}
