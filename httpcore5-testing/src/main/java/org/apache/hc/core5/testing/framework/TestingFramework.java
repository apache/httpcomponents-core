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
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.REQUEST;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.RESPONSE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.io.CloseMode;

public class TestingFramework {

    /**
     * Use the ALL_METHODS list to conveniently cycle through all HTTP methods.
     */
    public static final List<String> ALL_METHODS = Arrays.asList("HEAD", "GET", "DELETE", "POST", "PUT", "PATCH");

    /**
     * If an {@link ClassicTestClientTestingAdapter} is unable to return a response in
     * the format this testing framework is needing, then it will need to check the
     * item in the response (such as body, status, headers, or contentType) itself and set
     * the returned value of the item as ALREADY_CHECKED.
     */
    public static final Object ALREADY_CHECKED = new Object();

    /**
     * If a test does not specify a path, this one is used.
     */
    public static final String DEFAULT_REQUEST_PATH = "a/path";

    /**
     * If a test does not specify a body, this one is used.
     */
    public static final String DEFAULT_REQUEST_BODY = "{\"location\":\"home\"}";

    /**
     * If a test does not specify a request contentType, this one is used.
     */
    public static final String DEFAULT_REQUEST_CONTENT_TYPE = "application/json";

    /**
     * If a test does not specify query parameters, these are used.
     */
    public static final Map<String, String> DEFAULT_REQUEST_QUERY;

    /**
     * If a test does not specify a request headers, these are used.
     */
    public static final Map<String, String> DEFAULT_REQUEST_HEADERS;

    /**
     * If a test does not specify a protocol version, this one is used.
     */
    public static final ProtocolVersion DEFAULT_REQUEST_PROTOCOL_VERSION = HttpVersion.HTTP_1_1;

    /**
     * If a test does not specify an expected response status, this one is used.
     */
    public static final int DEFAULT_RESPONSE_STATUS = 200;

    /**
     * If a test does not specify an expected response body, this one is used.
     */
    public static final String DEFAULT_RESPONSE_BODY = "{\"location\":\"work\"}";

    /**
     * If a test does not specify an expected response contentType, this one is used.
     */
    public static final String DEFAULT_RESPONSE_CONTENT_TYPE = "application/json";

    /**
     * If a test does not specify expected response headers, these are used.
     */
    public static final Map<String, String> DEFAULT_RESPONSE_HEADERS;

    static {
        final Map<String, String> request = new HashMap<>();
        request.put("p1", "this");
        request.put("p2", "that");
        DEFAULT_REQUEST_QUERY = Collections.unmodifiableMap(request);

        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "stuff");
        headers.put("header2", "more stuff");
        DEFAULT_REQUEST_HEADERS = Collections.unmodifiableMap(headers);

