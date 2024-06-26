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

class TestClientPojoAdapter {
    @Test
    void modifyRequest() {
        final ClientPOJOAdapter adapter = new ClassicTestClientAdapter();
        final Map<String, Object> request = new HashMap<>();
        final Map<String, Object> request2 = adapter.modifyRequest(request);

        Assertions.assertSame(request, request2, "request should have been returned");
    }

    @Test
    void checkRequestSupport() throws Exception {
        final ClientPOJOAdapter adapter = new ClassicTestClientAdapter();
        final String reason = adapter.checkRequestSupport(null);

        Assertions.assertNull(reason, "reason should be null");

        adapter.assertRequestSupported(null);
    }

    @Test
    void checkRequestSupportThrows() {
        final ClientPOJOAdapter adapter = new ClientPOJOAdapter() {

            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request) throws Exception {
                return null;
            }

            @Override
            public String checkRequestSupport(final java.util.Map<String,Object> request) {
                return "A reason this request is not supported.";
            }

            @Override
            public String getClientName() {
                return null;
            }
        };

        Assertions.assertThrows(Exception.class, () -> adapter.assertRequestSupported(null));
    }
}
