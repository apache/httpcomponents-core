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

final class TestPriorityParamsParser {

    @Test
    void captures_presence_correctly() {
        final PriorityParams p1 = PriorityParamsParser.parse("u=1");
        Assertions.assertThat(p1.getUrgency()).isEqualTo(1);
        Assertions.assertThat(p1.getIncremental()).isNull();

        final PriorityParams p2 = PriorityParamsParser.parse("i");
        Assertions.assertThat(p2.getUrgency()).isNull();
        Assertions.assertThat(p2.getIncremental()).isEqualTo(Boolean.TRUE);

        final PriorityParams p3 = PriorityParamsParser.parse("u=5, i");
        Assertions.assertThat(p3.getUrgency()).isEqualTo(5);
        Assertions.assertThat(p3.getIncremental()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void ignores_unknown_and_out_of_range() {
        final PriorityParams p = PriorityParamsParser.parse("foo=bar, u=9, i=?0");
        Assertions.assertThat(p.getUrgency()).isNull();                 // out-of-range ignored => absent
        Assertions.assertThat(p.getIncremental()).isEqualTo(Boolean.FALSE);
    }

    @Test
    void toValueWithDefaults_applies_defaults() {
        final PriorityParams p = PriorityParamsParser.parse("i");
        final PriorityValue v = p.toValueWithDefaults();
        Assertions.assertThat(v.getUrgency()).isEqualTo(3);
        Assertions.assertThat(v.isIncremental()).isTrue();
    }
}
