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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestPrefaceHandlerBase {

    private static class TestHandler extends PrefaceHandlerBase {
        TestHandler(final ProtocolIOSession ioSession,
                    final FutureCallback<ProtocolIOSession> resultCallback,
                    final Callback<Exception> exceptionCallback) {
            super(ioSession, resultCallback, exceptionCallback);
        }

        @Override
        public void connected(final IOSession session) {
        }

        @Override
        public void inputReady(final IOSession session, final ByteBuffer buffer) {
        }

        @Override
        public void outputReady(final IOSession session) {
        }
    }

    @Test
    void startProtocolUpgradesAndSignals() throws Exception {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        @SuppressWarnings("unchecked")
        final FutureCallback<ProtocolIOSession> resultCallback =
                (FutureCallback<ProtocolIOSession>) Mockito.mock(FutureCallback.class);
        try (TestHandler handler = new TestHandler(ioSession, resultCallback, null)) {

            final HttpConnectionEventHandler protocolHandler = Mockito.mock(HttpConnectionEventHandler.class);
            final ByteBuffer data = ByteBuffer.wrap(new byte[] {1, 2, 3});

            handler.startProtocol(protocolHandler, data);

            Mockito.verify(ioSession).upgrade(protocolHandler);
            Mockito.verify(protocolHandler).connected(ioSession);
            Mockito.verify(protocolHandler).inputReady(ioSession, data);
            Mockito.verify(resultCallback).completed(ioSession);
        }
    }

    @Test
    void exceptionDelegatesToProtocolHandler() throws Exception {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        try (TestHandler handler = new TestHandler(ioSession, null, null)) {
            final HttpConnectionEventHandler protocolHandler = Mockito.mock(HttpConnectionEventHandler.class);

            handler.startProtocol(protocolHandler, null);
            final RuntimeException cause = new RuntimeException("boom");
            handler.exception(ioSession, cause);

            Mockito.verify(ioSession).close(CloseMode.IMMEDIATE);
            Mockito.verify(protocolHandler).exception(Mockito.eq(ioSession), Mockito.eq(cause));
        }
    }

    @Test
    void disconnectedWithoutProtocolHandlerFailsCallback() throws Exception {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        final AtomicReference<Exception> failed = new AtomicReference<>();
        final FutureCallback<ProtocolIOSession> callback = new FutureCallback<ProtocolIOSession>() {
            @Override
            public void completed(final ProtocolIOSession result) {
            }

            @Override
            public void failed(final Exception ex) {
                failed.set(ex);
            }

            @Override
            public void cancelled() {
            }
        };
        try (TestHandler handler = new TestHandler(ioSession, callback, null)) {

            handler.disconnected(ioSession);

            Assertions.assertTrue(failed.get() instanceof ConnectionClosedException);
        }
    }

    @Test
    void sslSessionIsExposed() throws Exception {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        final SSLSession sslSession = Mockito.mock(SSLSession.class);
        final TlsDetails tlsDetails = new TlsDetails(sslSession, "h2");
        Mockito.when(ioSession.getTlsDetails()).thenReturn(tlsDetails);

        try (TestHandler handler = new TestHandler(ioSession, null, null)) {

            Assertions.assertSame(sslSession, handler.getSSLSession());
        }
    }

    @Test
    void delegatesSessionProperties() throws Exception {
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getSocketTimeout()).thenReturn(Timeout.ofSeconds(2));
        Mockito.when(ioSession.getRemoteAddress()).thenReturn(new InetSocketAddress("localhost", 80));
        Mockito.when(ioSession.getLocalAddress()).thenReturn(new InetSocketAddress("localhost", 0));
        Mockito.when(ioSession.isOpen()).thenReturn(true);

        try (TestHandler handler = new TestHandler(ioSession, null, null)) {

            Assertions.assertEquals(Timeout.ofSeconds(2), handler.getSocketTimeout());
            handler.setSocketTimeout(Timeout.ofSeconds(1));
            Mockito.verify(ioSession).setSocketTimeout(Timeout.ofSeconds(1));
            Assertions.assertEquals(new InetSocketAddress("localhost", 80), handler.getRemoteAddress());
            Assertions.assertEquals(new InetSocketAddress("localhost", 0), handler.getLocalAddress());
            Assertions.assertTrue(handler.isOpen());
            final ProtocolVersion protocolVersion = handler.getProtocolVersion();
            Assertions.assertNotNull(protocolVersion);
            handler.close();
            handler.close(CloseMode.GRACEFUL);
            Mockito.verify(ioSession).close();
            Mockito.verify(ioSession).close(CloseMode.GRACEFUL);
        }
    }

}
