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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TestPriorityMerge {

    @Test
    void rfc_example_server_overrides_urgency_preserves_incremental() {
        // Client: u=5, i=true
        final PriorityValue clientReq = PriorityValue.of(5, true);
        // Server response: u=1 (i omitted => server does not change it)
        final PriorityParams serverResp = PriorityParamsParser.parse("u=1");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        assertEquals(1, merged.getUrgency());
        assertTrue(merged.isIncremental());
    }

    @Test
    void response_absent_keeps_client_values() {
        final PriorityValue clientReq = PriorityValue.of(2, false);
        final PriorityValue merged = PriorityMerge.merge(clientReq, null);

        assertEquals(2, merged.getUrgency());
        assertFalse(merged.isIncremental());
    }

    @Test
    void client_absent_uses_response_or_defaults() {
        // Only server response provided
        final PriorityParams resp1 = PriorityParamsParser.parse("u=0, i");
        final PriorityValue merged1 = PriorityMerge.merge(null, resp1);
        assertEquals(0, merged1.getUrgency());
        assertTrue(merged1.isIncremental());

        // Neither side provided => defaults
        final PriorityValue merged2 = PriorityMerge.merge(null, null);
        assertEquals(PriorityValue.DEFAULT_URGENCY, merged2.getUrgency());
        assertEquals(PriorityValue.DEFAULT_INCREMENTAL, merged2.isIncremental());
    }

    @Test
    void server_sets_only_incremental_true_keeps_client_urgency() {
        final PriorityValue clientReq = PriorityValue.of(4, false);
        final PriorityParams serverResp = PriorityParamsParser.parse("i");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        assertEquals(4, merged.getUrgency());
        assertTrue(merged.isIncremental());
    }

    @Test
    void server_sets_only_urgency_keeps_client_incremental() {
        final PriorityValue clientReq = PriorityValue.of(6, true);
        final PriorityParams serverResp = PriorityParamsParser.parse("u=1");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        assertEquals(1, merged.getUrgency());
        assertTrue(merged.isIncremental());
    }

    @Test
    void out_of_range_server_urgency_is_ignored_but_other_members_apply() {
        final PriorityValue clientReq = PriorityValue.of(3, false);
        // u=9 is invalid -> ignored; i applies
        final PriorityParams serverResp = PriorityParamsParser.parse("u=9, i");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        assertEquals(3, merged.getUrgency());   // unchanged
        assertTrue(merged.isIncremental());     // from server
    }

    @Test
    void null_safety_with_valid_inputs() {
        // Server provides only u; client null -> defaults for i
        final PriorityValue merged = PriorityMerge.merge(null, new PriorityParams(2, null));
        assertEquals(2, merged.getUrgency());
        assertEquals(PriorityValue.DEFAULT_INCREMENTAL, merged.isIncremental());
    }
}