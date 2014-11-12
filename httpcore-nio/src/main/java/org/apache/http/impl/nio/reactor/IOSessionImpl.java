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

package org.apache.http.impl.nio.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionBufferStatus;
import org.apache.http.nio.reactor.SocketAccessor;
import org.apache.http.util.Args;

/**
 * Default implementation of {@link IOSession}.
 *
 * @since 4.0
 */
@ThreadSafe
public class IOSessionImpl implements IOSession, SocketAccessor {

    private final SelectionKey key;
    private final ByteChannel channel;
    private final Map<String, Object> attributes;
    private final InterestOpsCallback interestOpsCallback;
    private final SessionClosedCallback sessionClosedCallback;

    private volatile int status;
    private volatile int currentEventMask;
    private volatile SessionBufferStatus bufferStatus;
    private volatile int socketTimeout;

    private final long startedTime;

    private volatile long lastReadTime;
    private volatile long lastWriteTime;
    private volatile long lastAccessTime;

    /**
     * Creates new instance of IOSessionImpl.
     *
     * @param key the selection key.
     * @param interestOpsCallback interestOps callback.
     * @param sessionClosedCallback session closed callback.
     *
     * @since 4.1
     */
    public IOSessionImpl(
            final SelectionKey key,
            final InterestOpsCallback interestOpsCallback,
            final SessionClosedCallback sessionClosedCallback) {
        super();
        Args.notNull(key, "Selection key");
        this.key = key;
        this.channel = (ByteChannel) this.key.channel();
        this.interestOpsCallback = interestOpsCallback;
        this.sessionClosedCallback = sessionClosedCallback;
        this.attributes = Collections.synchronizedMap(new HashMap<String, Object>());
        this.currentEventMask = key.interestOps();
        this.socketTimeout = 0;
        this.status = ACTIVE;
        final long now = System.currentTimeMillis();
        this.startedTime = now;
        this.lastReadTime = now;
        this.lastWriteTime = now;
        this.lastAccessTime = now;
    }

    /**
     * Creates new instance of IOSessionImpl.
     *
     * @param key the selection key.
     * @param sessionClosedCallback session closed callback.
     */
    public IOSessionImpl(
            final SelectionKey key,
            final SessionClosedCallback sessionClosedCallback) {
        this(key, null, sessionClosedCallback);
    }

    @Override
    public ByteChannel channel() {
        return this.channel;
    }

    @Override
    public SocketAddress getLocalAddress() {
        if (this.channel instanceof SocketChannel) {
            return ((SocketChannel)this.channel).socket().getLocalSocketAddress();
        } else {
            return null;
        }
    }

    @Override
    public SocketAddress getRemoteAddress() {
        if (this.channel instanceof SocketChannel) {
            return ((SocketChannel)this.channel).socket().getRemoteSocketAddress();
        } else {
            return null;
        }
    }

    @Override
    public int getEventMask() {
        return this.interestOpsCallback != null ? this.currentEventMask : this.key.interestOps();
    }

    @Override
    public synchronized void setEventMask(final int ops) {
        if (this.status == CLOSED) {
            return;
        }
        if (this.interestOpsCallback != null) {
            // update the current event mask
            this.currentEventMask = ops;

            // local variable
            final InterestOpEntry entry = new InterestOpEntry(this.key, this.currentEventMask);

            // add this operation to the interestOps() queue
            this.interestOpsCallback.addInterestOps(entry);
        } else {
            this.key.interestOps(ops);
        }
        this.key.selector().wakeup();
    }

    @Override
    public synchronized void setEvent(final int op) {
        if (this.status == CLOSED) {
            return;
        }
        if (this.interestOpsCallback != null) {
            // update the current event mask
            this.currentEventMask |= op;

            // local variable
            final InterestOpEntry entry = new InterestOpEntry(this.key, this.currentEventMask);

            // add this operation to the interestOps() queue
            this.interestOpsCallback.addInterestOps(entry);
        } else {
            final int ops = this.key.interestOps();
            this.key.interestOps(ops | op);
        }
        this.key.selector().wakeup();
    }

    @Override
    public synchronized void clearEvent(final int op) {
        if (this.status == CLOSED) {
            return;
        }
        if (this.interestOpsCallback != null) {
            // update the current event mask
            this.currentEventMask &= ~op;

            // local variable
            final InterestOpEntry entry = new InterestOpEntry(this.key, this.currentEventMask);

            // add this operation to the interestOps() queue
            this.interestOpsCallback.addInterestOps(entry);
        } else {
            final int ops = this.key.interestOps();
            this.key.interestOps(ops & ~op);
        }
        this.key.selector().wakeup();
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
        synchronized (this) {
            if (this.status == CLOSED) {
                return;
            }
            this.status = CLOSED;
        }
        synchronized (this.key) {
            this.key.cancel();
            try {
                this.key.channel().close();
            } catch (final IOException ex) {
                // Munching exceptions is not nice
                // but in this case it is justified
            }
            if (this.sessionClosedCallback != null) {
                this.sessionClosedCallback.sessionClosed(this);
            }
            if (this.key.selector().isOpen()) {
                this.key.selector().wakeup();
            }
        }
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    @Override
    public boolean isClosed() {
        return this.status == CLOSED;
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
        this.attributes.put(name, obj);
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
            final InetSocketAddress addr = ((InetSocketAddress) socketAddress);
            buffer.append(addr.getAddress() != null ? addr.getAddress().getHostAddress() :
                addr.getAddress())
            .append(':')
            .append(addr.getPort());
        } else {
            buffer.append(socketAddress);
        }
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        synchronized (this.key) {
            final SocketAddress remoteAddress = getRemoteAddress();
            final SocketAddress localAddress = getLocalAddress();
            if (remoteAddress != null && localAddress != null) {
                formatAddress(buffer, localAddress);
                buffer.append("<->");
                formatAddress(buffer, remoteAddress);
            }
            buffer.append('[');
            switch (this.status) {
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
                formatOps(buffer, this.interestOpsCallback != null ?
                        this.currentEventMask : this.key.interestOps());
                buffer.append(':');
                formatOps(buffer, this.key.readyOps());
            }
        }
        buffer.append(']');
        return new String(buffer);
    }

    @Override
    public Socket getSocket() {
        if (this.channel instanceof SocketChannel) {
            return ((SocketChannel) this.channel).socket();
        } else {
            return null;
        }
    }

}
