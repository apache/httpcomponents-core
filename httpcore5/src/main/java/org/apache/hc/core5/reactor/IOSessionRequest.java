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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.io.GracefullyCloseable;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.TimeValue;

final class IOSessionRequest implements Cancellable {

    final NamedEndpoint remoteEndpoint;
    final SocketAddress remoteAddress;
    final SocketAddress localAddress;
    final TimeValue timeout;
    final Object attachment;
    final ComplexFuture<IOSession> future;

    private final AtomicReference<GracefullyCloseable> closeableRef;

    public IOSessionRequest(
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final TimeValue timeout,
            final Object attachment,
            final ComplexFuture<IOSession> future) {
        super();
        this.remoteEndpoint = remoteEndpoint;
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.timeout = timeout;
        this.attachment = attachment;
        this.future = future;
        this.closeableRef = new AtomicReference<>(null);
    }

    public void completed(final TlsCapableIOSession ioSession) {
        future.completed(ioSession);
    }

    public void failed(final Exception cause) {
        future.failed(cause);
    }

    public boolean cancel() {
        return future.cancel();
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }

    public void assign(final GracefullyCloseable closeable) {
        closeableRef.set(closeable);
        future.setDependency(new Cancellable() {

            @Override
            public boolean cancel() {
                final GracefullyCloseable closeable = closeableRef.getAndSet(null);
                if (closeable != null) {
                    closeable.shutdown(ShutdownType.IMMEDIATE);
                    return true;
                } else {
                    return false;
                }
            }

        });
    }

}
