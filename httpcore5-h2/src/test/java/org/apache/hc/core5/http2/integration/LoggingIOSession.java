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

package org.apache.hc.core5.http2.integration;

import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.Deque;

import org.apache.commons.logging.Log;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.SessionBufferStatus;

/**
 * Decorator class intended to transparently extend an {@link IOSession}
 * with basic event logging capabilities using Commons Logging.
 */
class LoggingIOSession implements IOSession {

    private final Log log;
    private final String id;
    private final IOSession session;

    LoggingIOSession(final IOSession session, final String id, final Log log) {
        super();
        this.session = session;
        this.id = id;
        this.log = log;
    }

    @Override
    public Deque<Command> getCommandQueue() {
        return this.session.getCommandQueue();
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
            this.log.debug(this.id + " " + this.session + ": Event mask set " + formatOps(ops));
        }
    }

    @Override
    public void setEvent(final int op) {
        this.session.setEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Event set " + formatOps(op));
        }
    }

    @Override
    public void clearEvent(final int op) {
        this.session.clearEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Event cleared " + formatOps(op));
        }
    }

    @Override
    public void close() {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Close");
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
    public void shutdown() {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Shutdown");
        }
        this.session.shutdown();
    }

    @Override
    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Set timeout " + timeout);
        }
        this.session.setSocketTimeout(timeout);
    }

    @Override
    public void setBufferStatus(final SessionBufferStatus status) {
        this.session.setBufferStatus(status);
    }

    @Override
    public boolean hasBufferedInput() {
        return this.session.hasBufferedInput();
    }

    @Override
    public boolean hasBufferedOutput() {
        return this.session.hasBufferedOutput();
    }

    @Override
    public Object getAttribute(final String name) {
        return this.session.getAttribute(name);
    }

    @Override
    public void setAttribute(final String name, final Object obj) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Set attribute " + name);
        }
        this.session.setAttribute(name, obj);
    }

    @Override
    public Object removeAttribute(final String name) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Remove attribute " + name);
        }
        return this.session.removeAttribute(name);
    }

    @Override
    public IOEventHandler getHandler() {
        return this.session.getHandler();
    }

    @Override
    public void setHandler(final IOEventHandler handler) {
        this.session.setHandler(handler);
    }

    @Override
    public String toString() {
        return this.id + " " + this.session.toString();
    }

}
