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

import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicHeader}.
 */
class TestBasicHeader {

    @Test
    void testConstructor() {
        final Header param = new BasicHeader("name", "value");
        Assertions.assertEquals("name", param.getName());
        Assertions.assertEquals("value", param.getValue());
    }

    @Test
    void testInvalidName() {
        Assertions.assertThrows(NullPointerException.class, () -> new BasicHeader(null, null));
    }

    @Test
    void testToString() {
        final Header param1 = new BasicHeader("name1", "value1");
        Assertions.assertEquals("name1: value1", param1.toString());
        final Header param2 = new BasicHeader("name1", null);
        Assertions.assertEquals("name1: ", param2.toString());
    }

}
