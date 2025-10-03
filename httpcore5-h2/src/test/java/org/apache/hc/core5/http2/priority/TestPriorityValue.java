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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TestPriorityValue {

    @Test
    void defaultsAreAsSpecified() {
        final PriorityValue v = PriorityValue.defaults();
        assertEquals(3, v.getUrgency());
        assertFalse(v.isIncremental());
    }

    @Test
    void factoryOfBuildsEquivalentInstances() {
        final PriorityValue v1 = new PriorityValue(0, true);
        final PriorityValue v2 = PriorityValue.of(0, true);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void acceptsFullValidRangeZeroToSeven() {
        for (int u = 0; u <= 7; u++) {
            final PriorityValue v = PriorityValue.of(u, false);
            assertEquals(u, v.getUrgency());
            assertFalse(v.isIncremental());
        }
    }

    @Test
    void rejectsUrgencyBelowZero() {
        final IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> new PriorityValue(-1, false));
        assertTrue(ex.getMessage().toLowerCase().contains("range"));
    }

    @Test
    void rejectsUrgencyAboveSeven() {
        final IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> PriorityValue.of(8, true));
        assertTrue(ex.getMessage().toLowerCase().contains("range"));
    }

    @Test
    void withersReturnNewInstancesAndDoNotMutate() {
        final PriorityValue base = PriorityValue.of(3, false);
        final PriorityValue u0 = base.withUrgency(0);
        final PriorityValue inc = base.withIncremental(true);

        // base remains unchanged
        assertEquals(3, base.getUrgency());
        assertFalse(base.isIncremental());

        // new values applied
        assertEquals(0, u0.getUrgency());
        assertFalse(u0.isIncremental());

        assertEquals(3, inc.getUrgency());
        assertTrue(inc.isIncremental());

        // withers create distinct instances when changing a field
        assertNotSame(base, u0);
        assertNotSame(base, inc);
    }

    @Test
    void withUrgencyValidatesRange() {
        final PriorityValue base = PriorityValue.defaults();
        assertThrows(IllegalArgumentException.class, () -> base.withUrgency(-1));
        assertThrows(IllegalArgumentException.class, () -> base.withUrgency(9));
    }

    @Test
    void equalityAndHashAreBasedOnFields() {
        final PriorityValue a = PriorityValue.of(2, true);
        final PriorityValue b = PriorityValue.of(2, true);
        final PriorityValue c = PriorityValue.of(2, false);
        final PriorityValue d = PriorityValue.of(3, true);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(c, d);
    }

    @Test
    void toStringIncludesFields() {
        final PriorityValue v = PriorityValue.of(1, true);
        final String s = v.toString();
        assertNotNull(s);
        assertTrue(s.contains("u=1"));
        assertTrue(s.contains("i=true"));
    }
}