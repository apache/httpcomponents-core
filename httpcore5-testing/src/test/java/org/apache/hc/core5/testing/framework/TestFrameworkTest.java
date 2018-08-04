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

import org.junit.Assert;
import org.junit.Test;

public class TestFrameworkTest {
    @Test
    public void defaults() throws Exception {
        final FrameworkTest test = new FrameworkTest();
        final Map<String, Object> request = test.initRequest();

        Assert.assertNotNull("request should not be null", request);
        Assert.assertEquals("Default method should be GET", "GET", request.get(METHOD));

        Assert.assertEquals("Default request body expected.",
                TestingFramework.DEFAULT_REQUEST_BODY,
                request.get(BODY));

        Assert.assertEquals("Default request content type expected.",
                TestingFramework.DEFAULT_REQUEST_CONTENT_TYPE,
                request.get(CONTENT_TYPE));

        Assert.assertEquals("Default request query parameters expected.",
                            TestingFramework.DEFAULT_REQUEST_QUERY,
                            request.get(QUERY));

        Assert.assertEquals("Default request headers expected.",
                            TestingFramework.DEFAULT_REQUEST_HEADERS,
                            request.get(HEADERS));

        Assert.assertEquals("Default protocol version expected.",
                TestingFramework.DEFAULT_REQUEST_PROTOCOL_VERSION,
                request.get(PROTOCOL_VERSION));

        final Map<String, Object> responseExpectations = test.initResponseExpectations();
        Assert.assertNotNull("responseExpectations should not be null", responseExpectations);
        Assert.assertEquals("Default status expected.", TestingFramework.DEFAULT_RESPONSE_STATUS,
                            responseExpectations.get(STATUS));

        Assert.assertEquals("Default body expected.", TestingFramework.DEFAULT_RESPONSE_BODY,
                            responseExpectations.get(BODY));

        Assert.assertEquals("Default response content type expected.", TestingFramework.DEFAULT_RESPONSE_CONTENT_TYPE,
                responseExpectations.get(CONTENT_TYPE));

        Assert.assertEquals("Default headers expected.", TestingFramework.DEFAULT_RESPONSE_HEADERS,
                            responseExpectations.get(HEADERS));
    }

    @Test
    public void changeStatus() throws Exception {
        final Map<String, Object> testMap = new HashMap<>();
        final Map<String, Object> response = new HashMap<>();
        testMap.put(RESPONSE, response);
        response.put(STATUS, 201);

        final FrameworkTest test = new FrameworkTest(testMap);
        final Map<String, Object> responseExpectations = test.initResponseExpectations();

        Assert.assertEquals("Status unexpected.", 201, responseExpectations.get(STATUS));
    }

    @Test
    public void changeMethod() throws Exception {
        final Map<String, Object> testMap = new HashMap<>();
        final Map<String, Object> request = new HashMap<>();
        testMap.put(REQUEST, request);
        request.put(METHOD, "POST");

        final FrameworkTest test = new FrameworkTest(testMap);
        final Map<String, Object> requestExpectations = test.initRequest();

        Assert.assertEquals("Method unexpected.", "POST", requestExpectations.get(METHOD));
    }
}
