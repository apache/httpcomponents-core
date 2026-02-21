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
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestClientH2StreamMultiplexer {

    private ClientH2StreamMultiplexer newMultiplexer() {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        return new ClientH2StreamMultiplexer(
                ioSession,
                DefaultFrameFactory.INSTANCE,
                httpProcessor,
                pushHandlerFactory,
                H2Config.DEFAULT,
                null,
                null);
    }

    @Test
    void validateSettingRejectsEnablePush1OnClient() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.validateSetting(H2Param.ENABLE_PUSH, 1));
        Assertions.assertEquals(H2Error.PROTOCOL_ERROR.getCode(), ex.getCode());
    }

    @Test
    void validateSettingAcceptsEnablePush0OnClient() throws Exception {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        multiplexer.validateSetting(H2Param.ENABLE_PUSH, 0);
    }

    @Test
    void validateSettingRejectsEnablePush2OnClient() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.validateSetting(H2Param.ENABLE_PUSH, 2));
        Assertions.assertEquals(H2Error.PROTOCOL_ERROR.getCode(), ex.getCode());
    }

    @Test
    void validateSettingRejectsEnablePushNegativeOnClient() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.validateSetting(H2Param.ENABLE_PUSH, -1));
        Assertions.assertEquals(H2Error.PROTOCOL_ERROR.getCode(), ex.getCode());
    }

    @Test
    void acceptHeaderFrameRejected() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        Assertions.assertThrows(H2ConnectionException.class, multiplexer::acceptHeaderFrame);
    }

    @Test
    void outgoingRequestCreatesHandler() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        final H2StreamHandler handler = multiplexer.outgoingRequest(
                Mockito.mock(H2StreamChannel.class),
                Mockito.mock(AsyncClientExchangeHandler.class),
                null,
                null);
        Assertions.assertNotNull(handler);
    }

    @Test
    void incomingRequestRejected() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.incomingRequest(Mockito.mock(H2StreamChannel.class)));
    }

    @Test
    void outgoingPushPromiseRejected() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        Assertions.assertThrows(H2ConnectionException.class, () ->
                multiplexer.outgoingPushPromise(
                        Mockito.mock(H2StreamChannel.class),
                        Mockito.mock(AsyncPushProducer.class)));
    }

    @Test
    void incomingPushPromiseCreatesHandler() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        final H2StreamHandler handler = multiplexer.incomingPushPromise(
                Mockito.mock(H2StreamChannel.class),
                pushHandlerFactory);
        Assertions.assertNotNull(handler);
    }

    @Test
    void allowGracefulAbortUsesRemoteClosed() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        final H2Stream stream = Mockito.mock(H2Stream.class);
        Mockito.when(stream.isRemoteClosed()).thenReturn(true);
        Mockito.when(stream.isLocalClosed()).thenReturn(false);
        Assertions.assertTrue(multiplexer.allowGracefulAbort(stream));
    }

    @Test
    void toStringContainsState() {
        final ClientH2StreamMultiplexer multiplexer = newMultiplexer();
        final String text = multiplexer.toString();
        Assertions.assertTrue(text.startsWith("["));
        Assertions.assertTrue(text.endsWith("]"));
    }

}
