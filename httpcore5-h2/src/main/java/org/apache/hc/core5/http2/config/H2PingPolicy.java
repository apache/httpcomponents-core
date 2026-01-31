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

package org.apache.hc.core5.http2.config;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 keep-alive ping policy.
 *
 * @since 5.5
 */
public final class H2PingPolicy {

    private static final H2PingPolicy DISABLED = new H2PingPolicy(Timeout.DISABLED, Timeout.DISABLED);

    private final Timeout idleTime;
    private final Timeout ackTimeout;

    private H2PingPolicy(final Timeout idleTime, final Timeout ackTimeout) {
        this.idleTime = idleTime;
        this.ackTimeout = ackTimeout;
    }

    public static H2PingPolicy disabled() {
        return DISABLED;
    }

    public static Builder custom() {
        return new Builder();
    }

    public Timeout getIdleTime() {
        return idleTime;
    }

    public Timeout getAckTimeout() {
        return ackTimeout;
    }

    public boolean isEnabled() {
        return isActive(idleTime) && isActive(ackTimeout);
    }

    private static boolean isActive(final Timeout timeout) {
        return timeout != null && timeout.isEnabled() && TimeValue.isPositive(timeout);
    }

    public static final class Builder {

        private Timeout idleTime;
        private Timeout ackTimeout;

        private Builder() {
            this.idleTime = Timeout.DISABLED;
            this.ackTimeout = Timeout.DISABLED;
        }

        public Builder setIdleTime(final Timeout idleTime) {
            this.idleTime = Args.notNull(idleTime, "idleTime");
            return this;
        }

        public Builder setAckTimeout(final Timeout ackTimeout) {
            this.ackTimeout = Args.notNull(ackTimeout, "ackTimeout");
            return this;
        }

        public H2PingPolicy build() {
            if (isActive(idleTime)) {
                Args.check(isActive(ackTimeout), "ackTimeout must be positive when idleTime is enabled");
            }
            return new H2PingPolicy(idleTime, ackTimeout);
        }
    }

}
