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

import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.util.Args;

/**
 * Per-connection resource policy for unsolicited HTTP/2 control frames. Non-ACK PING and
 * SETTINGS frames share one token bucket, exactly as before this class existed; rapid
 * cancellation of peer-initiated streams, whether by an inbound RST_STREAM or by a local
 * reset triggered by the peer (a protocol violation on an otherwise valid frame), is charged
 * against its own, independent bucket.
 *
 * <p>ACK frames, DATA and WINDOW_UPDATE are not charged as frames; a local reset one of them
 * provokes is. Legitimate DATA and WINDOW_UPDATE traffic can represent high-throughput or
 * highly concurrent exchanges and must stay free. An inbound RST_STREAM is only charged
 * against the reset bucket when it arrives shortly after the stream was activated; resets
 * of long-lived streams are not charged this way, since a client can legitimately cancel a
 * long-running exchange at any time. A local reset the peer makes us perform has no such
 * legitimate long-lived case (for example, a stream we reset because the peer sent a
 * zero-increment WINDOW_UPDATE on it), so it is always charged against the same bucket,
 * regardless of the stream's age; this keeps a peer from using one of these primitives to
 * get a larger budget than plain RST_STREAM would allow.</p>
 *
 * <p>Parameters are hardwired for now; they may become user configurable in the future, at
 * which point this is the single place that would need a public constructor or setters.</p>
 *
 * <p>This is a resource safeguard, not an RFC 9113 protocol-validity rule.</p>
 */
final class FrameLimitController {

    static final int CONTROL_FRAME_CAPACITY = 256;
    static final int CONTROL_FRAME_REFILL_PER_SECOND = 128;

    static final int RST_STREAM_CAPACITY = 128;
    static final int RST_STREAM_REFILL_PER_SECOND = 64;
    static final long RST_STREAM_MAX_AGE_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final TokenBucket controlFrameBucket;
    private final TokenBucket rstStreamBucket;
    private final long rstStreamMaxAgeNanos;

    FrameLimitController() {
        this(CONTROL_FRAME_CAPACITY, CONTROL_FRAME_REFILL_PER_SECOND,
                RST_STREAM_CAPACITY, RST_STREAM_REFILL_PER_SECOND,
                RST_STREAM_MAX_AGE_NANOS);
    }

    FrameLimitController(
            final int controlFrameCapacity,
            final int controlFrameRefillPerSecond,
            final int rstStreamCapacity,
            final int rstStreamRefillPerSecond,
            final long rstStreamMaxAgeNanos) {
        this.controlFrameBucket = new TokenBucket(controlFrameCapacity, controlFrameRefillPerSecond);
        this.rstStreamBucket = new TokenBucket(rstStreamCapacity, rstStreamRefillPerSecond);
        this.rstStreamMaxAgeNanos = Args.positive(rstStreamMaxAgeNanos, "Maximum reset age");
    }

    boolean acceptControlFrame(final long nowNanos) {
        return controlFrameBucket.accept(nowNanos);
    }

    boolean acceptRapidReset(final long activatedAtNanos, final long nowNanos) {
        final long age = nowNanos - activatedAtNanos;
        return age < 0 || age >= rstStreamMaxAgeNanos || rstStreamBucket.accept(nowNanos);
    }

    /**
     * Charges the same bucket as {@link #acceptRapidReset(long, long)}, without its age
     * exemption. Used for a local reset of a peer-initiated stream triggered by the peer,
     * which has no legitimate long-lived-stream case to exempt.
     */
    boolean acceptLocalReset(final long nowNanos) {
        return rstStreamBucket.accept(nowNanos);
    }

    /**
     * Constant-time token bucket, with no locks or per-frame allocation, used to tolerate
     * bursts and recover over time. Shared by every bucket kept by {@link FrameLimitController};
     * a new kind of limited frame is just another instance of this class.
     */
    static final class TokenBucket {

        private static final long NANOS_PER_SECOND = 1_000_000_000L;

        private final int capacity;
        private final int refillPerSecond;
        private final long fullRefillNanos;

        private int tokens;
        private long previousNanos;
        private long remainder;
        private boolean initialized;

        TokenBucket(final int capacity, final int refillPerSecond) {
            this.capacity = Args.positive(capacity, "Capacity");
            this.refillPerSecond = Args.positive(refillPerSecond, "Refill per second");
            this.fullRefillNanos = (NANOS_PER_SECOND * capacity + refillPerSecond - 1) / refillPerSecond;
            this.tokens = capacity;
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
}
