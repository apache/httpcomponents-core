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


import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.annotation.Internal;

/**
 * Formats PriorityValue as RFC 9218 Structured Fields Dictionary.
 * Only emits non-defaults: u when != 3, i when true.
 * Returns null when both are defaults (callers should omit the header then).
 */
@Internal
public final class PriorityFormatter {

    private PriorityFormatter() {
    }

    public static String format(final PriorityValue value) {
        if (value == null) {
            return null;
        }
        final List<String> parts = new ArrayList<>(2);
        if (value.getUrgency() != PriorityValue.DEFAULT_URGENCY) {
            parts.add("u=" + value.getUrgency());
        }
        if (value.isIncremental()) {
            // In SF Dictionary, boolean true can be represented by key without value (per RFC 8941).
            parts.add("i");
        }
        if (parts.isEmpty()) {
            return null; // omit header when all defaults
        }
        return String.join(", ", parts);
    }
}