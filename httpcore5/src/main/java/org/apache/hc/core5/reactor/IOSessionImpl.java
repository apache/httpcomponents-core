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
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

class IOSessionImpl implements IOSession {

    /** Counts instances created. */
    private final static AtomicLong COUNT = new AtomicLong(0);

    private final SelectionKey key;
    private final SocketChannel channel;
    private final Deque<Command> commandQueue;
    private final Lock lock;
    private final String id;
    private final AtomicReference<IOEventHandler> handlerRef;
    private final AtomicReference<IOSession.Status> status;

    private volatile Timeout socketTimeout;
    private volatile long lastReadTime;
    private volatile long lastWriteTime;
    private volatile long lastEventTime;

    public IOSessionImpl(final String type, final SelectionKey key, final SocketChannel socketChannel) {
        super();
        this.key = Args.notNull(key, "Selection key");
        this.channel = Args.notNull(socketChannel, "Socket channel");
        this.commandQueue = new ConcurrentLinkedDeque<>();
        this.lock = new ReentrantLock();
        this.socketTimeout = Timeout.DISABLED;
        this.id = String.format(type + "-%010d", COUNT.getAndIncrement());
        this.handlerRef = new AtomicReference<>();
        this.status = new AtomicReference<>(Status.ACTIVE);
        final long currentTimeMillis = System.currentTimeMillis();
        this.lastReadTime = currentTimeMillis;
        this.lastWriteTime = currentTimeMillis;
        this.lastEventTime = currentTimeMillis;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public IOEventHandler getHandler() {
        return handlerRef.get();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        handlerRef.set(handler);
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        if (priority == Command.Priority.IMMEDIATE) {
            commandQueue.addFirst(command);
        } else {
            commandQueue.add(command);
        }
        setEvent(SelectionKey.OP_WRITE);

        if (isStatusClosed()) {
            command.cancel();
        }
    }

    @Override
    public boolean hasCommands() {
        return !commandQueue.isEmpty();
    }

    @Override
    public Command poll() {
        return commandQueue.poll();
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
        lock.lock();
        try {
            if (isStatusClosed()) {
                return;
            }
            this.key.interestOps(newValue);
        } finally {
            lock.unlock();
        }
        this.key.selector().wakeup();
    }

    @Override
    public void setEvent(final int op) {
        lock.lock();
        try {
            if (isStatusClosed()) {
                return;
            }
            this.key.interestOps(this.key.interestOps() | op);
        } finally {
            lock.unlock();
        }
        this.key.selector().wakeup();
    }

    @Override
    public void clearEvent(final int op) {
        lock.lock();
        try {
            if (isStatusClosed()) {
                return;
            }
            this.key.interestOps(this.key.interestOps() & ~op);
        } finally {
            lock.unlock();
        }
        this.key.selector().wakeup();
    }

    @Override
    public Timeout getSocketTimeout() {
        return this.socketTimeout;
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        this.socketTimeout = Timeout.defaultsToDisabled(timeout);
        this.lastEventTime = System.currentTimeMillis();
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        return this.channel.read(dst);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return this.channel.write(src);
    }

    @Override
    public void updateReadTime() {
        lastReadTime = System.currentTimeMillis();
        lastEventTime = lastReadTime;
    }

    @Override
    public void updateWriteTime() {
        lastWriteTime = System.currentTimeMillis();
        lastEventTime = lastWriteTime;
    }

    @Override
    public long getLastReadTime() {
        return lastReadTime;
    }

    @Override
    public long getLastWriteTime() {
        return lastWriteTime;
    }

    @Override
    public long getLastEventTime() {
        return lastEventTime;
    }

    @Override
    public Status getStatus() {
        return this.status.get();
    }

    private boolean isStatusClosed() {
        return this.status.get() == Status.CLOSED;
    }

    @Override
    public boolean isOpen() {
        return this.status.get() == Status.ACTIVE && this.channel.isOpen();
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.status.compareAndSet(Status.ACTIVE, Status.CLOSED)) {
            if (closeMode == CloseMode.IMMEDIATE) {
                try {
                    this.channel.socket().setSoLinger(true, 0);
                } catch (final SocketException e) {
                    // Quietly ignore
                }
            }
            this.key.cancel();
            this.key.attach(null);
            Closer.closeQuietly(this.key.channel());
            if (this.key.selector().isOpen()) {
                this.key.selector().wakeup();
            }
        }
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

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(id).append("[");
        buffer.append(this.status);
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
