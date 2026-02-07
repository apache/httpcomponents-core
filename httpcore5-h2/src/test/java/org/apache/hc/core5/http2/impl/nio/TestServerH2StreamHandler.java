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
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestServerH2StreamHandler {

    private ServerH2StreamHandler newHandler() {
        final H2StreamChannel channel = Mockito.mock(H2StreamChannel.class);
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        final BasicHttpConnectionMetrics metrics = new BasicHttpConnectionMetrics(
                new BasicHttpTransportMetrics(), new BasicHttpTransportMetrics());
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory =
                (HandlerFactory<AsyncServerExchangeHandler>) Mockito.mock(HandlerFactory.class);
        return new ServerH2StreamHandler(channel, httpProcessor, metrics, exchangeHandlerFactory,
                HttpCoreContext.create());
    }

    @Test
    void defaults() {
        final ServerH2StreamHandler handler = newHandler();
        Assertions.assertNull(handler.getPushHandlerFactory());
        Assertions.assertFalse(handler.isOutputReady());
    }

    @Test
    void consumeDataBeforeHeadersRejected() {
        final ServerH2StreamHandler handler = newHandler();
        Assertions.assertThrows(ProtocolException.class, () ->
                handler.consumeData(ByteBuffer.allocate(0), true));
    }

    @Test
    void updateCapacityWithoutHandlerFails() {
        final ServerH2StreamHandler handler = newHandler();
        Assertions.assertThrows(IllegalStateException.class, () -> handler.updateInputCapacity());
    }

    @Test
    void toStringIncludesStates() {
        final ServerH2StreamHandler handler = newHandler();
        final String text = handler.toString();
        Assertions.assertTrue(text.contains("requestState"));
        Assertions.assertTrue(text.contains("responseState"));
    }

}
