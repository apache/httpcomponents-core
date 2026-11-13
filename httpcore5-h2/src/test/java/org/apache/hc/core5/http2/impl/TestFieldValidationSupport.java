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

package org.apache.hc.core5.http2.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestFieldValidationSupport {

    @Test
    void testNameCharValid() throws Exception {
        Assertions.assertThat(FieldValidationSupport.isNameCharValid(' ')).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameCharValid('a')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameCharValid('A')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameCharValid('0')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameCharValid('@')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameCharValid(':')).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameCharValid('ä')).isFalse();
    }

    @Test
    void testNameCharLowerCaseValid() throws Exception {
        Assertions.assertThat(FieldValidationSupport.isNameCharLowerCaseValid(' ')).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameCharLowerCaseValid('a')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameCharLowerCaseValid('A')).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameCharLowerCaseValid('0')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameCharLowerCaseValid('@')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameCharLowerCaseValid(':')).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameCharLowerCaseValid('ä')).isFalse();
    }

    @Test
    void testNameValid() throws Exception {
        Assertions.assertThat(FieldValidationSupport.isNameValid("ABCDEF0123456789")).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameValid(":Blah")).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameValid("Blah ")).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameValid("Bläh")).isFalse();
    }

    @Test
    void testNameLowerCaseValid() throws Exception {
        Assertions.assertThat(FieldValidationSupport.isNameValid("abcdef0123456789")).isTrue();
        Assertions.assertThat(FieldValidationSupport.isNameValid(":blah")).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameValid("blah ")).isFalse();
        Assertions.assertThat(FieldValidationSupport.isNameValid("bläh")).isFalse();
    }

    @Test
    void testValueCharValid() throws Exception {
        Assertions.assertThat(FieldValidationSupport.isValueCharValid(' ')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid('a')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid('A')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid('0')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid('@')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid(':')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid('ä')).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid((char) 0x00)).isFalse();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid('\n')).isFalse();
        Assertions.assertThat(FieldValidationSupport.isValueCharValid('\r')).isFalse();
    }

    @Test
    void testValueValid() throws Exception {
        Assertions.assertThat(FieldValidationSupport.isValueValid("ABCDEF0123456789")).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueValid(":Blah")).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueValid("Bläh")).isTrue();
        Assertions.assertThat(FieldValidationSupport.isValueValid(" Blah")).isFalse();
        Assertions.assertThat(FieldValidationSupport.isValueValid("Blah\t")).isFalse();
        Assertions.assertThat(FieldValidationSupport.isValueValid("Blah\nBlah")).isFalse();
        Assertions.assertThat(FieldValidationSupport.isValueValid("\rBlah")).isFalse();
    }

}

