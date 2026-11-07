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

import org.apache.hc.core5.util.Args;

/**
 * Per-connection resource policy for unsolicited HTTP/2 control frames.
 *
 * <p>The limiter is owned and called by a single HTTP/2 I/O session. Only
 * non-ACK PING and SETTINGS frames are charged. ACK frames, DATA,
 * WINDOW_UPDATE and RST_STREAM are not charged, as they can represent
 * legitimate high-throughput or highly concurrent exchanges.</p>
 *
 * <p>Uses a small token bucket to tolerate bursts and recover over time,
 * with no locks or per-frame allocation. This is a resource safeguard,
 * not an RFC 9113 protocol-validity rule.</p>
 */
final class H2ControlFrameRateLimiter {

    static final int CAPACITY = 256;
    static final int REFILL_PER_SECOND = 128;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int capacity;
    private final int refillPerSecond;
    private final long fullRefillNanos;

    private int tokens;
    private long previousNanos;
    private long remainder;
    private boolean initialized;

    H2ControlFrameRateLimiter(final int capacity, final int refillPerSecond) {
        this.capacity = Args.positive(capacity, "Capacity");
        this.refillPerSecond = Args.positive(refillPerSecond, "Refill per second");
        this.fullRefillNanos = (NANOS_PER_SECOND * capacity + refillPerSecond - 1) / refillPerSecond;
        this.tokens = capacity;
    }

    H2ControlFrameRateLimiter() {
        this(CAPACITY, REFILL_PER_SECOND);
    }

    boolean accept(final long nowNanos) {
        if (!initialized) {
            initialized = true;
            previousNanos = nowNanos;
        } else {
            final long elapsed = nowNanos - previousNanos;
            // Avoid overflow and do not refill on a backward clock jump.
            if (elapsed > 0) {
                previousNanos = nowNanos;
                if (elapsed >= fullRefillNanos) {
                    tokens = capacity;
                    remainder = 0;
                } else {
                    final long numerator = elapsed * refillPerSecond + remainder;
                    final long refill = numerator / NANOS_PER_SECOND;
                    remainder = numerator % NANOS_PER_SECOND;
                    if (refill > 0) {
                        tokens = (int) Math.min(capacity, (long) tokens + refill);
                        if (tokens == capacity) {
                            remainder = 0;
                        }
                    }
                }
            }
        }
        if (tokens == 0) {
            return false;
        }
        tokens--;
        return true;
    }
}
