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

import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.io.Closer;

class IOSessionImpl implements IOSession {

    private final static AtomicLong COUNT = new AtomicLong(0);

    private final SelectionKey key;
    private final SocketChannel channel;
    private final Deque<Command> commandQueue;
    private final Lock lock;
    private final String id;
    private final AtomicInteger status;

    private volatile IOEventHandler eventHandler;
    private volatile int socketTimeout;
    private volatile long lastReadTime;
    private volatile long lastWriteTime;

    /**
     * Creates new instance of IOSessionImpl.
     *
     * @param key the selection key.
     * @param socketChannel the socket channel
     */
    public IOSessionImpl(final SelectionKey key, final SocketChannel socketChannel) {
        super();
        this.key = Args.notNull(key, "Selection key");
        this.channel = Args.notNull(socketChannel, "Socket channel");
        this.commandQueue = new ConcurrentLinkedDeque<>();
        this.lock = new ReentrantLock();
        this.socketTimeout = 0;
        this.id = String.format("i/o-%08X", COUNT.getAndIncrement());
        this.status = new AtomicInteger(ACTIVE);
        this.lastReadTime = System.currentTimeMillis();
        this.lastWriteTime = System.currentTimeMillis();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Lock lock() {
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
        if (this.status.get() == CLOSED) {
            return;
        }
        this.key.interestOps(newValue);
        this.key.selector().wakeup();
    }

    @Override
    public void setEvent(final int op) {
        if (this.status.get() == CLOSED) {
            return;
        }
        lock.lock();
        try {
            this.key.interestOps(this.key.interestOps() | op);
        } finally {
            lock.unlock();
        }
        this.key.selector().wakeup();
    }

    @Override
    public void clearEvent(final int op) {
        if (this.status.get() == CLOSED) {
            return;
        }
        lock.lock();
        try {
            this.key.interestOps(this.key.interestOps() & ~op);
        } finally {
            lock.unlock();
        }
        this.key.selector().wakeup();
    }

    @Override
    public int getSocketTimeoutMillis() {
        return this.socketTimeout;
    }

    @Override
    public void setSocketTimeoutMillis(final int timeout) {
        this.socketTimeout = timeout;
    }

    @Override
    public void updateReadTime() {
        lastReadTime = System.currentTimeMillis();
    }

    @Override
    public void updateWriteTime() {
        lastWriteTime = System.currentTimeMillis();
    }

    @Override
    public long getLastReadTimeMillis() {
        return lastReadTime;
    }

    @Override
    public long getLastWriteTime() {
        return lastWriteTime;
    }

    @Override
    public void close() {
        if (this.status.compareAndSet(ACTIVE, CLOSED)) {
            this.key.cancel();
            this.key.attach(null);
            Closer.closeQuietly(this.key.channel());
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
        return this.status.get() == CLOSED || !this.channel.isOpen();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (closeMode == CloseMode.IMMEDIATE) {
            try {
                this.channel.socket().setSoLinger(true, 0);
            } catch (final SocketException e) {
                // Quietly ignore
            }
        }
        close();
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
