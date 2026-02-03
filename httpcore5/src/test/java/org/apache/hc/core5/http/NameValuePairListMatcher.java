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
package org.apache.hc.core5.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;

public final class NameValuePairListMatcher {

    private NameValuePairListMatcher() {
    }

    public static void assertEqualsTo(final List<NameValuePair> actual, final NameValuePair... expected) {
        assertEqualsTo(actual, Arrays.asList(expected));
    }

    public static void assertEmpty(final List<NameValuePair> actual) {
        assertEqualsTo(actual, Collections.emptyList());
    }

    private static void assertEqualsTo(final List<NameValuePair> actual,
            final List<? extends NameValuePair> expected) {
        Assertions.assertNotNull(actual, "name-value list should not be null");
        Assertions.assertEquals(expected.size(), actual.size(),
                "name-value list size mismatch: expected=" + expected.size() + ", actual=" + actual.size());
        for (int i = 0; i < actual.size(); i++) {
            final NameValuePair actualNvp = actual.get(i);
            final NameValuePair expectedNvp = expected.get(i);
            Assertions.assertNotNull(expectedNvp, "expected name-value pair at index " + i + " should not be null");
            Assertions.assertNotNull(actualNvp, "actual name-value pair at index " + i + " should not be null");
            Assertions.assertEquals(actualNvp.getName(), expectedNvp.getName(), "name mismatch at index " + i + ": expected=" + expectedNvp.getName()
                    + ", actual=" + actualNvp.getName());
            Assertions.assertEquals(actualNvp.getValue(), expectedNvp.getValue(), "value mismatch at index " + i + ": expected=" + expectedNvp.getValue()
                    + ", actual=" + actualNvp.getValue());
        }
    }

}