        headers = new HashMap<>();
        headers.put("header3", "header_three");
        headers.put("header4", "header_four");
        DEFAULT_RESPONSE_HEADERS = Collections.unmodifiableMap(headers);
    }

    private ClientTestingAdapter adapter;
    private TestingFrameworkRequestHandler requestHandler = new TestingFrameworkRequestHandler();
    private List<FrameworkTest> tests = new ArrayList<>();

    private HttpServer server;
    private int port;

    public TestingFramework() throws TestingFrameworkException {
        this(null);
    }

    public TestingFramework(final ClientTestingAdapter adapter) throws TestingFrameworkException {
        this.adapter = adapter;

        /*
         * By default, a set of tests that will exercise each HTTP method are pre-loaded.
         */
        for (final String method : ALL_METHODS) {
            final List<Integer> statusList = Arrays.asList(200, 201);
            for (final Integer status : statusList) {
                final Map<String, Object> request = new HashMap<>();
                request.put(METHOD, method);

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, status);

                final Map<String, Object> test = new HashMap<>();
                test.put(REQUEST, request);
                test.put(RESPONSE, response);

                addTest(test);
            }
        }
    }

    /**
     * This is not likely to be used except during the testing of this class.
     * It is used to inject a mocked request handler.
     *
     * @param requestHandler
     */
    public void setRequestHandler(final TestingFrameworkRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    /**
     * Run the tests that have been previously added.  First, an in-process {@link HttpServer} is
     * started.  Then, all the tests are completed by passing each test to the adapter
     * which will make the HTTP request.
     *
     * @throws TestingFrameworkException if there is a test failure or unexpected problem.
     */
    public void runTests() throws TestingFrameworkException {
        if (adapter == null) {
            throw new TestingFrameworkException("adapter should not be null");
        }

        startServer();

        try {
            for (final FrameworkTest test : tests) {
                try {
                    callAdapter(test);
                } catch (final Throwable t) {
                    processThrowable(t, test);
                }
            }
        } finally {
            stopServer();
        }
    }

    private void processThrowable(final Throwable t, final FrameworkTest test) throws TestingFrameworkException {
        final TestingFrameworkException e;
        if (t instanceof TestingFrameworkException) {
            e = (TestingFrameworkException) t;
        } else {
            e = new TestingFrameworkException(t);
        }
        e.setAdapter(adapter);
        e.setTest(test);
        throw e;
    }

    private void startServer() throws TestingFrameworkException {
        /*
         * Start an in-process server and handle all HTTP requests
         * with the requestHandler.
         */
        final SocketConfig socketConfig = SocketConfig.custom()
                                          .setSoTimeout(15000, TimeUnit.MILLISECONDS)
                                          .build();

        final ServerBootstrap serverBootstrap = ServerBootstrap.bootstrap()
                                          .setLookupRegistry(new UriPatternMatcher<>())
                                          .setSocketConfig(socketConfig)
                                          .register("/*", requestHandler);

        server = serverBootstrap.create();
        try {
            server.start();
        } catch (final IOException e) {
            throw new TestingFrameworkException(e);
        }

        port = server.getLocalPort();
    }

    private void stopServer() {
        final HttpServer local = this.server;
        this.server = null;
        if (local != null) {
            local.close(CloseMode.IMMEDIATE);
        }
    }

    private void callAdapter(final FrameworkTest test) throws TestingFrameworkException {
        Map<String, Object> request = test.initRequest();

        /*
         * If the adapter does not support the particular request, skip the test.
         */
        if (! adapter.isRequestSupported(request)) {
            return;
        }

        /*
         * Allow the adapter to modify the request before the request expectations
         * are given to the requestHandler.  Typically, adapters should not have
         * to modify the request.
         */
        request = adapter.modifyRequest(request);

        // Tell the request handler what to expect in the request.
        requestHandler.setRequestExpectations(request);

        Map<String, Object> responseExpectations = test.initResponseExpectations();
        /*
         * Allow the adapter to modify the response expectations before the handler
         * is told what to return.  Typically, adapters should not have to modify
         * the response expectations.
         */
        responseExpectations = adapter.modifyResponseExpectations(request, responseExpectations);

        // Tell the request handler what response to return.
        requestHandler.setDesiredResponse(responseExpectations);

        /*
         * Use the adapter to make the HTTP call.  Make sure the responseExpectations are not changed
         * since they have already been sent to the request handler and they will later be used
         * to check the response.
         */
        final String defaultURI = getDefaultURI();
        final Map<String, Object> response = adapter.execute(
                                                defaultURI,
                                                request,
                                                requestHandler,
                                                Collections.unmodifiableMap(responseExpectations));
        /*
         * The adapter is welcome to call assertNothingThrown() earlier, but we will
         * do it here to make sure it is done.  If the handler threw any exception
         * while checking the request it received, it will be re-thrown here.
         */
        requestHandler.assertNothingThrown();

        assertResponseMatchesExpectation(request.get(METHOD), response, responseExpectations);
    }

    @SuppressWarnings("unchecked")
    private void assertResponseMatchesExpectation(final Object method, final Map<String, Object> actualResponse,
                                                  final Map<String, Object> expectedResponse)
                                                  throws TestingFrameworkException {
        if (actualResponse == null) {
            throw new TestingFrameworkException("response should not be null");
        }
        /*
         * Now check the items in the response unless the adapter says they
         * already checked something.
         */
        if (actualResponse.get(STATUS) != TestingFramework.ALREADY_CHECKED) {
            assertStatusMatchesExpectation(actualResponse.get(STATUS), expectedResponse.get(STATUS));
        }
        if (! method.equals("HEAD")) {
            if (actualResponse.get(BODY) != TestingFramework.ALREADY_CHECKED) {
                assertBodyMatchesExpectation(actualResponse.get(BODY), expectedResponse.get(BODY));
            }
            if (actualResponse.get(CONTENT_TYPE) != TestingFramework.ALREADY_CHECKED) {
                assertContentTypeMatchesExpectation(actualResponse.get(CONTENT_TYPE), expectedResponse.get(CONTENT_TYPE));
            }
        }
        if (actualResponse.get(HEADERS) != TestingFramework.ALREADY_CHECKED) {
            assertHeadersMatchExpectation((Map<String, String>) actualResponse.get(HEADERS),
                                          (Map<String, String>) expectedResponse.get(HEADERS));
        }
    }

    private void assertStatusMatchesExpectation(final Object actualStatus, final Object expectedStatus)
            throws TestingFrameworkException {
        if (actualStatus == null) {
            throw new TestingFrameworkException("Returned status is null.");
        }
        if ((expectedStatus != null) && (! actualStatus.equals(expectedStatus))) {
            throw new TestingFrameworkException("Expected status not found. expected="
                                                  + expectedStatus + "; actual=" + actualStatus);
        }
    }

    private void assertBodyMatchesExpectation(final Object actualBody, final Object expectedBody)
        throws TestingFrameworkException {
        if (actualBody == null) {
            throw new TestingFrameworkException("Returned body is null.");
        }
        if ((expectedBody != null) && (! actualBody.equals(expectedBody))) {
            throw new TestingFrameworkException("Expected body not found. expected="
                                    + expectedBody + "; actual=" + actualBody);
        }
    }

    private void assertContentTypeMatchesExpectation(final Object actualContentType, final Object expectedContentType)
        throws TestingFrameworkException {
        if (expectedContentType != null) {
            if (actualContentType == null) {
                throw new TestingFrameworkException("Returned contentType is null.");
            }
            if (! actualContentType.equals(expectedContentType)) {
                throw new TestingFrameworkException("Expected content type not found.  expected="
                                    + expectedContentType + "; actual=" + actualContentType);
            }
        }
    }

    private void assertHeadersMatchExpectation(final Map<String, String> actualHeaders,
                                               final Map<String, String>  expectedHeaders)
            throws TestingFrameworkException {
        if (expectedHeaders == null) {
            return;
        }
        for (final Map.Entry<String, String> expectedHeader : expectedHeaders.entrySet()) {
            final String expectedHeaderName = expectedHeader.getKey();
            if (! actualHeaders.containsKey(expectedHeaderName)) {
                throw new TestingFrameworkException("Expected header not found: name=" + expectedHeaderName);
            }
            if (! actualHeaders.get(expectedHeaderName).equals(expectedHeaders.get(expectedHeaderName))) {
                throw new TestingFrameworkException("Header value not expected: name=" + expectedHeaderName
                        + "; expected=" + expectedHeaders.get(expectedHeaderName)
                        + "; actual=" + actualHeaders.get(expectedHeaderName));
            }
        }
    }

    private String getDefaultURI() {
        return "http://localhost:" + port  + "/";
    }

    /**
     * Sets the {@link ClientTestingAdapter}.
     *
     * @param adapter
     */
    public void setAdapter(final ClientTestingAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Deletes all tests.
     */
    public void deleteTests() {
        tests = new ArrayList<>();
    }

    /**
     * Call to add a test with defaults.
     *
     * @throws TestingFrameworkException
     */
    public void addTest() throws TestingFrameworkException {
        addTest(null);
    }

    /**
     * Call to add a test.  The test is a map with a REQUEST and a RESPONSE key.
     * See {@link ClientPOJOAdapter} for details on the format of the request and response.
     *
     * @param test Map with a REQUEST and a RESPONSE key.
     * @throws TestingFrameworkException
     */
    @SuppressWarnings("unchecked")
    public void addTest(final Map<String, Object> test) throws TestingFrameworkException {
        final Map<String, Object> testCopy = (Map<String, Object>) deepcopy(test);

        tests.add(new FrameworkTest(testCopy));
    }

    /**
     * Used to make a "deep" copy of an object.  This testing framework makes deep copies
     * of tests that are added as well as requestExpectations Maps and response Maps.
     *
     * @param orig a serializable object.
     * @return a deep copy of the orig object.
     * @throws TestingFrameworkException
     */
    public static Object deepcopy(final Object orig) throws TestingFrameworkException {
        try {
            // this is from http://stackoverflow.com/questions/13155127/deep-copy-map-in-groovy
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(orig);
            oos.flush();
            final ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
            final ObjectInputStream ois = new ObjectInputStream(bin);
            return ois.readObject();
        } catch (final ClassNotFoundException | IOException ex) {
            throw new TestingFrameworkException(ex);
        }
    }
}
