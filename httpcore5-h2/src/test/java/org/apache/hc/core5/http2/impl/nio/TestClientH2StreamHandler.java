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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestClientH2StreamHandler {

    @Mock
    H2StreamChannel channel;
    @Mock
    HttpProcessor httpProcessor;
    @Mock
    AsyncClientExchangeHandler exchangeHandler;
    @Mock
    HandlerFactory<AsyncPushConsumer> pushHandlerFactory;

    ClientH2StreamHandler handler;

    @BeforeEach
    void prepareMocks() {
        MockitoAnnotations.openMocks(this);
        handler = new ClientH2StreamHandler(
                channel, httpProcessor,
                new BasicHttpConnectionMetrics(
                        new BasicHttpTransportMetrics(), new BasicHttpTransportMetrics()),
                exchangeHandler,
                pushHandlerFactory,
                HttpCoreContext.create());
    }

    @Test
    void defaults() {
        Assertions.assertNotNull(handler.getPushHandlerFactory());
        Assertions.assertTrue(handler.isOutputReady());
    }

    @Test
    void consumePromiseRejected() {
        Assertions.assertThrows(ProtocolException.class, () -> handler.consumePromise(null));
    }

    @Test
    void consumeDataRejectedBeforeHeaders() {
        Assertions.assertThrows(ProtocolException.class, () ->
                handler.consumeData(ByteBuffer.allocate(0), true));
    }

    @Test
    void updateCapacityDelegates() throws Exception {
        handler.updateInputCapacity();

        Mockito.verify(exchangeHandler).updateCapacity(channel);
    }

    @Test
    void toStringIncludesStates() {
        final String text = handler.toString();
        Assertions.assertTrue(text.contains("requestState"));
        Assertions.assertTrue(text.contains("responseState"));
    }

    @Test
    void consumeTrailersWithPseudoHeaderRejected() throws Exception {
        final List<Header> responseHeaders = Collections.singletonList(
                new BasicHeader(":status", "200"));
        handler.consumeHeader(responseHeaders, false);

        final List<Header> trailers = Collections.singletonList(
                new BasicHeader(":status", "200"));

        Assertions.assertThrows(ProtocolException.class, () -> handler.consumeHeader(trailers, true));
        Mockito.verify(exchangeHandler, Mockito.never()).streamEnd(Mockito.anyList());
    }

    @Test
    void contentLengthValid() throws Exception {
        final List<Header> responseHeaders = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("content-length", "12"));
        handler.consumeHeader(responseHeaders, false);
        handler.consumeData(ByteBuffer.wrap(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }), false);
        handler.consumeData(ByteBuffer.wrap(new byte[] { 0, 1 }), true);
    }

    @Test
    void contentLengthInvalid() throws Exception {
        final List<Header> responseHeaders = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("content-length", "12"));
        handler.consumeHeader(responseHeaders, false);
        handler.consumeData(ByteBuffer.wrap(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }), false);
        Assertions.assertThrows(ProtocolException.class, () ->
                handler.consumeData(ByteBuffer.wrap(new byte[] { 0, 1, 2 }), true));
    }

    @Test
    void contentLengthInvalidNoBody() throws Exception {
        final List<Header> responseHeaders = Arrays.asList(
                new BasicHeader(":status", "200"),
                new BasicHeader("content-length", "12"));
        Assertions.assertThrows(ProtocolException.class, () ->
            handler.consumeHeader(responseHeaders, true));
    }

}
