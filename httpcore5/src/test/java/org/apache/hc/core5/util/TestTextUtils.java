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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TextUtils}.
 *
 */
public class TestTextUtils {

    @Test
    public void testTextEmpty() {
        Assertions.assertTrue(TextUtils.isEmpty(null));
        Assertions.assertTrue(TextUtils.isEmpty(""));
        Assertions.assertFalse(TextUtils.isEmpty("\t"));
    }

    @Test
    public void testTextBlank() {
        Assertions.assertTrue(TextUtils.isBlank(null));
        Assertions.assertTrue(TextUtils.isBlank(""));
        Assertions.assertTrue(TextUtils.isBlank("   "));
        Assertions.assertTrue(TextUtils.isBlank("\t"));
    }

    @Test
    public void testTextContainsBlanks() {
        Assertions.assertFalse(TextUtils.containsBlanks(null));
        Assertions.assertFalse(TextUtils.containsBlanks(""));
        Assertions.assertTrue(TextUtils.containsBlanks("   "));
        Assertions.assertTrue(TextUtils.containsBlanks("\t"));
        Assertions.assertTrue(TextUtils.containsBlanks(" a"));
        Assertions.assertFalse(TextUtils.containsBlanks("a"));
    }

    @Test
    public void testToHexString() {
        Assertions.assertEquals("000c2001ff", TextUtils.toHexString(new byte[] { 0, 12, 32, 1 , -1}));
        Assertions.assertNull(TextUtils.toHexString(null));
    }

}
