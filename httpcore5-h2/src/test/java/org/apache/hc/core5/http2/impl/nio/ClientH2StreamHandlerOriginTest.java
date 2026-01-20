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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.impl.BasicH2TransportMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClientH2StreamHandlerOriginTest {

    @Test
    void removesOriginOn421() throws Exception {
        final ClientH2StreamMultiplexer parent = mock(ClientH2StreamMultiplexer.class);
        final H2StreamChannel channel = mock(H2StreamChannel.class);
        final HttpProcessor httpProcessor = mock(HttpProcessor.class);
        final BasicHttpConnectionMetrics metrics = new BasicHttpConnectionMetrics(new BasicH2TransportMetrics(), new BasicH2TransportMetrics());
        final HttpCoreContext ctx = HttpCoreContext.create();

        final HttpRequest req = new BasicHttpRequest("GET", URI.create("https://Example.com/"));
        final AsyncClientExchangeHandler eh = new OneShot(req);

        final ClientH2StreamHandler h =
                new ClientH2StreamHandler(parent, channel, httpProcessor, metrics, eh, null, ctx);

        h.produceOutput();
        verify(channel).submit(anyList(), eq(true));

        when(parent.getOriginSetSnapshot()).thenReturn(Collections.<HttpHost>emptySet());

        h.consumeHeader(Collections.<Header>singletonList(new BasicHeader(":status", "421")), true);

        final ArgumentCaptor<HttpHost> cap = ArgumentCaptor.forClass(HttpHost.class);
        verify(parent).removeOrigin(cap.capture());
        final HttpHost o = cap.getValue();
        assertEquals("https", o.getSchemeName());
        assertEquals("example.com", o.getHostName());
        assertEquals(443, o.getPort());
    }

    @Test
    void throwsWhenOriginNotAllowed() throws Exception {
        final ClientH2StreamMultiplexer parent = mock(ClientH2StreamMultiplexer.class);
        final H2StreamChannel channel = mock(H2StreamChannel.class);
        final HttpProcessor httpProcessor = mock(HttpProcessor.class);
        final BasicHttpConnectionMetrics metrics = new BasicHttpConnectionMetrics(new BasicH2TransportMetrics(), new BasicH2TransportMetrics());
        final HttpCoreContext ctx = HttpCoreContext.create();

        final HttpRequest req = new BasicHttpRequest("GET", URI.create("https://blocked.example/"));
        final AsyncClientExchangeHandler eh = new OneShot(req);

        final ClientH2StreamHandler h =
                new ClientH2StreamHandler(parent, channel, httpProcessor, metrics, eh, null, ctx);

        h.produceOutput();

        final Set<HttpHost> initialized = new HashSet<HttpHost>();
        initialized.add(new HttpHost("https", "allowed.example", 443));
        when(parent.getOriginSetSnapshot()).thenReturn(initialized);
        when(parent.isOriginAllowed(any(HttpHost.class))).thenReturn(false);

        final ProtocolException ex = assertThrows(ProtocolException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        h.consumeHeader(Collections.<Header>singletonList(new BasicHeader(":status", "200")), true);
                    }
                });
        assertTrue(ex.getMessage().contains("Origin not allowed"));
    }

    @Test
    void preservesExplicitPortInAuthority() throws Exception {
        final ClientH2StreamMultiplexer parent = mock(ClientH2StreamMultiplexer.class);
        final H2StreamChannel channel = mock(H2StreamChannel.class);
        final HttpProcessor httpProcessor = mock(HttpProcessor.class);
        final BasicHttpConnectionMetrics metrics = new BasicHttpConnectionMetrics(new BasicH2TransportMetrics(), new BasicH2TransportMetrics());
        final HttpCoreContext ctx = HttpCoreContext.create();

        final HttpRequest req = new BasicHttpRequest("GET", URI.create("http://example.com:8080/"));
        final AsyncClientExchangeHandler eh = new OneShot(req);

        final ClientH2StreamHandler h =
                new ClientH2StreamHandler(parent, channel, httpProcessor, metrics, eh, null, ctx);

        h.produceOutput();
        verify(channel).submit(anyList(), eq(true));

        when(parent.getOriginSetSnapshot()).thenReturn(Collections.<HttpHost>emptySet());
        h.consumeHeader(Collections.<Header>singletonList(new BasicHeader(":status", "421")), true);

        final ArgumentCaptor<HttpHost> cap = ArgumentCaptor.forClass(HttpHost.class);
        verify(parent).removeOrigin(cap.capture());
        final HttpHost o = cap.getValue();
        assertEquals("http", o.getSchemeName());
        assertEquals("example.com", o.getHostName());
        assertEquals(8080, o.getPort());
    }

    private static final class OneShot implements AsyncClientExchangeHandler {
        private final HttpRequest request;

        OneShot(final HttpRequest request) {
            this.request = request;
        }

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
            channel.sendRequest(request, null, context);
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) {
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext context) {
        }

        @Override
        public void cancel() {

        }

        @Override
        public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context) {
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) {
        }

        @Override
        public void consume(final ByteBuffer src) {
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
        }

        @Override
        public void failed(final Exception cause) {
        }

        @Override
        public void releaseResources() {
        }
    }
}
