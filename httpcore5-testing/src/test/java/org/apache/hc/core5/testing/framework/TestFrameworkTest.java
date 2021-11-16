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

import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.BODY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.CONTENT_TYPE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.HEADERS;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.METHOD;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PROTOCOL_VERSION;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.QUERY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.REQUEST;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.RESPONSE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestFrameworkTest {
    @Test
    public void defaults() throws Exception {
        final FrameworkTest test = new FrameworkTest();
        final Map<String, Object> request = test.initRequest();

        Assertions.assertNotNull(request, "request should not be null");
        Assertions.assertEquals(request.get(METHOD), "GET", "Default method should be GET");

        Assertions.assertEquals(TestingFramework.DEFAULT_REQUEST_BODY,
                request.get(BODY), "Default request body expected.");

        Assertions.assertEquals(TestingFramework.DEFAULT_REQUEST_CONTENT_TYPE,
                request.get(CONTENT_TYPE), "Default request content type expected.");

        Assertions.assertEquals(TestingFramework.DEFAULT_REQUEST_QUERY,
                            request.get(QUERY), "Default request query parameters expected.");

        Assertions.assertEquals(TestingFramework.DEFAULT_REQUEST_HEADERS,
                            request.get(HEADERS), "Default request headers expected.");

        Assertions.assertEquals(TestingFramework.DEFAULT_REQUEST_PROTOCOL_VERSION,
                request.get(PROTOCOL_VERSION), "Default protocol version expected.");

        final Map<String, Object> responseExpectations = test.initResponseExpectations();
        Assertions.assertNotNull(responseExpectations, "responseExpectations should not be null");
        Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_STATUS,
                            responseExpectations.get(STATUS), "Default status expected.");

        Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_BODY,
                            responseExpectations.get(BODY), "Default body expected.");

        Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_CONTENT_TYPE,
                responseExpectations.get(CONTENT_TYPE), "Default response content type expected.");

        Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS,
                            responseExpectations.get(HEADERS), "Default headers expected.");
    }

    @Test
    public void changeStatus() throws Exception {
        final Map<String, Object> testMap = new HashMap<>();
        final Map<String, Object> response = new HashMap<>();
        testMap.put(RESPONSE, response);
        response.put(STATUS, 201);

        final FrameworkTest test = new FrameworkTest(testMap);
        final Map<String, Object> responseExpectations = test.initResponseExpectations();

        Assertions.assertEquals(201, responseExpectations.get(STATUS), "Status unexpected.");
    }

    @Test
    public void changeMethod() throws Exception {
        final Map<String, Object> testMap = new HashMap<>();
        final Map<String, Object> request = new HashMap<>();
        testMap.put(REQUEST, request);
        request.put(METHOD, "POST");

        final FrameworkTest test = new FrameworkTest(testMap);
        final Map<String, Object> requestExpectations = test.initRequest();

        Assertions.assertEquals("POST", requestExpectations.get(METHOD), "Method unexpected.");
    }
}
