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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class TestPriorityMerge {

    @Test
    void rfc_example_server_overrides_urgency_preserves_incremental() {
        // Client: u=5, i=true
        final PriorityValue clientReq = PriorityValue.of(5, true);
        // Server response: u=1 (i omitted => server does not change it)
        final PriorityParams serverResp = PriorityParamsParser.parse("u=1");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        Assertions.assertThat(merged.getUrgency()).isEqualTo(1);
        Assertions.assertThat(merged.isIncremental()).isTrue();
    }

    @Test
    void response_absent_keeps_client_values() {
        final PriorityValue clientReq = PriorityValue.of(2, false);
        final PriorityValue merged = PriorityMerge.merge(clientReq, null);

        Assertions.assertThat(merged.getUrgency()).isEqualTo(2);
        Assertions.assertThat(merged.isIncremental()).isFalse();
    }

    @Test
    void client_absent_uses_response_or_defaults() {
        // Only server response provided
        final PriorityParams resp1 = PriorityParamsParser.parse("u=0, i");
        final PriorityValue merged1 = PriorityMerge.merge(null, resp1);
        Assertions.assertThat(merged1.getUrgency()).isEqualTo(0);
        Assertions.assertThat(merged1.isIncremental()).isTrue();

        // Neither side provided => defaults
        final PriorityValue merged2 = PriorityMerge.merge(null, null);
        Assertions.assertThat(merged2.getUrgency()).isEqualTo(PriorityValue.DEFAULT_URGENCY);
        Assertions.assertThat(merged2.isIncremental()).isEqualTo(PriorityValue.DEFAULT_INCREMENTAL);
    }

    @Test
    void server_sets_only_incremental_true_keeps_client_urgency() {
        final PriorityValue clientReq = PriorityValue.of(4, false);
        final PriorityParams serverResp = PriorityParamsParser.parse("i");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        Assertions.assertThat(merged.getUrgency()).isEqualTo(4);
        Assertions.assertThat(merged.isIncremental()).isTrue();
    }

    @Test
    void server_sets_only_urgency_keeps_client_incremental() {
        final PriorityValue clientReq = PriorityValue.of(6, true);
        final PriorityParams serverResp = PriorityParamsParser.parse("u=1");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        Assertions.assertThat(merged.getUrgency()).isEqualTo(1);
        Assertions.assertThat(merged.isIncremental()).isTrue();
    }

    @Test
    void out_of_range_server_urgency_is_ignored_but_other_members_apply() {
        final PriorityValue clientReq = PriorityValue.of(3, false);
        // u=9 is invalid -> ignored; i applies
        final PriorityParams serverResp = PriorityParamsParser.parse("u=9, i");

        final PriorityValue merged = PriorityMerge.merge(clientReq, serverResp);
        Assertions.assertThat(merged.getUrgency()).isEqualTo(3);   // unchanged
        Assertions.assertThat(merged.isIncremental()).isTrue();     // from server
    }

    @Test
    void null_safety_with_valid_inputs() {
        // Server provides only u; client null -> defaults for i
        final PriorityValue merged = PriorityMerge.merge(null, new PriorityParams(2, null));
        Assertions.assertThat(merged.getUrgency()).isEqualTo(2);
        Assertions.assertThat(merged.isIncremental()).isEqualTo(PriorityValue.DEFAULT_INCREMENTAL);
    }
}