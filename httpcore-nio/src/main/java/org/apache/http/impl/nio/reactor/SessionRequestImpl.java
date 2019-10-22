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
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.util.Args;

/**
 * Default implementation of {@link SessionRequest}.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class SessionRequestImpl implements SessionRequest {

    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;
    private final Object attachment;
    private final SessionRequestCallback callback;
    private final AtomicBoolean completed;

    private volatile SelectionKey key;

    private volatile boolean terminated;
    private volatile int connectTimeout;
    private volatile IOSession session = null;
    private volatile IOException exception = null;

    public SessionRequestImpl(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final Object attachment,
            final SessionRequestCallback callback) {
        super();
        Args.notNull(remoteAddress, "Remote address");
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.attachment = attachment;
        this.callback = callback;
        this.completed = new AtomicBoolean(false);
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return this.localAddress;
    }

    @Override
    public Object getAttachment() {
        return this.attachment;
    }

    @Override
    public boolean isCompleted() {
        return this.completed.get();
    }

    boolean isTerminated() {
        return this.terminated;
    }

    protected void setKey(final SelectionKey key) {
        this.key = key;
        if (this.completed.get()) {
            key.cancel();
            final Channel channel = key.channel();
            if (channel.isOpen()) {
                try {
                    channel.close();
                } catch (final IOException ignore) {}
            }
        }
    }

    @Override
    public void waitFor() throws InterruptedException {
        if (this.completed.get()) {
            return;
        }
        synchronized (this) {
            while (!this.completed.get()) {
                wait();
            }
        }
    }

    @Override
    public IOSession getSession() {
        synchronized (this) {
            return this.session;
        }
    }

    @Override
    public IOException getException() {
        synchronized (this) {
            return this.exception;
        }
    }

    public void completed(final IOSession session) {
        Args.notNull(session, "Session");
        if (this.completed.compareAndSet(false, true)) {
            synchronized (this) {
                this.session = session;
                if (this.callback != null) {
                    this.callback.completed(this);
                }
                notifyAll();
            }
        }
    }

    public void failed(final IOException exception) {
        if (exception == null) {
            return;
        }
        if (this.completed.compareAndSet(false, true)) {
            this.terminated = true;
            final SelectionKey key = this.key;
            if (key != null) {
                key.cancel();
                final Channel channel = key.channel();
                try {
                    channel.close();
                } catch (final IOException ignore) {}
            }
            synchronized (this) {
                this.exception = exception;
                if (this.callback != null) {
                    this.callback.failed(this);
                }
                notifyAll();
            }
        }
    }

    public void timeout() {
        if (this.completed.compareAndSet(false, true)) {
            this.terminated = true;
            final SelectionKey key = this.key;
            if (key != null) {
                key.cancel();
                final Channel channel = key.channel();
                if (channel.isOpen()) {
                    try {
                        channel.close();
                    } catch (final IOException ignore) {}
                }
            }
            synchronized (this) {
                if (this.callback != null) {
                    this.callback.timeout(this);
                }
            }
        }
    }

    @Override
    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    @Override
    public void setConnectTimeout(final int timeout) {
        if (this.connectTimeout != timeout) {
            this.connectTimeout = timeout;
            final SelectionKey key = this.key;
            if (key != null) {
                key.selector().wakeup();
            }
        }
    }

    @Override
    public void cancel() {
        if (this.completed.compareAndSet(false, true)) {
            this.terminated = true;
            final SelectionKey key = this.key;
            if (key != null) {
                key.cancel();
                final Channel channel = key.channel();
                if (channel.isOpen()) {
                    try {
                        channel.close();
                    } catch (final IOException ignore) {}
                }
            }
            synchronized (this) {
                if (this.callback != null) {
                    this.callback.cancelled(this);
                }
                notifyAll();
            }
        }
    }

}
