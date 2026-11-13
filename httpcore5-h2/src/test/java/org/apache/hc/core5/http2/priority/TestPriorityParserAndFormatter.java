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

import org.apache.hc.core5.http.Header;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public final class TestPriorityParserAndFormatter {

    // ---- RFC 9218 examples --------------------------------------------------

    @Test
    void rfc_example_css_urgency0() {
        // From RFC 9218 §4.1: priority = u=0
        final PriorityValue v = PriorityParser.parse("u=0");
        Assertions.assertThat(v.getUrgency()).isEqualTo(0);
        Assertions.assertThat(v.isIncremental()).isFalse();

        // Formatter emits only non-defaults
        Assertions.assertThat(PriorityFormatter.format(v)).isEqualTo("u=0");
    }

    @Test
    void rfc_example_jpeg_u5_incremental() {
        // From RFC 9218 §4.2: priority = u=5, i
        final PriorityValue v = PriorityParser.parse("u=5, i");
        Assertions.assertThat(v.getUrgency()).isEqualTo(5);
        Assertions.assertThat(v.isIncremental()).isTrue();

        // Formatter keeps canonical "u=5, i"
        Assertions.assertThat(PriorityFormatter.format(v)).isEqualTo("u=5, i");
    }

    // ---- Defaults & omission -------------------------------------------------

    @Test
    void defaults_when_empty_or_null() {
        final PriorityValue v1 = PriorityParser.parse("");
        Assertions.assertThat(v1.getUrgency()).isEqualTo(3);
        Assertions.assertThat(v1.isIncremental()).isFalse();

        final PriorityValue v2 = PriorityParser.parse((Header) null);
        Assertions.assertThat(v2.getUrgency()).isEqualTo(3);
        Assertions.assertThat(v2.isIncremental()).isFalse();
    }

    // ---- Boolean variants per RFC 8941 --------------------------------------

    @Test
    void boolean_variants_for_incremental() {
        Assertions.assertThat(PriorityParser.parse("i").isIncremental()).isTrue();        // bare true
        Assertions.assertThat(PriorityParser.parse("i=?1").isIncremental()).isTrue();     // structured boolean true
        Assertions.assertThat(PriorityParser.parse("i=?0").isIncremental()).isFalse();    // structured boolean false
        Assertions.assertThat(PriorityParser.parse("i=1").isIncremental()).isTrue();      // tolerant numeric '1'
        Assertions.assertThat(PriorityParser.parse("i=0").isIncremental()).isFalse();     // tolerant numeric '0'
    }

    // ---- Unknown & invalid handling -----------------------------------------

    @Test
    void unknown_members_are_ignored() {
        final PriorityValue v = PriorityParser.parse("foo=bar, u=2, i, baz=?1");
        Assertions.assertThat(v.getUrgency()).isEqualTo(2);
        Assertions.assertThat(v.isIncremental()).isTrue();
    }

    @Test
    void urgency_out_of_range_is_ignored() {
        final PriorityValue v1 = PriorityParser.parse("u=9");   // >7
        Assertions.assertThat(v1.getUrgency()).isEqualTo(3);

        final PriorityValue v2 = PriorityParser.parse("u=-1");  // <0
        Assertions.assertThat(v2.getUrgency()).isEqualTo(3);
    }

    @Test
    void malformed_members_are_ignored() {
        final PriorityValue v = PriorityParser.parse("u=abc, i=banana, i=?x");
        Assertions.assertThat(v.getUrgency()).isEqualTo(3);        // default
        Assertions.assertThat(v.isIncremental()).isFalse();         // default
    }

    // ---- Whitespace, params, case-insensitivity -----------------------------

    @Test
    void handles_ows_and_parameters_and_case() {
        // Ignore structured-field parameters after members, and key is case-insensitive
        final PriorityValue v = PriorityParser.parse("  U = 1 ;p=v  ,  I  ;x  ");
        Assertions.assertThat(v.getUrgency()).isEqualTo(1);
        Assertions.assertThat(v.isIncremental()).isTrue();

        // Formatter canonicalizes output (no params, normalized layout)
        Assertions.assertThat(PriorityFormatter.format(v)).isEqualTo("u=1, i");
    }

    // ---- Minimal formatting rules -------------------------------------------

    @Test
    void formatter_emits_only_non_defaults_in_canonical_order() {
        Assertions.assertThat(PriorityFormatter.format(PriorityValue.of(1, false))).isEqualTo("u=1");
        Assertions.assertThat(PriorityFormatter.format(PriorityValue.of(3, true))).isEqualTo("i");
        Assertions.assertThat(PriorityFormatter.format(PriorityValue.of(2, true))).isEqualTo("u=2, i");
    }

    // ---- Round-trips ---------------------------------------------------------

    @Test
    void round_trip_common_values() {
        roundTrip("u=0");
        roundTrip("u=0, i");
        roundTrip("u=5, i");
        roundTrip("i");
        roundTrip("u=7");
    }

    private static void roundTrip(final String s) {
        final PriorityValue v = PriorityParser.parse(s);
        final String out = PriorityFormatter.format(v);
        // If v equals defaults, formatter returns null; otherwise we expect the canonical equivalent
        if (v.getUrgency() == 3 && !v.isIncremental()) {
            Assertions.assertThat(out).isNull();
        } else {
            // Canonical ordering is u first (if non-default), then i if true
            if (v.getUrgency() != 3 && v.isIncremental()) {
                Assertions.assertThat(out).isEqualTo("u=" + v.getUrgency() + ", i");
            } else if (v.getUrgency() != 3) {
                Assertions.assertThat(out).isEqualTo("u=" + v.getUrgency());
            } else {
                Assertions.assertThat(out).isEqualTo("i");
            }
        }
    }
}