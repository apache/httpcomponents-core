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
package org.apache.hc.core5.http2.impl.nio;

import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TestClientPushH2StreamHandler {

    @Mock
    H2StreamChannel channel;
    @Mock
    HttpProcessor httpProcessor;
    @Mock
    HandlerFactory<AsyncPushConsumer> pushHandlerFactory;

    ClientPushH2StreamHandler handler;

    @BeforeEach
    void prepareMocks() {
        MockitoAnnotations.openMocks(this);
        handler = new ClientPushH2StreamHandler(
                channel, httpProcessor,
                new BasicHttpConnectionMetrics(
                        new BasicHttpTransportMetrics(), new BasicHttpTransportMetrics()),
                pushHandlerFactory,
                HttpCoreContext.create());
    }

    @Test
    void consumePromiseRefusedWhenFactoryReturnsNull() throws Exception {
        final H2StreamResetException ex = Assertions.assertThrows(H2StreamResetException.class, () ->
                handler.consumePromise(java.util.Arrays.asList(
                        new BasicHeader(":method", "GET"),
                        new BasicHeader(":scheme", "https"),
                        new BasicHeader(":authority", "example.com"),
                        new BasicHeader(":path", "/"))));
        Assertions.assertEquals(H2Error.REFUSED_STREAM.getCode(), ex.getCode());
    }

    @Test
    void consumeDataRejectedBeforeBody() {
        Assertions.assertThrows(ProtocolException.class, () ->
                handler.consumeData(ByteBuffer.allocate(0), true));
    }

    @Test
    void updateCapacityFailsWithoutHandler() {
        Assertions.assertThrows(IllegalStateException.class, handler::updateInputCapacity);
    }

}
