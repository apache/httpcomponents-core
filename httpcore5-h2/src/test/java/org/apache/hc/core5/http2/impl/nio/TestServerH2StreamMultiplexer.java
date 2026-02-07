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

import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestServerH2StreamMultiplexer {

    private ServerH2StreamMultiplexer newMultiplexer() {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory =
                (HandlerFactory<AsyncServerExchangeHandler>) Mockito.mock(HandlerFactory.class);
        return new ServerH2StreamMultiplexer(
                ioSession,
                DefaultFrameFactory.INSTANCE,
                httpProcessor,
                exchangeHandlerFactory,
                null,
                H2Config.DEFAULT,
                null);
    }

    @Test
    void acceptPushFrameRejected() {
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer();
        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, multiplexer::acceptPushFrame);
        Assertions.assertEquals(H2Error.PROTOCOL_ERROR.getCode(), ex.getCode());
    }

    @Test
    void outgoingRequestRejected() {
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer();
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.outgoingRequest(Mockito.mock(H2StreamChannel.class),
                        Mockito.mock(AsyncClientExchangeHandler.class),
                        pushHandlerFactory,
                        null));
    }

    @Test
    void incomingPushPromiseRejected() {
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer();
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.incomingPushPromise(Mockito.mock(H2StreamChannel.class), pushHandlerFactory));
    }

    @Test
    void outgoingPushPromiseCreatesHandler() throws Exception {
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer();
        final H2StreamHandler handler = multiplexer.outgoingPushPromise(
                Mockito.mock(H2StreamChannel.class),
                Mockito.mock(AsyncPushProducer.class));
        Assertions.assertNotNull(handler);
    }

    @Test
    void allowGracefulAbortIsFalse() {
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer();
        Assertions.assertFalse(multiplexer.allowGracefulAbort(Mockito.mock(H2Stream.class)));
    }

    @Test
    void toStringContainsState() {
        final ServerH2StreamMultiplexer multiplexer = newMultiplexer();
        final String text = multiplexer.toString();
        Assertions.assertTrue(text.startsWith("["));
        Assertions.assertTrue(text.endsWith("]"));
    }

}
