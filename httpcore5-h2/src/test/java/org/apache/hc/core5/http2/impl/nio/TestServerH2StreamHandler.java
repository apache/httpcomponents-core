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
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestServerH2StreamHandler {

    @Mock
    H2StreamChannel channel;
    @Mock
    HttpProcessor httpProcessor;
    @Mock
    AsyncServerExchangeHandler exchangeHandler;
    @Mock
    HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;

    ServerH2StreamHandler handler;

    @BeforeEach
    void prepareMocks() {
        handler = new ServerH2StreamHandler(
                channel,
                httpProcessor,
                new BasicHttpConnectionMetrics(
                        new BasicHttpTransportMetrics(), new BasicHttpTransportMetrics()),
                exchangeHandlerFactory,
                HttpCoreContext.create());
    }

    @Test
    void defaults() {
        Assertions.assertThat(handler.getPushHandlerFactory()).isNull();
        Assertions.assertThat(handler.isOutputReady()).isFalse();
    }

    @Test
    void consumeDataBeforeHeadersRejected() {
        Assertions.assertThatThrownBy(() ->
                handler.consumeData(ByteBuffer.allocate(0), true)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void updateCapacityWithoutHandlerFails() {
        Assertions.assertThatThrownBy(() ->
                        handler.updateInputCapacity())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toStringIncludesStates() {
        final String text = handler.toString();
        Assertions.assertThat(text.contains("requestState")).isTrue();
        Assertions.assertThat(text.contains("responseState")).isTrue();
    }

    @Test
    void consumeTrailersWithPseudoHeaderRejected() throws Exception {
        Mockito.when(exchangeHandlerFactory.create(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(exchangeHandler);

        final List<Header> requestHeaders = Arrays.asList(
                new BasicHeader(":method", "POST"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(":path", "/upload"));
        handler.consumeHeader(requestHeaders, false);

        final List<Header> trailers = Collections.singletonList(new BasicHeader(":status", "200"));
        Assertions.assertThatThrownBy(() ->
                        handler.consumeHeader(trailers, true))
                .isInstanceOf(H2StreamResetException.class);
        Mockito.verify(exchangeHandler, Mockito.never()).streamEnd(ArgumentMatchers.anyList());
    }

    @Test
    void consumeTrailersWithoutEndStreamRejected() throws Exception {
        Mockito.when(exchangeHandlerFactory.create(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(exchangeHandler);

        final List<Header> requestHeaders = Arrays.asList(
                new BasicHeader(":method", "POST"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(":path", "/upload"));
        handler.consumeHeader(requestHeaders, false);

        final List<Header> trailers = Collections.singletonList(new BasicHeader("x-checksum", "abc123"));
        final H2StreamResetException ex = Assertions.assertThatThrownBy(() ->
                        handler.consumeHeader(trailers, false))
                .isInstanceOf(H2StreamResetException.class).asInstanceOf(InstanceOfAssertFactories.type(H2StreamResetException.class)).actual();
        Assertions.assertThat(H2Error.getByCode(ex.getCode())).isEqualTo(H2Error.PROTOCOL_ERROR);
        Mockito.verify(exchangeHandler, Mockito.never()).streamEnd(ArgumentMatchers.anyList());
    }

    @Test
    void contentLengthValid() throws Exception {
        Mockito.when(exchangeHandlerFactory.create(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(exchangeHandler);
        final List<Header> requestHeaders = Arrays.asList(
                new BasicHeader(":method", "POST"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(":path", "/upload"),
                new BasicHeader("content-length", "12"));
        handler.consumeHeader(requestHeaders, false);
        handler.consumeData(ByteBuffer.wrap(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }), false);
        handler.consumeData(ByteBuffer.wrap(new byte[] { 0, 1 }), true);
    }

    @Test
    void contentLengthInvalid() throws Exception {
        Mockito.when(exchangeHandlerFactory.create(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(exchangeHandler);
        final List<Header> requestHeaders = Arrays.asList(
                new BasicHeader(":method", "POST"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(":path", "/upload"),
                new BasicHeader("content-length", "12"));
        handler.consumeHeader(requestHeaders, false);
        handler.consumeData(ByteBuffer.wrap(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }), false);
        Assertions.assertThatThrownBy(() ->
                handler.consumeData(ByteBuffer.wrap(new byte[]{0, 1, 2}), true)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void contentLengthInvalidNoBody() throws Exception {
        final List<Header> requestHeaders = Arrays.asList(
                new BasicHeader(":method", "POST"),
                new BasicHeader(":scheme", "https"),
                new BasicHeader(":authority", "example.test"),
                new BasicHeader(":path", "/upload"),
                new BasicHeader("content-length", "12"));
        Assertions.assertThatThrownBy(() ->
                handler.consumeHeader(requestHeaders, true)).isInstanceOf(ProtocolException.class);
    }

}
