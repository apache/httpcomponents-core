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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.locks.Lock;

import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.testing.classic.Wire;
import org.slf4j.Logger;

public class LoggingIOSession implements IOSession {

    private final Logger log;
    private final Wire wirelog;
    private final IOSession session;
    private final ByteChannel channel;

    public LoggingIOSession(final IOSession session, final Logger log, final Logger wirelog) {
        super();
        this.session = session;
        this.log = log;
        this.wirelog = wirelog != null ? new Wire(wirelog, session.getId()) : null;
        this.channel = wirelog != null ? new LoggingByteChannel() : session.channel();
    }

    public LoggingIOSession(final IOSession session, final Logger log) {
        this(session, log, null);
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public Lock lock() {
        return this.session.lock();
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        this.session.enqueue(command, priority);
        if (this.log.isDebugEnabled()) {
            this.log.debug("Enqueued " + command.getClass().getSimpleName() + " with priority " + priority);
        }
    }

    @Override
    public boolean hasCommands() {
        return this.session.hasCommands();
    }

    @Override
    public Command poll() {
        return this.session.poll();
    }

    @Override
    public ByteChannel channel() {
        return this.channel;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return this.session.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.session.getRemoteAddress();
    }

    @Override
    public int getEventMask() {
        return this.session.getEventMask();
    }

    private static String formatOps(final int ops) {
        final StringBuilder buffer = new StringBuilder(6);
        buffer.append('[');
        if ((ops & SelectionKey.OP_READ) > 0) {
            buffer.append('r');
        }
        if ((ops & SelectionKey.OP_WRITE) > 0) {
            buffer.append('w');
        }
        if ((ops & SelectionKey.OP_ACCEPT) > 0) {
            buffer.append('a');
        }
        if ((ops & SelectionKey.OP_CONNECT) > 0) {
            buffer.append('c');
        }
        buffer.append(']');
        return buffer.toString();
    }

    @Override
    public void setEventMask(final int ops) {
        this.session.setEventMask(ops);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.session + " Event mask set " + formatOps(ops));
        }
    }

    @Override
    public void setEvent(final int op) {
        this.session.setEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.session + " Event set " + formatOps(op));
        }
    }

    @Override
    public void clearEvent(final int op) {
        this.session.clearEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.session + " Event cleared " + formatOps(op));
        }
    }

    @Override
    public void close() {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.session + " Close");
        }
        this.session.close();
    }

    @Override
    public int getStatus() {
        return this.session.getStatus();
    }

    @Override
    public boolean isClosed() {
        return this.session.isClosed();
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.session + " Shutdown " + shutdownType);
        }
        this.session.shutdown(shutdownType);
    }

    @Override
    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.session + " Set timeout " + timeout);
        }
        this.session.setSocketTimeout(timeout);
    }

    @Override
    public void updateReadTime() {
        this.session.updateReadTime();
    }

    @Override
    public void updateWriteTime() {
        this.session.updateWriteTime();
    }

    @Override
    public long getLastReadTime() {
        return this.session.getLastReadTime();
    }

    @Override
    public long getLastWriteTime() {
        return this.session.getLastWriteTime();
    }

    @Override
    public String toString() {
        return this.session.toString();
    }

    class LoggingByteChannel implements ByteChannel {

        @Override
        public int read(final ByteBuffer dst) throws IOException {
            final int bytesRead = session.channel().read(dst);
            if (log.isDebugEnabled()) {
                log.debug(session + " " + bytesRead + " bytes read");
            }
            if (bytesRead > 0 && wirelog.isEnabled()) {
                final ByteBuffer b = dst.duplicate();
                final int p = b.position();
                b.limit(p);
                b.position(p - bytesRead);
                wirelog.input(b);
            }
            return bytesRead;
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int byteWritten = session.channel().write(src);
            if (log.isDebugEnabled()) {
                log.debug(session + " " + byteWritten + " bytes written");
            }
            if (byteWritten > 0 && wirelog.isEnabled()) {
                final ByteBuffer b = src.duplicate();
                final int p = b.position();
                b.limit(p);
                b.position(p - byteWritten);
                wirelog.output(b);
            }
            return byteWritten;
        }

        @Override
        public void close() throws IOException {
            if (log.isDebugEnabled()) {
                log.debug(session + " Channel close");
            }
            session.channel().close();
        }

        @Override
        public boolean isOpen() {
            return session.channel().isOpen();
        }

    }

}
