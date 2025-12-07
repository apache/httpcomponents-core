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
package org.apache.hc.core5.http2;

import java.net.SocketTimeoutException;

import org.apache.hc.core5.util.Timeout;

/**
 * {@link java.net.SocketTimeoutException} raised by the HTTP/2 stream
 * multiplexer when a per-stream timeout elapses.
 * <p>
 * This exception is used for timeouts that are scoped to a single HTTP/2
 * stream rather than the underlying TCP connection, for example:
 * </p>
 * <ul>
 *     <li>an idle timeout where no activity has been observed on the stream, or</li>
 *     <li>a lifetime timeout where the total age of the stream exceeds
 *     the configured limit.</li>
 * </ul>
 * <p>
 * The {@link #isIdleTimeout()} flag can be used to distinguish whether
 * the timeout was triggered by idleness or by the overall stream lifetime.
 * The affected stream id and the timeout value are exposed via
 * {@link #getStreamId()} and {@link #getTimeout()} respectively.
 * </p>
 *
 * @since 5.4
 */
public class H2StreamTimeoutException extends SocketTimeoutException {

    private static final long serialVersionUID = 1L;

    private final int streamId;
    private final Timeout timeout;
    private final boolean idleTimeout;

    public H2StreamTimeoutException(final String message, final int streamId, final Timeout timeout, final boolean idleTimeout) {
        super(message);
        this.streamId = streamId;
        this.timeout = timeout;
        this.idleTimeout = idleTimeout;
    }

    public int getStreamId() {
        return streamId;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    /**
     * Indicates whether this timeout was triggered by idle time (no activity)
     * rather than by stream lifetime.
     *
     * @return {@code true} if this is an idle timeout, {@code false} if it is a lifetime timeout.
     */
    public boolean isIdleTimeout() {
        return idleTimeout;
    }

}
