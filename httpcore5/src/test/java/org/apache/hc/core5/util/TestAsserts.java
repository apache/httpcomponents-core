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
 * Unit tests for {@link Asserts}.
 */
class TestAsserts {

    @Test
    void testExpressionCheckPass() {
        Asserts.check(true, "All is well");
    }

    @Test
    void testExpressionCheckFail() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                Asserts.check(false, "Oopsie"));
    }

    @Test
    void testExpressionNotNullFail() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                Asserts.notNull(null, "Stuff"));
    }

    @Test
    void testExpressionNotEmptyFail1() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                Asserts.notEmpty(null, "Stuff"));
    }

    @Test
    void testExpressionNotEmptyFail2() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                Asserts.notEmpty("", "Stuff"));
    }

    @Test
    void testExpressionNotEmptyBlank1() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                Asserts.notBlank(null, "Stuff"));
    }

    @Test
    void testExpressionNotEmptyBlank2() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                Asserts.notBlank("", "Stuff"));
    }

    @Test
    void testExpressionNotBlankFail3() {
        Assertions.assertThrows(IllegalStateException.class, () ->
                Asserts.notBlank(" \t \n\r", "Stuff"));
    }

}
