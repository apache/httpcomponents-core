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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for various HTTP exception classes.
 */
class TestHttpExceptions {

    private static final String CLEAN_MESSAGE = "[0x01]Hello[0x01][0x02][0x03][0x04][0x05][0x06][0x07][0x08][0x09][0x00]World";
    private static final String nonPrintableMessage = String.valueOf(
            new char[] { 1, 'H', 'e', 'l', 'l', 'o', 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 'W', 'o', 'r', 'l', 'd' });

    @Test
    void testConstructor() {
        final Throwable cause = new Exception();
        Assertions.assertThatCode(() -> new HttpException()).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new HttpException("Oppsie")).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new HttpException("Oppsie", cause)).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new ProtocolException()).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new ProtocolException("Oppsie")).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new ProtocolException("Oppsie", cause)).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new NoHttpResponseException("Oppsie")).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new ConnectionClosedException("Oppsie")).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new MethodNotSupportedException("Oppsie")).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new MethodNotSupportedException("Oppsie", cause)).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new UnsupportedHttpVersionException()).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> new UnsupportedHttpVersionException("Oppsie")).doesNotThrowAnyException();
    }

    @Test
    void testNonPrintableCharactersInConnectionClosedException() {
        Assertions.assertThat(new ConnectionClosedException(nonPrintableMessage).getMessage()).isEqualTo(CLEAN_MESSAGE);
    }

    @Test
    void testNonPrintableCharactersInHttpException() {
        Assertions.assertThat(new HttpException(nonPrintableMessage).getMessage()).isEqualTo(CLEAN_MESSAGE);
    }

    @Test
    void testNonPrintableCharactersInMethodNotSupportedException() {
        Assertions.assertThat(new MethodNotSupportedException(nonPrintableMessage).getMessage()).isEqualTo(CLEAN_MESSAGE);
    }

    @Test
    void testNonPrintableCharactersInNoHttpResponseException() {
        Assertions.assertThat(new NoHttpResponseException(nonPrintableMessage).getMessage()).isEqualTo(CLEAN_MESSAGE);
    }

    @Test
    void testNonPrintableCharactersInProtocolException() {
        Assertions.assertThat(new ProtocolException(nonPrintableMessage).getMessage()).isEqualTo(CLEAN_MESSAGE);
    }

    @Test
    void testNonPrintableCharactersInUnsupportedHttpVersionException() {
        Assertions.assertThat(new UnsupportedHttpVersionException(nonPrintableMessage).getMessage()).isEqualTo(CLEAN_MESSAGE);
    }


    @Test
    void testControlCharacterEscaping() {
        final HttpException exception = new HttpException("0123456789abcdef\nsuffix");

        Assertions.assertThat(exception.getMessage()).isEqualTo("0123456789abcdef[0x0a]suffix");
    }

    @Test
    void testControlCharacterEncodingIndependentOfPosition() {
        Assertions.assertThat(new HttpException("a\nb").getMessage()).isEqualTo("a[0x0a]b");
        Assertions.assertThat(new HttpException("0123456789abcdef\nb").getMessage()).isEqualTo("0123456789abcdef[0x0a]b");
    }

}
