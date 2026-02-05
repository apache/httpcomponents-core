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

import org.junit.jupiter.api.Assertions;

public final class HeadersMatcher {

    private HeadersMatcher() {
    }

    public static void assertSame(final Header[] actual, final Header... expected) {
        Assertions.assertNotNull(actual, "headers should not be null");
        Assertions.assertEquals(expected.length, actual.length,
                "header array length mismatch: expected=" + expected.length + ", actual=" + actual.length);
        for (int i = 0; i < actual.length; i++) {
            final Header actualHeader = actual[i];
            final Header expectedHeader = expected[i];
            Assertions.assertNotNull(expectedHeader, "expected header at index " + i + " should not be null");
            Assertions.assertNotNull(actualHeader, "actual header at index " + i + " should not be null");
            Assertions.assertTrue(expectedHeader.getName().equalsIgnoreCase(actualHeader.getName()),
                    "header name mismatch at index " + i + ": expected=" + expectedHeader.getName()
                            + ", actual=" + actualHeader.getName());
            Assertions.assertEquals(expectedHeader.getValue(), actualHeader.getValue(), "header value mismatch at index " + i + ": expected=" + expectedHeader.getValue()
                    + ", actual=" + actualHeader.getValue());
        }
    }

    public static void assertSame(final Header[] actual) {
        assertSame(actual, new Header[0]);
    }

}
