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

package org.apache.http.util;

import junit.framework.TestCase;

/**
 * Unit tests for {@link LangUtils}.
 *
 */
public class TestLangUtils extends TestCase {

    public TestLangUtils(String testName) {
        super(testName);
    }

    public void testBasicHash() {
        Integer i = new Integer(1234);
        int h1 = LangUtils.hashCode(LangUtils.HASH_SEED, i.hashCode());
        int h2 = LangUtils.hashCode(LangUtils.HASH_SEED, i);
        assertTrue(h1 == h2);
    }

    public void testNullObjectHash() {
        int h1 = LangUtils.hashCode(LangUtils.HASH_SEED, null);
        int h2 = LangUtils.hashCode(LangUtils.HASH_SEED, 0);
        assertTrue(h1 == h2);
    }

    public void testBooleanHash() {
        int h1 = LangUtils.hashCode(LangUtils.HASH_SEED, true);
        int h2 = LangUtils.hashCode(LangUtils.HASH_SEED, false);
        int h3 = LangUtils.hashCode(LangUtils.HASH_SEED, true);
        int h4 = LangUtils.hashCode(LangUtils.HASH_SEED, false);
        assertTrue(h1 != h2);
        assertTrue(h1 == h3);
        assertTrue(h2 == h4);
    }

    public void testBasicEquality() {
        assertTrue(LangUtils.equals(null, null));
        assertFalse(LangUtils.equals(null, "abc"));
        assertFalse(LangUtils.equals("abc", null));
        assertTrue(LangUtils.equals("abc", "abc"));
    }

    public void testArrayEquals() {
        assertFalse(LangUtils.equals(null, new Object[] {}));
        assertFalse(LangUtils.equals(new Object[] {}, null));
        assertTrue(LangUtils.equals(new Object[] {}, new Object[] {}));
        assertFalse(LangUtils.equals(
                new Object[] {new Integer(1), new Integer(2)},
                new Object[] {new Integer(1)}));
        assertFalse(LangUtils.equals(
                new Object[] {new Integer(1), new Integer(2)},
                new Object[] {new Integer(1), new Integer(3)}));
        assertTrue(LangUtils.equals(
                new Object[] {new Integer(1), new Integer(2)},
                new Object[] {new Integer(1), new Integer(2)}));
    }
}
