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
package org.apache.hc.core5.http2.impl.nio;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Internal;

/**
 * Attachment-based bridge between the HTTP/2 multiplexing connection
 * pool and the stream multiplexer. The pool creates an instance and
 * passes it as the {@code attachment} argument to the connection
 * initiator; the I/O event handler detects it and wires it to the
 * multiplexer. The multiplexer pushes state updates into this object
 * and fires observer callbacks through it; the pool reads state and
 * registers its observer here.
 *
 * @since 5.5
 */
@Internal
public final class H2PoolSessionSupport {

    /**
     * Callback for capacity, draining and session-closure notifications.
     * Implementations are invoked from the I/O reactor thread
     * and must not block.
     */
    public interface Observer {

        /** Stream capacity became available on the connection. */
        void onCapacityAvailable();

        /** Connection entered a draining state. */
        void onDraining();

        /**
         * Underlying session has been fully disconnected. Fired exactly once
         * after the multiplexer has finalised its disconnect bookkeeping, so
         * the observer may treat reservations and active streams on this
         * session as void. Default is a no-op to preserve source and binary
         * compatibility with existing observers.
         *
         * @since 5.5
         */
        default void onSessionClosed() {
        }

    }

    private final AtomicInteger activeLocalStreams;
    private final AtomicInteger peerMaxConcurrentStreams;
    private final AtomicReference<Observer> observerRef;

    private volatile boolean goAwayReceived;
    private volatile boolean shutdown;

    public H2PoolSessionSupport() {
        this.activeLocalStreams = new AtomicInteger();
        this.peerMaxConcurrentStreams = new AtomicInteger(Integer.MAX_VALUE);
        this.observerRef = new AtomicReference<>();
    }

    /** Returns the number of locally initiated streams currently open. */
    public int getActiveLocalStreams() {
        return activeLocalStreams.get();
    }

    /** Returns the peer's {@code MAX_CONCURRENT_STREAMS} value. */
    public int getPeerMaxConcurrentStreams() {
        return peerMaxConcurrentStreams.get();
    }

    /** Returns {@code true} if a {@code GOAWAY} frame has been received. */
    public boolean isGoAwayReceived() {
        return goAwayReceived;
    }

    /** Returns {@code true} if the session is shutting down. */
    public boolean isShutdown() {
        return shutdown;
    }

    /** Registers the observer for capacity change notifications. */
    public void setObserver(final Observer observer) {
        observerRef.set(observer);
    }

    /** Pushes the current active local stream count. */
    public void updateActiveLocalStreams(final int count) {
        activeLocalStreams.set(count);
    }

    /** Pushes the peer's {@code MAX_CONCURRENT_STREAMS} value. */
    public void updatePeerMaxConcurrentStreams(final int max) {
        peerMaxConcurrentStreams.set(max);
    }

    /** Pushes the GOAWAY-received flag. */
    public void updateGoAwayReceived(final boolean value) {
        goAwayReceived = value;
    }

    /** Pushes the shutdown flag. */
    public void updateShutdown(final boolean value) {
        shutdown = value;
    }

    /** Fires the capacity-available notification. */
    public void fireCapacityAvailable() {
        final Observer observer = observerRef.get();
        if (observer != null) {
            observer.onCapacityAvailable();
        }
    }

    /** Fires the draining notification. */
    public void fireDraining() {
        final Observer observer = observerRef.get();
        if (observer != null) {
            observer.onDraining();
        }
    }

    /**
     * Fires the session-closed notification. Intended to be invoked exactly
     * once per session after the multiplexer has reset its local stream
     * state, so the pool sees a coherent final picture.
     *
     * @since 5.5
     */
    public void fireSessionClosed() {
        final Observer observer = observerRef.get();
        if (observer != null) {
            observer.onSessionClosed();
        }
    }

}
