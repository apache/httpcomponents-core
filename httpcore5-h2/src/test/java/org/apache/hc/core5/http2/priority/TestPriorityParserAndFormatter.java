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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Test;

public final class TestPriorityParserAndFormatter {

    // ---- RFC 9218 examples --------------------------------------------------

    @Test
    void rfc_example_css_urgency0() {
        // From RFC 9218 §4.1: priority = u=0
        final PriorityValue v = PriorityParser.parse("u=0");
        assertEquals(0, v.getUrgency());
        assertFalse(v.isIncremental());

        // Formatter emits only non-defaults
        assertEquals("u=0", PriorityFormatter.format(v));
    }

    @Test
    void rfc_example_jpeg_u5_incremental() {
        // From RFC 9218 §4.2: priority = u=5, i
        final PriorityValue v = PriorityParser.parse("u=5, i");
        assertEquals(5, v.getUrgency());
        assertTrue(v.isIncremental());

        // Formatter keeps canonical "u=5, i"
        assertEquals("u=5, i", PriorityFormatter.format(v));
    }

    // ---- Defaults & omission -------------------------------------------------

    @Test
    void defaults_when_empty_or_null() {
        final PriorityValue v1 = PriorityParser.parse("");
        assertEquals(3, v1.getUrgency());
        assertFalse(v1.isIncremental());

        final PriorityValue v2 = PriorityParser.parse((Header) null);
        assertEquals(3, v2.getUrgency());
        assertFalse(v2.isIncremental());
    }

    // ---- Boolean variants per RFC 8941 --------------------------------------

    @Test
    void boolean_variants_for_incremental() {
        assertTrue(PriorityParser.parse("i").isIncremental());        // bare true
        assertTrue(PriorityParser.parse("i=?1").isIncremental());     // structured boolean true
        assertFalse(PriorityParser.parse("i=?0").isIncremental());    // structured boolean false
        assertTrue(PriorityParser.parse("i=1").isIncremental());      // tolerant numeric '1'
        assertFalse(PriorityParser.parse("i=0").isIncremental());     // tolerant numeric '0'
    }

    // ---- Unknown & invalid handling -----------------------------------------

    @Test
    void unknown_members_are_ignored() {
        final PriorityValue v = PriorityParser.parse("foo=bar, u=2, i, baz=?1");
        assertEquals(2, v.getUrgency());
        assertTrue(v.isIncremental());
    }

    @Test
    void urgency_out_of_range_is_ignored() {
        final PriorityValue v1 = PriorityParser.parse("u=9");   // >7
        assertEquals(3, v1.getUrgency());

        final PriorityValue v2 = PriorityParser.parse("u=-1");  // <0
        assertEquals(3, v2.getUrgency());
    }

    @Test
    void malformed_members_are_ignored() {
        final PriorityValue v = PriorityParser.parse("u=abc, i=banana, i=?x");
        assertEquals(3, v.getUrgency());        // default
        assertFalse(v.isIncremental());         // default
    }

    // ---- Whitespace, params, case-insensitivity -----------------------------

    @Test
    void handles_ows_and_parameters_and_case() {
        // Ignore structured-field parameters after members, and key is case-insensitive
        final PriorityValue v = PriorityParser.parse("  U = 1 ;p=v  ,  I  ;x  ");
        assertEquals(1, v.getUrgency());
        assertTrue(v.isIncremental());

        // Formatter canonicalizes output (no params, normalized layout)
        assertEquals("u=1, i", PriorityFormatter.format(v));
    }

    // ---- Minimal formatting rules -------------------------------------------

    @Test
    void formatter_emits_only_non_defaults_in_canonical_order() {
        assertEquals("u=1", PriorityFormatter.format(PriorityValue.of(1, false)));
        assertEquals("i", PriorityFormatter.format(PriorityValue.of(3, true)));
        assertEquals("u=2, i", PriorityFormatter.format(PriorityValue.of(2, true)));
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
            assertNull(out);
        } else {
            // Canonical ordering is u first (if non-default), then i if true
            if (v.getUrgency() != 3 && v.isIncremental()) {
                assertEquals("u=" + v.getUrgency() + ", i", out);
            } else if (v.getUrgency() != 3) {
                assertEquals("u=" + v.getUrgency(), out);
            } else {
                assertEquals("i", out);
            }
        }
    }
}