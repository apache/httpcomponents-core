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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestClientH2OriginEnforcement {

    private static final HttpHost INITIAL = new HttpHost("https", "primary.example", 443);
    private static final HttpHost ALT = new HttpHost("https", "assets.example", 443);

    @Test
    void rejectsAbsentOriginBeforeSubmittingHeaders() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        originSet.update(Collections.emptyList());
        final Fixture fixture = new Fixture(originSet, new BasicHttpRequest(Method.GET, ALT, "/asset.js"));

        Assertions.assertThrows(MisdirectedRequestException.class, fixture.handler::produceOutput);

        Mockito.verify(fixture.channel, Mockito.never()).submit(ArgumentMatchers.anyList(), ArgumentMatchers.anyBoolean());
    }

    @Test
    void submitsHeadersForAdvertisedOrigin() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        originSet.update(Collections.singleton(ALT));
        final Fixture fixture = new Fixture(originSet, new BasicHttpRequest(Method.GET, ALT, "/asset.js"));

        fixture.handler.produceOutput();

        Mockito.verify(fixture.channel).submit(ArgumentMatchers.anyList(), Mockito.eq(true));
    }

    @Test
    void response421RemovesRequestOrigin() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        originSet.update(Collections.singleton(ALT));
        final Fixture fixture = new Fixture(originSet, new BasicHttpRequest(Method.GET, ALT, "/asset.js"));
        fixture.handler.produceOutput();

        fixture.handler.consumeHeader(
                Collections.singletonList(new BasicHeader(":status", "421")), true);

        Assertions.assertFalse(originSet.snapshot().contains(ALT));
        Assertions.assertTrue(originSet.isInitialized());
    }

    @Test
    void refusesPushPromiseForOriginAbsentFromInitializedSet() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        originSet.update(Collections.emptyList());
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
        final ClientPushH2StreamHandler handler = new ClientPushH2StreamHandler(
                Mockito.mock(H2StreamChannel.class),
                Mockito.mock(HttpProcessor.class),
                new BasicHttpConnectionMetrics(
                        new BasicHttpTransportMetrics(), new BasicHttpTransportMetrics()),
                pushHandlerFactory,
                HttpCoreContext.create(),
                originSet);

        final H2StreamResetException ex = Assertions.assertThrows(H2StreamResetException.class, () ->
                handler.consumePromise(promiseHeaders(ALT)));

        Assertions.assertEquals(H2Error.REFUSED_STREAM.getCode(), ex.getCode());
        Mockito.verify(pushHandlerFactory, Mockito.never()).create(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private static List<Header> promiseHeaders(final HttpHost origin) {
        return Arrays.asList(
                new BasicHeader(":method", "GET"),
                new BasicHeader(":scheme", origin.getSchemeName()),
                new BasicHeader(":authority", origin.toHostString()),
                new BasicHeader(":path", "/asset.js"));
    }

    private static final class Fixture {

        private final H2StreamChannel channel;
        private final ClientH2StreamHandler handler;

        private Fixture(final H2OriginSet originSet, final BasicHttpRequest request) throws Exception {
            channel = Mockito.mock(H2StreamChannel.class);
            final HttpProcessor httpProcessor = Mockito.mock(HttpProcessor.class);
            final AsyncClientExchangeHandler exchangeHandler = Mockito.mock(AsyncClientExchangeHandler.class);
            @SuppressWarnings("unchecked")
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory =
                    (HandlerFactory<AsyncPushConsumer>) Mockito.mock(HandlerFactory.class);
            handler = new ClientH2StreamHandler(
                    channel,
                    httpProcessor,
                    new BasicHttpConnectionMetrics(
                            new BasicHttpTransportMetrics(), new BasicHttpTransportMetrics()),
                    exchangeHandler,
                    pushHandlerFactory,
                    HttpCoreContext.create(),
                    originSet);
            Mockito.doAnswer(invocation -> {
                final RequestChannel requestChannel = invocation.getArgument(0);
                requestChannel.sendRequest(request, null, invocation.getArgument(1));
                return null;
            }).when(exchangeHandler).produceRequest(ArgumentMatchers.any(), ArgumentMatchers.any());
        }
    }
}
