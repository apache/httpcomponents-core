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

package org.apache.hc.core5.http.message;

import org.apache.hc.core5.http.NameValuePair;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NameValuePair}.
 *
 */
class TestNameValuePair {

    @Test
    void testConstructor() {
        final NameValuePair param = new BasicNameValuePair("name", "value");
        Assertions.assertThat(param.getName()).isEqualTo("name");
        Assertions.assertThat(param.getValue()).isEqualTo("value");
    }

    @Test
    void testInvalidName() {
        Assertions.assertThatThrownBy(() ->
                        new BasicNameValuePair(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testToString() {
        final NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        Assertions.assertThat(param1.toString()).isEqualTo("name1=value1");
        final NameValuePair param2 = new BasicNameValuePair("name1", null);
        Assertions.assertThat(param2.toString()).isEqualTo("name1");
    }

    @Test
    void testNullNotEqual() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");

        Assertions.assertThat(NameValuePair).isNotEqualTo(null);
    }

    @Test
    void testObjectNotEqual() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");

        Assertions.assertThat(NameValuePair).isNotEqualTo(new Object());
    }

    @Test
    void testNameEquals() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("NAME", "value");

        Assertions.assertThat(NameValuePair2).isEqualTo(NameValuePair);
        Assertions.assertThat(NameValuePair2.hashCode()).isEqualTo(NameValuePair.hashCode());

        final NameValuePair NameValuePair3 = new BasicNameValuePair("NAME", "value");
        final NameValuePair NameValuePair4 = new BasicNameValuePair("name", "value");

        Assertions.assertThat(NameValuePair4).isEqualTo(NameValuePair3);
        Assertions.assertThat(NameValuePair4.hashCode()).isEqualTo(NameValuePair3.hashCode());
    }

    @Test
    void testValueEquals() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value");

        Assertions.assertThat(NameValuePair2).isEqualTo(NameValuePair);
        Assertions.assertThat(NameValuePair2.hashCode()).isEqualTo(NameValuePair.hashCode());
    }

    @Test
    void testNameNotEqual() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name2", "value");

        Assertions.assertThat(NameValuePair2).isNotEqualTo(NameValuePair);
    }

    @Test
    void testValueNotEqual() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value2");

        Assertions.assertThat(NameValuePair2).isNotEqualTo(NameValuePair);

        final NameValuePair NameValuePair3 = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair4 = new BasicNameValuePair("name", "VALUE");

        Assertions.assertThat(NameValuePair4).isNotEqualTo(NameValuePair3);

        final NameValuePair NameValuePair5 = new BasicNameValuePair("name", "VALUE");
        final NameValuePair NameValuePair6 = new BasicNameValuePair("name", "value");

        Assertions.assertThat(NameValuePair6).isNotEqualTo(NameValuePair5);
    }

    @Test
    void testNullValuesEquals() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", null);
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", null);

        Assertions.assertThat(NameValuePair2).isEqualTo(NameValuePair);
        Assertions.assertThat(NameValuePair2.hashCode()).isEqualTo(NameValuePair.hashCode());
    }

    @Test
    void testNullValueNotEqual() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", null);
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value");

        Assertions.assertThat(NameValuePair2).isNotEqualTo(NameValuePair);
    }

    @Test
    void testNullValue2NotEqual() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", null);

        Assertions.assertThat(NameValuePair2).isNotEqualTo(NameValuePair);
    }

    @Test
    void testEquals() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value");

        Assertions.assertThat(NameValuePair).isEqualTo(NameValuePair);
        Assertions.assertThat(NameValuePair.hashCode()).isEqualTo(NameValuePair.hashCode());
        Assertions.assertThat(NameValuePair2).isEqualTo(NameValuePair2);
        Assertions.assertThat(NameValuePair2.hashCode()).isEqualTo(NameValuePair2.hashCode());
        Assertions.assertThat(NameValuePair2).isEqualTo(NameValuePair);
        Assertions.assertThat(NameValuePair2.hashCode()).isEqualTo(NameValuePair.hashCode());
    }

    @Test
    void testHashCode() {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", null);
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name2", null);

        Assertions.assertThat(NameValuePair2.hashCode()).isNotEqualTo(NameValuePair.hashCode());

        final NameValuePair NameValuePair3 = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair4 = new BasicNameValuePair("name", "value2");

        Assertions.assertThat(NameValuePair4.hashCode()).isNotEqualTo(NameValuePair3.hashCode());

        final NameValuePair NameValuePair5 = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair6 = new BasicNameValuePair("name", null);

        Assertions.assertThat(NameValuePair6.hashCode()).isNotEqualTo(NameValuePair5.hashCode());
    }

}
