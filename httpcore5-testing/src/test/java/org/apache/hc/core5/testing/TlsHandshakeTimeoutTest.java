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

package org.apache.hc.core5.testing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.Host;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLIOSession;
import org.apache.hc.core5.reactor.ssl.SSLMode;
import org.apache.hc.core5.reactor.ssl.TlsHandshakeTimeoutException;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

/**
 * Unit test for TLS handshake timeout handling in SSLIOSession.
 */
public class TlsHandshakeTimeoutTest {

    @Test
    void testHandshakeTimeoutTriggersTlsHandshakeTimeoutException() throws Exception {
        // Use concrete Host since NamedEndpoint is an interface
        final Host endpoint = new Host("localhost", 443);
        // Create a client SSL context
        final SSLContext sslContext = SSLTestContexts.createClientSSLContext();

        // Capture exception from handshake
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final FutureCallback<javax.net.ssl.SSLSession> callback = new FutureCallback<javax.net.ssl.SSLSession>() {
            @Override
            public void completed(final javax.net.ssl.SSLSession result) {
                // Should not complete successfully
                failure.set(new RuntimeException("Handshake should not complete"));
            }

            @Override
            public void failed(final Exception ex) {
                failure.set(ex);
            }

            @Override
            public void cancelled() {
                failure.set(new RuntimeException("Handshake cancelled"));
            }
        };

        // Stub IOSession with small socket timeout
        final TestIOSession mockSession = new TestIOSession(Timeout.ofSeconds(1));

        // Create SSLIOSession with a tiny handshake timeout
        final SSLIOSession sslioSession = new SSLIOSession(
                endpoint,
                mockSession,
                SSLMode.CLIENT,
                sslContext,
                SSLBufferMode.STATIC,
                null,
                null,
                Timeout.ofMilliseconds(10),
                null,
                null,
                callback
        );

        // Start the handshake process
        sslioSession.beginHandshake(mockSession);

        // Simulate a timeout event after handshakeTimeout
        sslioSession.getHandler().timeout(mockSession, Timeout.ofMilliseconds(10));

        // Assert that our callback received a TlsHandshakeTimeoutException
        final Exception ex = failure.get();
        assertNotNull(ex, "Expected handshake failure");
        Throwable cause = ex;
        while (cause != null && !(cause instanceof TlsHandshakeTimeoutException)) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof TlsHandshakeTimeoutException,
                "Expected TlsHandshakeTimeoutException but got: " + ex);
    }

    /**
     * Minimal IOSession stub for testing SSLIOSession handshake timeout logic.
     */
    static class TestIOSession implements IOSession {
        private Timeout socketTimeout;
        private IOEventHandler handler;
        private final Lock lock = new ReentrantLock();

        TestIOSession(final Timeout socketTimeout) {
            this.socketTimeout = socketTimeout;
            // default no-op handler
            this.handler = new IOEventHandler() {
                @Override
                public void connected(final IOSession session) {
                }

                @Override
                public void inputReady(final IOSession session, final ByteBuffer src) {
                }

                @Override
                public void outputReady(final IOSession session) {
                }

                @Override
                public void timeout(final IOSession session, final Timeout timeout) {
                }

                @Override
                public void exception(final IOSession session, final Exception ex) {
                }

                @Override
                public void disconnected(final IOSession session) {
                }
            };
        }

        @Override
        public IOEventHandler getHandler() {
            return handler;
        }

        @Override
        public void upgrade(final IOEventHandler handler) {
            this.handler = handler;
        }

        @Override
        public Lock getLock() {
            return lock;
        }

        @Override
        public String getId() {
            return "test-session";
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
        }

        @Override
        public void close(final org.apache.hc.core5.io.CloseMode closeMode) {
        }

        @Override
        public Status getStatus() {
            return Status.ACTIVE;
        }

        @Override
        public java.nio.channels.ByteChannel channel() {
            return new java.nio.channels.ByteChannel() {
                @Override
                public int read(final ByteBuffer dst) throws IOException {
                    return 0;
                }

                @Override
                public int write(final ByteBuffer src) throws IOException {
                    return 0;
                }

                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public void close() throws IOException {
                }
            };
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public int getEventMask() {
            return 0;
        }

        @Override
        public void setEventMask(final int ops) {
        }

        @Override
        public void setEvent(final int op) {
        }

        @Override
        public void clearEvent(final int op) {
        }

        @Override
        public Timeout getSocketTimeout() {
            return socketTimeout;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            this.socketTimeout = timeout;
        }

        @Override
        public void updateReadTime() {
        }

        @Override
        public void updateWriteTime() {
        }

        @Override
        public long getLastReadTime() {
            return 0;
        }

        @Override
        public long getLastWriteTime() {
            return 0;
        }

        @Override
        public long getLastEventTime() {
            return 0;
        }

        @Override
        public void enqueue(final org.apache.hc.core5.reactor.Command command, final org.apache.hc.core5.reactor.Command.Priority priority) {
        }

        @Override
        public boolean hasCommands() {
            return false;
        }

        @Override
        public org.apache.hc.core5.reactor.Command poll() {
            return null;
        }

        @Override
        public int read(final ByteBuffer byteBuffer) throws IOException {
            return 0;
        }

        @Override
        public int write(final ByteBuffer byteBuffer) throws IOException {
            return 0;
        }
    }
}
