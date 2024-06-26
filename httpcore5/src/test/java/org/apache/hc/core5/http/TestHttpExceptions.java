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

package org.apache.hc.core5.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for various HTTP exception classes.
 */
class TestHttpExceptions {

    private static final String CLEAN_MESSAGE = "[0x00]Hello[0x06][0x07][0x08][0x09][0x0a][0x0b][0x0c][0x0d][0x0e][0x0f]World";
    private static final String nonPrintableMessage = String.valueOf(
            new char[] { 1, 'H', 'e', 'l', 'l', 'o', 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 'W', 'o', 'r', 'l', 'd' });

    @Test
    void testConstructor() {
        final Throwable cause = new Exception();
        Assertions.assertDoesNotThrow(() -> new HttpException());
        Assertions.assertDoesNotThrow(() -> new HttpException("Oppsie"));
        Assertions.assertDoesNotThrow(() -> new HttpException("Oppsie", cause));
        Assertions.assertDoesNotThrow(() -> new ProtocolException());
        Assertions.assertDoesNotThrow(() -> new ProtocolException("Oppsie"));
        Assertions.assertDoesNotThrow(() -> new ProtocolException("Oppsie", cause));
        Assertions.assertDoesNotThrow(() -> new NoHttpResponseException("Oppsie"));
        Assertions.assertDoesNotThrow(() -> new ConnectionClosedException("Oppsie"));
        Assertions.assertDoesNotThrow(() -> new MethodNotSupportedException("Oppsie"));
        Assertions.assertDoesNotThrow(() -> new MethodNotSupportedException("Oppsie", cause));
        Assertions.assertDoesNotThrow(() -> new UnsupportedHttpVersionException());
        Assertions.assertDoesNotThrow(() -> new UnsupportedHttpVersionException("Oppsie"));
    }

    @Test
    void testNonPrintableCharactersInConnectionClosedException() {
        Assertions.assertEquals(CLEAN_MESSAGE, new ConnectionClosedException(nonPrintableMessage).getMessage());
    }

    @Test
    void testNonPrintableCharactersInHttpException() {
        Assertions.assertEquals(CLEAN_MESSAGE, new HttpException(nonPrintableMessage).getMessage());
    }

    @Test
    void testNonPrintableCharactersInMethodNotSupportedException() {
        Assertions.assertEquals(CLEAN_MESSAGE, new MethodNotSupportedException(nonPrintableMessage).getMessage());
    }

    @Test
    void testNonPrintableCharactersInNoHttpResponseException() {
        Assertions.assertEquals(CLEAN_MESSAGE, new NoHttpResponseException(nonPrintableMessage).getMessage());
    }

    @Test
    void testNonPrintableCharactersInProtocolException() {
        Assertions.assertEquals(CLEAN_MESSAGE, new ProtocolException(nonPrintableMessage).getMessage());
    }

    @Test
    void testNonPrintableCharactersInUnsupportedHttpVersionException() {
        Assertions.assertEquals(CLEAN_MESSAGE, new UnsupportedHttpVersionException(nonPrintableMessage).getMessage());
    }

}
