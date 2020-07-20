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
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PATH;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PROTOCOL_VERSION;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.QUERY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.REQUEST;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.RESPONSE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

public class FrameworkTest {

    private Map<String, Object> request = new HashMap<>();
    private Map<String, Object> response = new HashMap<>();

    /**
     * Constructs a test with default values.
     */
    public FrameworkTest() {
        this(null);
    }

    /**
     * Constructs a test with values that are passed in as well as defaults
     * for values that are not passed in.
     *
     * @param test Contains a REQUEST and an expected RESPONSE.
     *             See {@link ClientPOJOAdapter} for details.
     */
    @SuppressWarnings("unchecked")
    public FrameworkTest(final Map<String, Object> test) {
        if (test != null) {
            if (test.containsKey(REQUEST)) {
                request = (Map<String, Object>) test.get(REQUEST);
            }
            if (test.containsKey(RESPONSE)) {
                response = (Map<String, Object>) test.get(RESPONSE);
            }
        }
    }

    /**
     * Returns a request with defaults for any parameter that is not specified.
     *
     * @return a REQUEST map.
     * @throws TestingFrameworkException a problem such as an invalid URL
     */
    public Map<String, Object> initRequest() throws TestingFrameworkException {
        // initialize to some helpful defaults
        final Map<String, Object> ret = new HashMap<>();
        ret.put(PATH, TestingFramework.DEFAULT_REQUEST_PATH);
        ret.put(BODY, TestingFramework.DEFAULT_REQUEST_BODY);
        ret.put(CONTENT_TYPE, TestingFramework.DEFAULT_REQUEST_CONTENT_TYPE);
        ret.put(QUERY, new HashMap<>(TestingFramework.DEFAULT_REQUEST_QUERY));
        ret.put(HEADERS, new HashMap<>(TestingFramework.DEFAULT_REQUEST_HEADERS));
        ret.put(PROTOCOL_VERSION, TestingFramework.DEFAULT_REQUEST_PROTOCOL_VERSION);

        // GET is the default method.
        if (! request.containsKey(METHOD)) {
            request.put(METHOD, "GET");
        }
        ret.putAll(request);

        moveAnyParametersInPathToQuery(ret);

        return ret;
    }

    private void moveAnyParametersInPathToQuery(final Map<String, Object> request) throws TestingFrameworkException {
        try {
            final String path = (String) request.get(PATH);
            if (path != null) {
                final URI uri = path.startsWith("/") ? new URI("http://localhost:8080" + path) :
                                                 new URI("http://localhost:8080/");
                final URIBuilder uriBuilder = new URIBuilder(uri, StandardCharsets.UTF_8);
                final List<NameValuePair> params = uriBuilder.getQueryParams();
                @SuppressWarnings("unchecked")
                final Map<String, Object> queryMap = (Map<String, Object>) request.get(QUERY);
                for (final NameValuePair param : params) {
                    queryMap.put(param.getName(), param.getValue());
                }
                if (! params.isEmpty()) {
                    request.put(PATH, uri.getPath());
                }
            }
        } catch (final URISyntaxException e) {
            throw new TestingFrameworkException(e);
        }
    }

    /**
     * Returns an expected response with defaults for any parameter that is not specified.
     *
     * @return the RESPONSE map.
     */
    public Map<String, Object> initResponseExpectations() {
        // 200 is the default status.
        if (! response.containsKey(STATUS)) {
            response.put(STATUS, 200);
        }

        final Map<String, Object> responseExpectations = new HashMap<>();
        // initialize to some helpful defaults
        responseExpectations.put(BODY, TestingFramework.DEFAULT_RESPONSE_BODY);
        responseExpectations.put(CONTENT_TYPE, TestingFramework.DEFAULT_RESPONSE_CONTENT_TYPE);
        responseExpectations.put(HEADERS, new HashMap<>(TestingFramework.DEFAULT_RESPONSE_HEADERS));

        // Now override any defaults with what is requested.
        responseExpectations.putAll(response);

        return responseExpectations;
    }

    @Override
    public String toString() {
        return "request: " + request + "\nresponse: " + response;
    }
}
