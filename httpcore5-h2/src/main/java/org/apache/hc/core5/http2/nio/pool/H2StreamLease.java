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
package org.apache.hc.core5.http2.nio.pool;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

/**
 * Reservation handle returned by {@link H2RequesterConnPool#leaseSession}.
 * Holds the leased {@link IOSession} together with a release action that the
 * requester must invoke exactly once when the corresponding stream slot is no
 * longer needed.
 *
 * @since 5.5
 */
@Internal
public final class H2StreamLease {

    private final IOSession session;
    private final Runnable releaseAction;
    private final AtomicBoolean released;

    H2StreamLease(final IOSession session, final Runnable releaseAction) {
        this.session = Args.notNull(session, "IO session");
        this.releaseAction = Args.notNull(releaseAction, "Release action");
        this.released = new AtomicBoolean(false);
    }

    public IOSession getSession() {
        return session;
    }

    public void releaseReservation() {
        if (released.compareAndSet(false, true)) {
            releaseAction.run();
        }
    }

    /**
     * Returns {@code true} once {@link #releaseReservation()} has been invoked
     * on this lease. Intended for diagnostics and for verifying lifecycle
     * invariants in tests.
     *
     * @since 5.5
     */
    public boolean isReleased() {
        return released.get();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("[session: ").append(session.getId());
        buf.append(", released: ").append(released.get());
        buf.append("]");
        return buf.toString();
    }
}