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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Default implementation of {@link IOSession}.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
class IOSessionImpl implements IOSession, SocketAccessor {

    private final SelectionKey key;
    private final SocketChannel channel;
    private final Map<String, Object> attributes;
    private final Queue<IOSession> closedSessions;

    private final long startedTime;
    private final AtomicInteger status;
    private final AtomicInteger eventMask;

    private volatile IOEventHandler eventHandler;
    private volatile SessionBufferStatus bufferStatus;
    private volatile int socketTimeout;

    private volatile long lastReadTime;
    private volatile long lastWriteTime;
    private volatile long lastAccessTime;

    /**
     * Creates new instance of IOSessionImpl.
     *
     * @param key the selection key.
     * @param socketChannel the socket channel
     * @param closedSessions the queue containing closed sessions
     *
     * @since 4.1
     */
    public IOSessionImpl(
            final SelectionKey key,
            final SocketChannel socketChannel,
            final Queue<IOSession> closedSessions) {
        super();
        this.key = Args.notNull(key, "Selection key");
        this.channel = Args.notNull(socketChannel, "Socket channel");
        this.closedSessions = closedSessions;
        this.attributes = new ConcurrentHashMap<>();
        this.socketTimeout = 0;
        this.eventMask = new AtomicInteger(key.interestOps());
        this.status = new AtomicInteger(ACTIVE);
        final long now = System.currentTimeMillis();
        this.startedTime = now;
        this.lastReadTime = now;
        this.lastWriteTime = now;
        this.lastAccessTime = now;
    }

    @Override
    public IOEventHandler getHandler() {
        return this.eventHandler;
    }

    @Override
    public void setHandler(final IOEventHandler handler) {
        this.eventHandler = handler;
    }

    @Override
    public ByteChannel channel() {
        return this.channel;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return this.channel.socket().getLocalSocketAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.channel.socket().getRemoteSocketAddress();
    }

    @Override
    public int getEventMask() {
        return this.key.interestOps();
    }

    @Override
    public void setEventMask(final int newValue) {
        if (this.status.get() == CLOSED) {
            return;
        }
        final int currentValue = this.eventMask.get();
        if (newValue == currentValue) {
            return;
        }
        if (this.eventMask.compareAndSet(currentValue, newValue)) {
            this.key.interestOps(newValue);
            this.key.selector().wakeup();
        }
    }

    @Override
    public void setEvent(final int op) {
        if (this.status.get() == CLOSED) {
            return;
        }
        for (;;) {
            final int currentValue = this.eventMask.get();
            final int newValue = currentValue | op;
            if (this.eventMask.compareAndSet(currentValue, newValue)) {
                this.key.interestOps(newValue);
                this.key.selector().wakeup();
                return;
            }
        }
    }

    @Override
    public void clearEvent(final int op) {
        if (this.status.get() == CLOSED) {
            return;
        }
        for (;;) {
            final int currentValue = this.eventMask.get();
            final int newValue = currentValue & ~op;
            if (this.eventMask.compareAndSet(currentValue, newValue)) {
                this.key.interestOps(newValue);
                this.key.selector().wakeup();
                return;
            }
        }
    }

    @Override
    public int getSocketTimeout() {
        return this.socketTimeout;
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        this.socketTimeout = timeout;
        this.lastAccessTime = System.currentTimeMillis();
    }

    @Override
    public void close() {
        if (this.status.compareAndSet(ACTIVE, CLOSED)) {
            if (this.closedSessions != null) {
                this.closedSessions.add(this);
            }
            this.key.cancel();
            this.key.attach(null);
            try {
                this.key.channel().close();
            } catch (final IOException ignore) {
            }
            if (this.key.selector().isOpen()) {
                this.key.selector().wakeup();
            }
        }
    }

    @Override
    public int getStatus() {
        return this.status.get();
    }

    @Override
    public boolean isClosed() {
        return this.status.get() == CLOSED;
    }

    @Override
    public void shutdown() {
        // For this type of session, a close() does exactly
        // what we need and nothing more.
        close();
    }

    @Override
    public boolean hasBufferedInput() {
        final SessionBufferStatus buffStatus = this.bufferStatus;
        return buffStatus != null && buffStatus.hasBufferedInput();
    }

    @Override
    public boolean hasBufferedOutput() {
        final SessionBufferStatus buffStatus = this.bufferStatus;
        return buffStatus != null && buffStatus.hasBufferedOutput();
    }

    @Override
    public void setBufferStatus(final SessionBufferStatus bufferStatus) {
        this.bufferStatus = bufferStatus;
    }

    @Override
    public Object getAttribute(final String name) {
        return this.attributes.get(name);
    }

    @Override
    public Object removeAttribute(final String name) {
        return this.attributes.remove(name);
    }

    @Override
    public void setAttribute(final String name, final Object obj) {
        if (obj == null) {
            this.attributes.remove(name);
        } else {
            this.attributes.put(name, obj);
        }
    }

    public long getStartedTime() {
        return this.startedTime;
    }

    public long getLastReadTime() {
        return this.lastReadTime;
    }

    public long getLastWriteTime() {
        return this.lastWriteTime;
    }

    public long getLastAccessTime() {
        return this.lastAccessTime;
    }

    void resetLastRead() {
        final long now = System.currentTimeMillis();
        this.lastReadTime = now;
        this.lastAccessTime = now;
    }

    void resetLastWrite() {
        final long now = System.currentTimeMillis();
        this.lastWriteTime = now;
        this.lastAccessTime = now;
    }

    private static void formatOps(final StringBuilder buffer, final int ops) {
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
    }

    private static void formatAddress(final StringBuilder buffer, final SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            final InetSocketAddress addr = (InetSocketAddress) socketAddress;
            buffer.append(addr.getAddress() != null ? addr.getAddress().getHostAddress() :
                addr.getAddress())
            .append(':')
            .append(addr.getPort());
        } else {
            buffer.append(socketAddress);
        }
    }

    @Override
    public Socket getSocket() {
        if (this.channel instanceof SocketChannel) {
            return ((SocketChannel) this.channel).socket();
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        final SocketAddress remoteAddress = getRemoteAddress();
        final SocketAddress localAddress = getLocalAddress();
        if (remoteAddress != null && localAddress != null) {
            formatAddress(buffer, localAddress);
            buffer.append("<->");
            formatAddress(buffer, remoteAddress);
        }
        buffer.append("[");
        switch (this.status.get()) {
        case ACTIVE:
            buffer.append("ACTIVE");
            break;
        case CLOSING:
            buffer.append("CLOSING");
            break;
        case CLOSED:
            buffer.append("CLOSED");
            break;
        }
        buffer.append("][");
        if (this.key.isValid()) {
            formatOps(buffer, this.key.interestOps());
            buffer.append(":");
            formatOps(buffer, this.key.readyOps());
        }
        buffer.append("]");
        return buffer.toString();
    }

}
