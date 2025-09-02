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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestFieldValidationSupport {

    @Test
    void testNameCharValid() throws Exception {
        Assertions.assertFalse(FieldValidationSupport.isNameCharValid(' '));
        Assertions.assertTrue(FieldValidationSupport.isNameCharValid('a'));
        Assertions.assertTrue(FieldValidationSupport.isNameCharValid('A'));
        Assertions.assertTrue(FieldValidationSupport.isNameCharValid('0'));
        Assertions.assertTrue(FieldValidationSupport.isNameCharValid('@'));
        Assertions.assertFalse(FieldValidationSupport.isNameCharValid(':'));
        Assertions.assertFalse(FieldValidationSupport.isNameCharValid('ä'));
    }

    @Test
    void testNameCharLowerCaseValid() throws Exception {
        Assertions.assertFalse(FieldValidationSupport.isNameCharLowerCaseValid(' '));
        Assertions.assertTrue(FieldValidationSupport.isNameCharLowerCaseValid('a'));
        Assertions.assertFalse(FieldValidationSupport.isNameCharLowerCaseValid('A'));
        Assertions.assertTrue(FieldValidationSupport.isNameCharLowerCaseValid('0'));
        Assertions.assertTrue(FieldValidationSupport.isNameCharLowerCaseValid('@'));
        Assertions.assertFalse(FieldValidationSupport.isNameCharLowerCaseValid(':'));
        Assertions.assertFalse(FieldValidationSupport.isNameCharLowerCaseValid('ä'));
    }

    @Test
    void testNameValid() throws Exception {
        Assertions.assertTrue(FieldValidationSupport.isNameValid("ABCDEF0123456789"));
        Assertions.assertFalse(FieldValidationSupport.isNameValid(":Blah"));
        Assertions.assertFalse(FieldValidationSupport.isNameValid("Blah "));
        Assertions.assertFalse(FieldValidationSupport.isNameValid("Bläh"));
    }

    @Test
    void testNameLowerCaseValid() throws Exception {
        Assertions.assertTrue(FieldValidationSupport.isNameValid("abcdef0123456789"));
        Assertions.assertFalse(FieldValidationSupport.isNameValid(":blah"));
        Assertions.assertFalse(FieldValidationSupport.isNameValid("blah "));
        Assertions.assertFalse(FieldValidationSupport.isNameValid("bläh"));
    }

    @Test
    void testValueCharValid() throws Exception {
        Assertions.assertTrue(FieldValidationSupport.isValueCharValid(' '));
        Assertions.assertTrue(FieldValidationSupport.isValueCharValid('a'));
        Assertions.assertTrue(FieldValidationSupport.isValueCharValid('A'));
        Assertions.assertTrue(FieldValidationSupport.isValueCharValid('0'));
        Assertions.assertTrue(FieldValidationSupport.isValueCharValid('@'));
        Assertions.assertTrue(FieldValidationSupport.isValueCharValid(':'));
        Assertions.assertTrue(FieldValidationSupport.isValueCharValid('ä'));
        Assertions.assertFalse(FieldValidationSupport.isValueCharValid((char) 0x00));
        Assertions.assertFalse(FieldValidationSupport.isValueCharValid('\n'));
        Assertions.assertFalse(FieldValidationSupport.isValueCharValid('\r'));
    }

    @Test
    void testValueValid() throws Exception {
        Assertions.assertTrue(FieldValidationSupport.isValueValid("ABCDEF0123456789"));
        Assertions.assertTrue(FieldValidationSupport.isValueValid(":Blah"));
        Assertions.assertTrue(FieldValidationSupport.isValueValid("Bläh"));
        Assertions.assertFalse(FieldValidationSupport.isValueValid(" Blah"));
        Assertions.assertFalse(FieldValidationSupport.isValueValid("Blah\t"));
        Assertions.assertFalse(FieldValidationSupport.isValueValid("Blah\nBlah"));
        Assertions.assertFalse(FieldValidationSupport.isValueValid("\rBlah"));
    }

}

