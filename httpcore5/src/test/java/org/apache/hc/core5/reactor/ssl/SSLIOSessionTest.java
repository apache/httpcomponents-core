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
package org.apache.hc.core5.reactor.ssl;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;
import java.security.Provider;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SSLIOSessionTest {

    // Define common variables here, so you can easily modify them in each test
    private NamedEndpoint targetEndpoint;
    private IOSession ioSession;
    private SSLMode sslMode;
    private SSLContext sslContext;
    private SSLBufferMode sslBufferMode;
    private SSLSessionInitializer initializer;
    private SSLSessionVerifier verifier;
    private Timeout handshakeTimeout;
    private Callback<SSLIOSession> sessionStartCallback;
    private Callback<SSLIOSession> sessionEndCallback;
    private FutureCallback<SSLSession> resultCallback;
    private IOEventHandler ioEventHandler;
    private SSLEngine mockSSLEngine;
    private SSLSession sslSession;

    @BeforeEach
    public void setUp() throws SSLException {
        final String protocol = "TestProtocol";

        // Arrange
        targetEndpoint = mock(NamedEndpoint.class);
        ioSession = mock(IOSession.class);
        sslMode = SSLMode.CLIENT;  // Use actual SSLMode

        //SSLContext sslContext = SSLContext.getDefault();
        final SSLContextSpi sslContextSpi = mock(SSLContextSpi.class);
        final Provider provider = mock(Provider.class);
        sslContext = new TestSSLContext(sslContextSpi, provider, protocol);

        sslSession = mock(SSLSession.class);

        sslBufferMode = SSLBufferMode.STATIC;
        initializer = mock(SSLSessionInitializer.class);
        verifier = mock(SSLSessionVerifier.class);
        handshakeTimeout = mock(Timeout.class);
        sessionStartCallback = mock(Callback.class);
        sessionEndCallback = mock(Callback.class);
        resultCallback = mock(FutureCallback.class);
        ioEventHandler = mock(IOEventHandler.class);

        // Mock behavior of targetEndpoint
        Mockito.when(targetEndpoint.getHostName()).thenReturn("testHostName");
        Mockito.when(targetEndpoint.getPort()).thenReturn(8080);

        Mockito.when(sslSession.getPacketBufferSize()).thenReturn(1024);
        Mockito.when(sslSession.getApplicationBufferSize()).thenReturn(1024);

        // Mock behavior of ioSession
        Mockito.when(ioSession.getEventMask()).thenReturn(1);
        Mockito.when(ioSession.getLock()).thenReturn(new ReentrantLock());

        // Mock behavior of sslContext and SSLEngine
        mockSSLEngine = mock(SSLEngine.class);
        Mockito.when(sslContext.createSSLEngine(any(String.class), any(Integer.class))).thenReturn(mockSSLEngine);
        Mockito.when(mockSSLEngine.getSession()).thenReturn(sslSession);
        Mockito.when(mockSSLEngine.getHandshakeStatus()).thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        Mockito.when(ioSession.getHandler()).thenReturn(ioEventHandler);

        final SSLEngineResult mockResult = new SSLEngineResult(SSLEngineResult.Status.CLOSED,
                SSLEngineResult.HandshakeStatus.FINISHED,
                0, 464);
        Mockito.when(mockSSLEngine.wrap(any(ByteBuffer.class), any(ByteBuffer.class))).thenReturn(mockResult);
    }


    @Test
    void testConstructorWhenSSLEngineOk() {
        final String protocol = "TestProtocol";
        // Arrange
        Mockito.when(mockSSLEngine.getApplicationProtocol()).thenReturn(protocol);

        // Act
        final TestableSSLIOSession sslioSession = new TestableSSLIOSession(targetEndpoint, ioSession, sslMode, sslContext,
                sslBufferMode, initializer, verifier, handshakeTimeout, sessionStartCallback, sessionEndCallback,
                resultCallback);

        // Assert
        Assertions.assertDoesNotThrow(() -> sslioSession.beginHandshake(ioSession));
        Assertions.assertEquals(protocol, sslioSession.getTlsDetails().getApplicationProtocol());
    }

    @Test
    void testConstructorWhenSSLEngineThrowsException() {
        final String protocol = "http/1.1";
        // Arrange
        Mockito.when(mockSSLEngine.getApplicationProtocol()).thenThrow(UnsupportedOperationException.class);

        // Act
        final TestableSSLIOSession sslioSession = new TestableSSLIOSession(targetEndpoint, ioSession, sslMode, sslContext,
                sslBufferMode, initializer, verifier, handshakeTimeout, sessionStartCallback, sessionEndCallback,
                resultCallback);

        // Assert
        Assertions.assertDoesNotThrow(() -> sslioSession.beginHandshake(ioSession));
        Assertions.assertEquals(protocol, sslioSession.getTlsDetails().getApplicationProtocol());
    }


    static class TestSSLContext extends SSLContext {

        /**
         * Creates an SSLContext object.
         *
         * @param contextSpi the delegate
         * @param provider   the provider
         * @param protocol   the protocol
         */
        protected TestSSLContext(final SSLContextSpi contextSpi, final Provider provider, final String protocol) {
            super(contextSpi, provider, protocol);
        }
    }

    static class TestableSSLIOSession extends SSLIOSession {
        TestableSSLIOSession(final NamedEndpoint targetEndpoint, final IOSession session, final SSLMode sslMode, final SSLContext sslContext,
                             final SSLBufferMode sslBufferMode, final SSLSessionInitializer initializer, final SSLSessionVerifier verifier,
                             final Timeout handshakeTimeout, final Callback<SSLIOSession> sessionStartCallback,
                             final Callback<SSLIOSession> sessionEndCallback, final FutureCallback<SSLSession> resultCallback) {
            super(targetEndpoint, session, sslMode, sslContext, sslBufferMode, initializer, verifier,
                    handshakeTimeout, sessionStartCallback, sessionEndCallback, resultCallback);
        }
    }

}


