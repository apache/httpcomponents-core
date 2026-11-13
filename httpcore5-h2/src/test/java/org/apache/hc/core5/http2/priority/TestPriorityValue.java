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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

final class TestPriorityValue {

    @Test
    void defaultsAreAsSpecified() {
        final PriorityValue v = PriorityValue.defaults();
        Assertions.assertThat(v.getUrgency()).isEqualTo(3);
        Assertions.assertThat(v.isIncremental()).isFalse();
    }

    @Test
    void factoryOfBuildsEquivalentInstances() {
        final PriorityValue v1 = new PriorityValue(0, true);
        final PriorityValue v2 = PriorityValue.of(0, true);
        Assertions.assertThat(v2).isEqualTo(v1);
        Assertions.assertThat(v2.hashCode()).isEqualTo(v1.hashCode());
    }

    @Test
    void acceptsFullValidRangeZeroToSeven() {
        for (int u = 0; u <= 7; u++) {
            final PriorityValue v = PriorityValue.of(u, false);
            Assertions.assertThat(v.getUrgency()).isEqualTo(u);
            Assertions.assertThat(v.isIncremental()).isFalse();
        }
    }

    @Test
    void rejectsUrgencyBelowZero() {
        final IllegalArgumentException ex =
                Assertions.assertThatThrownBy(() ->
                                new PriorityValue(-1, false))
                        .isInstanceOf(IllegalArgumentException.class).asInstanceOf(InstanceOfAssertFactories.type(IllegalArgumentException.class)).actual();
        Assertions.assertThat(ex.getMessage().toLowerCase().contains("range")).isTrue();
    }

    @Test
    void rejectsUrgencyAboveSeven() {
        final IllegalArgumentException ex =
                Assertions.assertThatThrownBy(() ->
                                PriorityValue.of(8, true))
                        .isInstanceOf(IllegalArgumentException.class).asInstanceOf(InstanceOfAssertFactories.type(IllegalArgumentException.class)).actual();
        Assertions.assertThat(ex.getMessage().toLowerCase().contains("range")).isTrue();
    }

    @Test
    void withersReturnNewInstancesAndDoNotMutate() {
        final PriorityValue base = PriorityValue.of(3, false);
        final PriorityValue u0 = base.withUrgency(0);
        final PriorityValue inc = base.withIncremental(true);

        // base remains unchanged
        Assertions.assertThat(base.getUrgency()).isEqualTo(3);
        Assertions.assertThat(base.isIncremental()).isFalse();

        // new values applied
        Assertions.assertThat(u0.getUrgency()).isEqualTo(0);
        Assertions.assertThat(u0.isIncremental()).isFalse();

        Assertions.assertThat(inc.getUrgency()).isEqualTo(3);
        Assertions.assertThat(inc.isIncremental()).isTrue();

        // withers create distinct instances when changing a field
        Assertions.assertThat(u0).isNotSameAs(base);
        Assertions.assertThat(inc).isNotSameAs(base);
    }

    @Test
    void withUrgencyValidatesRange() {
        final PriorityValue base = PriorityValue.defaults();
        Assertions.assertThatThrownBy(() ->
                        base.withUrgency(-1))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        base.withUrgency(9))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityAndHashAreBasedOnFields() {
        final PriorityValue a = PriorityValue.of(2, true);
        final PriorityValue b = PriorityValue.of(2, true);
        final PriorityValue c = PriorityValue.of(2, false);
        final PriorityValue d = PriorityValue.of(3, true);

        Assertions.assertThat(b).isEqualTo(a);
        Assertions.assertThat(b.hashCode()).isEqualTo(a.hashCode());

        Assertions.assertThat(c).isNotEqualTo(a);
        Assertions.assertThat(d).isNotEqualTo(a);
        Assertions.assertThat(d).isNotEqualTo(c);
    }

    @Test
    void toStringIncludesFields() {
        final PriorityValue v = PriorityValue.of(1, true);
        final String s = v.toString();
        Assertions.assertThat(s).isNotNull();
        Assertions.assertThat(s.contains("u=1")).isTrue();
        Assertions.assertThat(s.contains("i=true")).isTrue();
    }
}