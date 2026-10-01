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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;

/** Thread-safe per-connection Origin Set state. */
final class H2OriginSet {

    private static final class State {

        private final boolean initialized;
        private final Set<HttpHost> origins;

        private State(final boolean initialized, final Set<HttpHost> origins) {
            this.initialized = initialized;
            this.origins = origins;
        }
    }

    private final HttpHost initialOrigin;
    private final int maxSize;
    private final AtomicReference<State> stateRef;

    H2OriginSet(final HttpHost initialOrigin, final int maxSize) {
        this.initialOrigin = initialOrigin != null ? H2OriginFrameCodec.normalize(initialOrigin) : null;
        this.maxSize = maxSize;
        this.stateRef = new AtomicReference<>(new State(false, Collections.emptySet()));
    }

    boolean isInitialized() {
        return stateRef.get().initialized;
    }

    Set<HttpHost> snapshot() {
        return stateRef.get().origins;
    }

    boolean isAllowed(final HttpHost origin) {
        final State state = stateRef.get();
        return !state.initialized || state.origins.contains(H2OriginFrameCodec.normalize(origin));
    }

    void ensureAllowed(final HttpHost origin) throws MisdirectedRequestException {
        if (origin != null && !isAllowed(origin)) {
            throw new H2OriginMismatchException(
                    "Origin " + H2OriginFrameCodec.format(origin) + " is not in the connection Origin Set");
        }
    }

    void update(final Collection<HttpHost> additions) throws H2ConnectionException {
        for (;;) {
            final State current = stateRef.get();
            final LinkedHashSet<HttpHost> origins = new LinkedHashSet<>();
            if (current.initialized) {
                origins.addAll(current.origins);
            } else if (initialOrigin != null) {
                origins.add(initialOrigin);
            }
            for (final HttpHost origin : additions) {
                origins.add(H2OriginFrameCodec.normalize(origin));
            }
            if (maxSize > 0 && origins.size() > maxSize) {
                throw new H2ConnectionException(
                        H2Error.ENHANCE_YOUR_CALM,
                        "Origin Set exceeds the configured limit of " + maxSize);
            }
            final State updated = new State(true, Collections.unmodifiableSet(origins));
            if (stateRef.compareAndSet(current, updated)) {
                return;
            }
        }
    }

    void remove(final HttpHost origin) {
        if (origin == null) {
            return;
        }
        final HttpHost normalized = H2OriginFrameCodec.normalize(origin);
        for (;;) {
            final State current = stateRef.get();
            if (!current.initialized || !current.origins.contains(normalized)) {
                return;
            }
            final LinkedHashSet<HttpHost> origins = new LinkedHashSet<>(current.origins);
            origins.remove(normalized);
            final State updated = new State(true, Collections.unmodifiableSet(origins));
            if (stateRef.compareAndSet(current, updated)) {
                return;
            }
        }
    }
}
