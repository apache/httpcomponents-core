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
package org.apache.hc.core5.jackson2;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class JsonTokenEventHandlerAdaptorTest {

    @Test
    void testIntTokenWithBigIntegerConvertibleToLongUsesLongCallback() throws Exception {
        final JsonTokenEventHandler eventHandler = Mockito.mock(JsonTokenEventHandler.class);
        final JsonParser jsonParser = Mockito.mock(JsonParser.class);
        Mockito.when(jsonParser.getNumberType()).thenReturn(JsonParser.NumberType.BIG_INTEGER);
        Mockito.when(jsonParser.getLongValue()).thenReturn(2147483648L);

        final JsonTokenEventHandlerAdaptor adaptor = new JsonTokenEventHandlerAdaptor(eventHandler);
        adaptor.accept(JsonTokenId.ID_NUMBER_INT, jsonParser);

        Mockito.verify(eventHandler).value(2147483648L);
        Mockito.verify(eventHandler, Mockito.never()).value(Mockito.anyInt());
        Mockito.verifyNoMoreInteractions(eventHandler);
    }

    @Test
    void testIntTokenWithBigIntegerOutOfLongRangeThrowsIOException() throws Exception {
        final JsonTokenEventHandler eventHandler = Mockito.mock(JsonTokenEventHandler.class);
        final JsonParser jsonParser = Mockito.mock(JsonParser.class);
        Mockito.when(jsonParser.getNumberType()).thenReturn(JsonParser.NumberType.BIG_INTEGER);
        Mockito.when(jsonParser.getText()).thenReturn("9223372036854775808");
        Mockito.when(jsonParser.getLongValue()).thenThrow(new IOException("Numeric value out of range"));

        final JsonTokenEventHandlerAdaptor adaptor = new JsonTokenEventHandlerAdaptor(eventHandler);
        Assertions.assertThatIOException().isThrownBy(
                        () -> adaptor.accept(JsonTokenId.ID_NUMBER_INT, jsonParser))
                .withMessageContaining("out of range for 64-bit signed long");

        Mockito.verifyNoInteractions(eventHandler);
    }

}
