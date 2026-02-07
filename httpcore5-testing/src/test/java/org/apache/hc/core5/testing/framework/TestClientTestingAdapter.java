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
package org.apache.hc.core5.testing.framework;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestClientTestingAdapter {

    @Test
    void getHttpClientPOJOAdapter() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final ClientPOJOAdapter pojoAdapter = adapter.getClientPOJOAdapter();

        Assertions.assertNotNull(pojoAdapter, "pojoAdapter should not be null");
    }

    @Test
    void isRequestSupported() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final Map<String, Object> request = null;
        Assertions.assertTrue(adapter.isRequestSupported(request), "isRequestSupported should return true");

    }

    @Test
    void executeCallsAssertNothingThrownWhenEnabled() throws Exception {
        final ClientPOJOAdapter pojoAdapter = new ClientPOJOAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request) {
                return new HashMap<>();
            }

            @Override
            public String getClientName() {
                return "test";
            }
        };
        final ClientTestingAdapter adapter = new ClientTestingAdapter(pojoAdapter) {
            {
                callAssertNothingThrown = true;
            }
        };

        final TestingFrameworkRequestHandler requestHandler = new TestingFrameworkRequestHandler() {
        };

        adapter.execute("http://localhost", new HashMap<>(), requestHandler, new HashMap<>());

        Assertions.assertDoesNotThrow(requestHandler::assertNothingThrown);
    }

    @Test
    void executeThrowsWhenHandlerNullAndAssertEnabled() {
        final ClientPOJOAdapter pojoAdapter = new ClientPOJOAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request) {
                return new HashMap<>();
            }

            @Override
            public String getClientName() {
                return "test";
            }
        };
        final ClientTestingAdapter adapter = new ClientTestingAdapter(pojoAdapter) {
            {
                callAssertNothingThrown = true;
            }
        };

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute("http://localhost", new HashMap<>(), null, new HashMap<>()));
    }

    @Test
    void executeThrowsWhenAdapterNull() {
        final ClientTestingAdapter adapter = new ClientTestingAdapter();

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute("http://localhost", new HashMap<>(), new TestingFrameworkRequestHandler(), new HashMap<>()));
    }
}
