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

package org.apache.http.contrib.logging;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionBufferStatus;

/**
 * Decorator class intended to transparently extend an {@link IOSession}
 * with basic event logging capabilities using Commons Logging.
 *
 */
public class LoggingIOSession implements IOSession {

    private static AtomicLong COUNT = new AtomicLong(0);

    private final Log log;
    private final Wire wirelog;
    private final IOSession session;
    private final ByteChannel channel;
    private final String id;

    public LoggingIOSession(final IOSession session, final String id) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("I/O session may not be null");
        }
        this.session = session;
        this.channel = new LoggingByteChannel();
        this.id = id + "-" + COUNT.incrementAndGet();
        this.log = LogFactory.getLog(session.getClass());
        this.wirelog = new Wire(LogFactory.getLog("org.apache.http.wire"));
    }

    public ByteChannel channel() {
        return this.channel;
    }

    public SocketAddress getLocalAddress() {
        return this.session.getLocalAddress();
    }

    public SocketAddress getRemoteAddress() {
        return this.session.getRemoteAddress();
    }

    public int getEventMask() {
        return this.session.getEventMask();
    }

    private static String formatOps(int ops) {
        StringBuffer buffer = new StringBuffer(6);
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

    public void setEventMask(int ops) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Set event mask "
                    + formatOps(ops));
        }
        this.session.setEventMask(ops);
    }

    public void setEvent(int op) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Set event "
                    + formatOps(op));
        }
        this.session.setEvent(op);
    }

    public void clearEvent(int op) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Clear event "
                    + formatOps(op));
        }
        this.session.clearEvent(op);
    }

    public void close() {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Close");
        }
        this.session.close();
    }

    public int getStatus() {
        return this.session.getStatus();
    }

    public boolean isClosed() {
        return this.session.isClosed();
    }

    public void shutdown() {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Shutdown");
        }
        this.session.shutdown();
    }

    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    public void setSocketTimeout(int timeout) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Set timeout "
                    + timeout);
        }
        this.session.setSocketTimeout(timeout);
    }

    public void setBufferStatus(final SessionBufferStatus status) {
        this.session.setBufferStatus(status);
    }

    public boolean hasBufferedInput() {
        return this.session.hasBufferedInput();
    }

    public boolean hasBufferedOutput() {
        return this.session.hasBufferedOutput();
    }

    public Object getAttribute(final String name) {
        return this.session.getAttribute(name);
    }

    public void setAttribute(final String name, final Object obj) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Set attribute "
                    + name);
        }
        this.session.setAttribute(name, obj);
    }

    public Object removeAttribute(final String name) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session " + this.id + " " + this.session + ": Remove attribute "
                    + name);
        }
        return this.session.removeAttribute(name);
    }

    class LoggingByteChannel implements ByteChannel {

        public int read(final ByteBuffer dst) throws IOException {
            int bytesRead = session.channel().read(dst);
            if (log.isDebugEnabled()) {
                log.debug("I/O session " + id + " " + session + ": " + bytesRead + " bytes read");
            }
            if (bytesRead > 0 && wirelog.isEnabled()) {
                ByteBuffer b = dst.duplicate();
                int p = b.position();
                b.limit(p);
                b.position(p - bytesRead);
                wirelog.input(b);
            }
            return bytesRead;
        }

        public int write(final ByteBuffer src) throws IOException {
            int byteWritten = session.channel().write(src);
            if (log.isDebugEnabled()) {
                log.debug("I/O session " + id + " " + session + ": " + byteWritten + " bytes written");
            }
            if (byteWritten > 0 && wirelog.isEnabled()) {
                ByteBuffer b = src.duplicate();
                int p = b.position();
                b.limit(p);
                b.position(p - byteWritten);
                wirelog.output(b);
            }
            return byteWritten;
        }

        public void close() throws IOException {
            if (log.isDebugEnabled()) {
                log.debug("I/O session " + id + " " + session + ": Channel close");
            }
            session.channel().close();
        }

        public boolean isOpen() {
            return session.channel().isOpen();
        }

    }

}