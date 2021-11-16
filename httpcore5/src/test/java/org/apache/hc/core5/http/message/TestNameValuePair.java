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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NameValuePair}.
 *
 */
public class TestNameValuePair {

    @Test
    public void testConstructor() {
        final NameValuePair param = new BasicNameValuePair("name", "value");
        Assertions.assertEquals("name", param.getName());
        Assertions.assertEquals("value", param.getValue());
    }

    @Test
    public void testInvalidName() {
        Assertions.assertThrows(NullPointerException.class, () -> new BasicNameValuePair(null, null));
    }

    @Test
    public void testToString() {
        final NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        Assertions.assertEquals("name1=value1", param1.toString());
        final NameValuePair param2 = new BasicNameValuePair("name1", null);
        Assertions.assertEquals("name1", param2.toString());
    }

    @Test
    public void testNullNotEqual() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");

        Assertions.assertNotEquals(null, NameValuePair);
    }

    @Test
    public void testObjectNotEqual() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");

        Assertions.assertNotEquals(NameValuePair, new Object());
    }

    @Test
    public void testNameEquals() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("NAME", "value");

        Assertions.assertEquals(NameValuePair, NameValuePair2);
        Assertions.assertEquals(NameValuePair.hashCode(), NameValuePair2.hashCode());

        final NameValuePair NameValuePair3 = new BasicNameValuePair("NAME", "value");
        final NameValuePair NameValuePair4 = new BasicNameValuePair("name", "value");

        Assertions.assertEquals(NameValuePair3, NameValuePair4);
        Assertions.assertEquals(NameValuePair3.hashCode(), NameValuePair4.hashCode());
    }

    @Test
    public void testValueEquals() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value");

        Assertions.assertEquals(NameValuePair, NameValuePair2);
        Assertions.assertEquals(NameValuePair.hashCode(), NameValuePair2.hashCode());
    }

    @Test
    public void testNameNotEqual() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name2", "value");

        Assertions.assertNotEquals(NameValuePair, NameValuePair2);
    }

    @Test
    public void testValueNotEqual() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value2");

        Assertions.assertNotEquals(NameValuePair, NameValuePair2);

        final NameValuePair NameValuePair3 = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair4 = new BasicNameValuePair("name", "VALUE");

        Assertions.assertNotEquals(NameValuePair3, NameValuePair4);

        final NameValuePair NameValuePair5 = new BasicNameValuePair("name", "VALUE");
        final NameValuePair NameValuePair6 = new BasicNameValuePair("name", "value");

        Assertions.assertNotEquals(NameValuePair5, NameValuePair6);
    }

    @Test
    public void testNullValuesEquals() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", null);
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", null);

        Assertions.assertEquals(NameValuePair, NameValuePair2);
        Assertions.assertEquals(NameValuePair.hashCode(), NameValuePair2.hashCode());
    }

    @Test
    public void testNullValueNotEqual() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", null);
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value");

        Assertions.assertNotEquals(NameValuePair, NameValuePair2);
    }

    @Test
    public void testNullValue2NotEqual() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", null);

        Assertions.assertNotEquals(NameValuePair, NameValuePair2);
    }

    @Test
    public void testEquals() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name", "value");

        Assertions.assertEquals(NameValuePair, NameValuePair);
        Assertions.assertEquals(NameValuePair.hashCode(), NameValuePair.hashCode());
        Assertions.assertEquals(NameValuePair2, NameValuePair2);
        Assertions.assertEquals(NameValuePair2.hashCode(), NameValuePair2.hashCode());
        Assertions.assertEquals(NameValuePair, NameValuePair2);
        Assertions.assertEquals(NameValuePair.hashCode(), NameValuePair2.hashCode());
    }

    @Test
    public void testHashCode() throws Exception {
        final NameValuePair NameValuePair = new BasicNameValuePair("name", null);
        final NameValuePair NameValuePair2 = new BasicNameValuePair("name2", null);

        Assertions.assertNotEquals(NameValuePair.hashCode(), NameValuePair2.hashCode());

        final NameValuePair NameValuePair3 = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair4 = new BasicNameValuePair("name", "value2");

        Assertions.assertNotEquals(NameValuePair3.hashCode(), NameValuePair4.hashCode());

        final NameValuePair NameValuePair5 = new BasicNameValuePair("name", "value");
        final NameValuePair NameValuePair6 = new BasicNameValuePair("name", null);

        Assertions.assertNotEquals(NameValuePair5.hashCode(), NameValuePair6.hashCode());
    }

}
