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
 * Unit tests for {@link TextUtils}.
 *
 */
class TestTextUtils {

    @Test
    void testTextEmpty() {
        Assertions.assertThat(TextUtils.isEmpty(null)).isTrue();
        Assertions.assertThat(TextUtils.isEmpty("")).isTrue();
        Assertions.assertThat(TextUtils.isEmpty("\t")).isFalse();
    }

    @Test
    void testTextBlank() {
        Assertions.assertThat(TextUtils.isBlank(null)).isTrue();
        Assertions.assertThat(TextUtils.isBlank("")).isTrue();
        Assertions.assertThat(TextUtils.isBlank("   ")).isTrue();
        Assertions.assertThat(TextUtils.isBlank("\t")).isTrue();
    }

    @Test
    void testTextContainsBlanks() {
        Assertions.assertThat(TextUtils.containsBlanks(null)).isFalse();
        Assertions.assertThat(TextUtils.containsBlanks("")).isFalse();
        Assertions.assertThat(TextUtils.containsBlanks("   ")).isTrue();
        Assertions.assertThat(TextUtils.containsBlanks("\t")).isTrue();
        Assertions.assertThat(TextUtils.containsBlanks(" a")).isTrue();
        Assertions.assertThat(TextUtils.containsBlanks("a")).isFalse();
    }

    @Test
    void testToHexString() {
        Assertions.assertThat(TextUtils.toHexString(new byte[]{0, 12, 32, 1, -1})).isEqualTo("000c2001ff");
        Assertions.assertThat(TextUtils.toHexString(null)).isNull();
    }

    @Test
    void testPrintableCharactersAndByteConversion() {
        for (int c = 0; c <= 0xFF; c++) {
            final boolean expected = c == '\t' || c >= 0x20 && c <= 0x7E || c >= 0xA0;
            Assertions.assertThat(TextUtils.isPrintable(c))
                    .as("Character: " + c)
                    .isEqualTo(expected);
            Assertions.assertThat(TextUtils.castAsByte(c))
                    .as("Character: " + c)
                    .isEqualTo(expected ? (byte) c : (byte) '?');
        }
        Assertions.assertThat(TextUtils.isPrintable(0x20AC)).isTrue();
        Assertions.assertThat(TextUtils.castAsByte(0x20AC)).isEqualTo((byte) '?');
    }

}
