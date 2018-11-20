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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;

final class IOSessionRequest implements Future<IOSession> {

    final NamedEndpoint remoteEndpoint;
    final SocketAddress remoteAddress;
    final SocketAddress localAddress;
    final Timeout timeout;
    final Object attachment;
    final BasicFuture<IOSession> future;

    private final AtomicReference<ModalCloseable> closeableRef;

    public IOSessionRequest(
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final Timeout timeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) {
        super();
        this.remoteEndpoint = remoteEndpoint;
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.timeout = timeout;
        this.attachment = attachment;
        this.future = new BasicFuture<>(callback);
        this.closeableRef = new AtomicReference<>(null);
    }

    public void completed(final ProtocolIOSession ioSession) {
        future.completed(ioSession);
        closeableRef.set(null);
    }

    public void failed(final Exception cause) {
        future.failed(cause);
        closeableRef.set(null);
    }

    public boolean cancel() {
        final boolean cancelled = future.cancel();
        final ModalCloseable closeable = closeableRef.getAndSet(null);
        if (cancelled && closeable != null) {
            closeable.close(CloseMode.IMMEDIATE);
        }
        return cancelled;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return cancel();
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    public void assign(final ModalCloseable closeable) {
        closeableRef.set(closeable);
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public IOSession get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public IOSession get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public String toString() {
        return "[" +
                "remoteEndpoint=" + remoteEndpoint +
                ", remoteAddress=" + remoteAddress +
                ", localAddress=" + localAddress +
                ", attachment=" + attachment +
                ']';
    }

}
