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

import org.assertj.core.api.Assertions;
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
        Assertions.assertThatThrownBy(() ->
                Asserts.check(false, "Oopsie")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testExpressionNotNullFail() {
        Assertions.assertThatThrownBy(() ->
                Asserts.notNull(null, "Stuff")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testExpressionNotEmptyFail1() {
        Assertions.assertThatThrownBy(() ->
                Asserts.notEmpty(null, "Stuff")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testExpressionNotEmptyFail2() {
        Assertions.assertThatThrownBy(() ->
                Asserts.notEmpty("", "Stuff")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testExpressionNotEmptyBlank1() {
        Assertions.assertThatThrownBy(() ->
                Asserts.notBlank(null, "Stuff")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testExpressionNotEmptyBlank2() {
        Assertions.assertThatThrownBy(() ->
                Asserts.notBlank("", "Stuff")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testExpressionNotBlankFail3() {
        Assertions.assertThatThrownBy(() ->
                Asserts.notBlank(" \t \n\r", "Stuff")).isInstanceOf(IllegalStateException.class);
    }

}
