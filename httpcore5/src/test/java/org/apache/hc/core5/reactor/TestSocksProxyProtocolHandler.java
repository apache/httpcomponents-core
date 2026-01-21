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
package org.apache.hc.core5.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.nio.command.CommandSupport;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

class TestSocksProxyProtocolHandler {

    @Test
    @org.junit.jupiter.api.Timeout(5)
    void socksProxyEOFDuringConnectResponseCompletesSessionRequestExceptionally() throws Exception {
        final IOReactorConfig reactorConfig = IOReactorConfig.custom().build();

        final NamedEndpoint remoteEndpoint = new NamedEndpoint() {
            @Override
            public String getHostName() {
                return "example";
            }

            @Override
            public int getPort() {
                return 443;
            }
        };

        final SocketAddress targetAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);

        final IOSessionRequest sessionRequest = new IOSessionRequest(
                remoteEndpoint,
                targetAddress,
                null,
                Timeout.ofSeconds(1),
                null,
                null);

        // dataChannel + eventHandlerFactory are only used on COMPLETE; we never reach that in this regression.
        final SocksProxyProtocolHandler handler = new SocksProxyProtocolHandler(null, sessionRequest, null, reactorConfig);

        final TestIOSession session = new TestIOSession();
        assertEquals(0, session.getEventMask());

        // 1) Client sends auth methods
        handler.connected(session);
        assertEquals(SelectionKey.OP_WRITE, session.getEventMask());

        handler.outputReady(session);
        assertEquals(SelectionKey.OP_READ, session.getEventMask());

        // 2) Server replies: VER=5, METHOD=NO_AUTH(0)
        handler.inputReady(session, ByteBuffer.wrap(new byte[]{0x05, 0x00}));
        assertEquals(SelectionKey.OP_WRITE, session.getEventMask());

        // 3) Client sends CONNECT, then expects 2 bytes: VER + REP
        handler.outputReady(session);
        assertEquals(SelectionKey.OP_READ, session.getEventMask());

        // 4) Now simulate proxy closing the TCP connection: read() returns -1.
        final ConnectionClosedException ex = assertThrows(
                ConnectionClosedException.class,
                () -> handler.inputReady(session, null),
                "EOF during SOCKS handshake must fail the exchange");

        // This is what the reactor would do: route the exception to the handler.
        handler.exception(session, ex);

        assertTrue(sessionRequest.isDone(), "Session request future must be completed");
        final ExecutionException ee = assertThrows(ExecutionException.class, sessionRequest::get);
        assertSame(ex, ee.getCause(), "Cause must be the original EOF/close exception");

        assertEquals(CloseMode.IMMEDIATE, session.getLastCloseMode(), "Session must be closed immediately");
    }

    private static final class TestIOSession implements IOSession {

        private final Lock lock;
        private final Deque<Command> commands;
        private volatile boolean open;
        private volatile int eventMask;
        private volatile IOEventHandler handler;
        private volatile Timeout socketTimeout;
        private volatile long lastReadTime;
        private volatile long lastWriteTime;
        private volatile long lastEventTime;
        private volatile CloseMode lastCloseMode;

        TestIOSession() {
            this.lock = new ReentrantLock();
            this.commands = new ArrayDeque<>();
            this.open = true;
            this.eventMask = 0;
            this.socketTimeout = Timeout.DISABLED;
            this.lastReadTime = System.currentTimeMillis();
            this.lastWriteTime = this.lastReadTime;
            this.lastEventTime = this.lastReadTime;
        }

        CloseMode getLastCloseMode() {
            return this.lastCloseMode;
        }

        @Override
        public ByteChannel channel() {
            return this;
        }

        @Override
        public void setEventMask(final int ops) {
            this.eventMask = ops;
            this.lastEventTime = System.currentTimeMillis();
        }

        @Override
        public int getEventMask() {
            return this.eventMask;
        }

        @Override
        public void setEvent(final int op) {
            setEventMask(this.eventMask | op);
        }

        @Override
        public void clearEvent(final int op) {
            setEventMask(this.eventMask & ~op);
        }

        @Override
        public IOEventHandler getHandler() {
            return this.handler;
        }

        @Override
        public void upgrade(final IOEventHandler handler) {
            this.handler = handler;
        }

        @Override
        public Lock getLock() {
            return this.lock;
        }

        @Override
        public void enqueue(final Command command, final Command.Priority priority) {
            // not needed for this regression
            this.commands.add(command);
        }

        @Override
        public boolean hasCommands() {
            return !this.commands.isEmpty();
        }

        @Override
        public Command poll() {
            return this.commands.poll();
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
        public Timeout getSocketTimeout() {
            return this.socketTimeout;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            this.socketTimeout = timeout;
            this.lastEventTime = System.currentTimeMillis();
        }

        @Override
        public long getLastReadTime() {
            return this.lastReadTime;
        }

        @Override
        public long getLastWriteTime() {
            return this.lastWriteTime;
        }

        @Override
        public long getLastEventTime() {
            return this.lastEventTime;
        }

        @Override
        public void updateReadTime() {
            this.lastReadTime = System.currentTimeMillis();
            this.lastEventTime = this.lastReadTime;
        }

        @Override
        public void updateWriteTime() {
            this.lastWriteTime = System.currentTimeMillis();
            this.lastEventTime = this.lastWriteTime;
        }

        @Override
        public Status getStatus() {
            return this.open ? Status.ACTIVE : Status.CLOSED;
        }

        @Override
        public String getId() {
            return "test";
        }

        @Override
        public int read(final ByteBuffer dst) throws IOException {
            // Simulate EOF from proxy.
            return -1;
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int n = src.remaining();
            src.position(src.limit()); // drain
            updateWriteTime();
            return n;
        }

        @Override
        public boolean isOpen() {
            return this.open;
        }

        @Override
        public void close() {
            this.open = false;
        }

        @Override
        public void close(final CloseMode closeMode) {
            this.lastCloseMode = closeMode;
            this.open = false;
            // ensure any pending commands are failed/cancelled on close paths if they exist
            CommandSupport.cancelCommands(this);
        }
    }
}
