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
package org.apache.hc.core5.http2.priority;

import org.apache.hc.core5.annotation.Internal;

/**
 * Presence-aware view for RFC 9218 Priority on responses.
 */
@Internal
public final class PriorityParams {
    private final Integer urgency;    // null if 'u' absent
    private final Boolean incremental; // null if 'i' absent

    public PriorityParams(final Integer urgency, final Boolean incremental) {
        if (urgency != null && (urgency < 0 || urgency > 7)) {
            throw new IllegalArgumentException("urgency out of range [0..7]: " + urgency);
        }
        this.urgency = urgency;
        this.incremental = incremental;
    }

    public Integer getUrgency() {
        return urgency;
    }

    public Boolean getIncremental() {
        return incremental;
    }

    /**
     * Convert to concrete value by applying RFC defaults (u=3, i=false) for missing members.
     */
    public PriorityValue toValueWithDefaults() {
        final int u = urgency != null ? urgency : PriorityValue.DEFAULT_URGENCY;
        final boolean i = incremental != null ? incremental : PriorityValue.DEFAULT_INCREMENTAL;
        return PriorityValue.of(u, i);
    }
}
