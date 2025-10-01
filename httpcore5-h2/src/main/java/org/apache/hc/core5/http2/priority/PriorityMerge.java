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
 * Non-normative merge helper per RFC 9218 §8 example.
 * Policy: if server provides a member, prefer it; otherwise keep client's.
 */
@Internal
public final class PriorityMerge {
    private PriorityMerge() {
    }

    public static PriorityValue merge(final PriorityValue clientRequest, final PriorityParams serverResponse) {
        final int u = serverResponse != null && serverResponse.getUrgency() != null
                ? serverResponse.getUrgency()
                : (clientRequest != null ? clientRequest.getUrgency() : PriorityValue.DEFAULT_URGENCY);

        final boolean i = serverResponse != null && serverResponse.getIncremental() != null
                ? serverResponse.getIncremental()
                : (clientRequest != null ? clientRequest.isIncremental() : PriorityValue.DEFAULT_INCREMENTAL);

        return PriorityValue.of(u, i);
    }
}
