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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.testing.classic.Wire;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

@Internal
public class LoggingIOSession implements IOSession {

    private final Logger log;
    private final Wire wireLog;
    private final IOSession session;

    public LoggingIOSession(final IOSession session, final Logger log, final Logger wireLog) {
        super();
        this.session = session;
        this.log = log;
        this.wireLog = wireLog != null ? new Wire(wireLog, session.getId()) : null;
    }

    public LoggingIOSession(final ProtocolIOSession session, final Logger log) {
        this(session, log, null);
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public Lock getLock() {
        return this.session.getLock();
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        this.session.enqueue(command, priority);
        if (this.log.isDebugEnabled()) {
            this.log.debug("{} enqueued {} with priority {}", this.session, command.getClass().getSimpleName(), priority);
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
        return this.session.channel();
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
            this.log.debug("{} event mask set {}", this.session, formatOps(ops));
        }
    }

    @Override
    public void setEvent(final int op) {
        this.session.setEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug("{} event set {}", this.session, formatOps(op));
        }
    }

    @Override
    public void clearEvent(final int op) {
        this.session.clearEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug("{} event cleared {}", this.session, formatOps(op));
        }
    }

    @Override
    public void close() {
        if (this.log.isDebugEnabled()) {
            this.log.debug("{} close", this.session);
        }
        this.session.close();
    }

    @Override
    public Status getStatus() {
        return this.session.getStatus();
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("{} shutdown {}", this.session, closeMode);
        }
        this.session.close(closeMode);
    }

    @Override
    public Timeout getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("{} set timeout {}", this.session, timeout);
        }
        this.session.setSocketTimeout(timeout);
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        final int bytesRead = session.read(dst);
        if (log.isDebugEnabled()) {
            log.debug("{} {} bytes read", session, bytesRead);
        }
        if (bytesRead > 0 && wireLog.isEnabled()) {
            final ByteBuffer b = dst.duplicate();
            final int p = b.position();
            b.limit(p);
            b.position(p - bytesRead);
            wireLog.input(b);
        }
        return bytesRead;
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        final int byteWritten = session.write(src);
        if (log.isDebugEnabled()) {
            log.debug("{} {} bytes written", session, byteWritten);
        }
        if (byteWritten > 0 && wireLog.isEnabled()) {
            final ByteBuffer b = src.duplicate();
            final int p = b.position();
            b.limit(p);
            b.position(p - byteWritten);
            wireLog.output(b);
        }
        return byteWritten;
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
    public long getLastEventTime() {
        return this.session.getLastEventTime();
    }

    @Override
    public IOEventHandler getHandler() {
        return this.session.getHandler();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("{} protocol upgrade: {}", this.session, handler != null ? handler.getClass() : null);
        }
        this.session.upgrade(handler);
    }

    @Override
    public String toString() {
        return this.session.toString();
    }

}
