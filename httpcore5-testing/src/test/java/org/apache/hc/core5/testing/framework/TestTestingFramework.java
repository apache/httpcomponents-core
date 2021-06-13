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
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.NAME;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PATH;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PROTOCOL_VERSION;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.QUERY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.REQUEST;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.RESPONSE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestTestingFramework {

    @Test
    public void ensureDefaultMapsUnmodifiable() throws Exception {
        assertUnmodifiable(TestingFramework.DEFAULT_REQUEST_QUERY);
        assertUnmodifiable(TestingFramework.DEFAULT_RESPONSE_HEADERS);
    }

    private void assertUnmodifiable(final Map<String, String> map) {
        final String aKey = (String) map.keySet().toArray()[0];
        Assert.assertThrows(UnsupportedOperationException.class, () -> map.remove(aKey));
    }

    private TestingFramework newWebServerTestingFramework(final ClientTestingAdapter adapter)
            throws TestingFrameworkException {
        final TestingFramework framework = new TestingFramework(adapter);
        // get rid of the default tests.
        framework.deleteTests();

        return framework;
    }

    private TestingFramework newWebServerTestingFramework() throws TestingFrameworkException {
        return newWebServerTestingFramework(null); // null adapter
    }

    @Test
    public void runTestsWithoutSettingAdapterThrows() throws Exception {
        final TestingFramework framework = newWebServerTestingFramework();
        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void nullAdapterThrows() throws Exception {
        final ClientTestingAdapter adapter = null;

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void nullSetAdapterThrows() throws Exception {
        final ClientTestingAdapter adapter = null;

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        framework.setAdapter(adapter);
        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void goodAdapterWithConstructor() throws Exception {
        final ClientTestingAdapter adapter = Mockito.mock(ClientTestingAdapter.class);

        // Have isRequestSupported() return false so no test will run.
        Mockito.when(adapter.isRequestSupported(ArgumentMatchers.anyMap()))
                     .thenReturn(false);

        final TestingFramework framework = newWebServerTestingFramework(adapter);

        framework.runTests();

        // since there are no tests, callMethod should not be called.
        verifyCallMethodNeverCalled(adapter);
    }

    private void verifyCallMethodNeverCalled(final ClientTestingAdapter adapter) throws Exception {
        Mockito.verify(adapter, Mockito.never()).execute(ArgumentMatchers.anyString(), ArgumentMatchers.anyMap(),
                       ArgumentMatchers.any(TestingFrameworkRequestHandler.class), ArgumentMatchers.anyMap());
    }

    private TestingFramework newFrameworkAndSetAdapter(final ClientTestingAdapter adapter)
            throws TestingFrameworkException {
        final TestingFramework framework = new TestingFramework();
        framework.setAdapter(adapter);

        // get rid of the default tests.
        framework.deleteTests();

        return framework;
    }

    @Test
    public void goodAdapterWithSetter() throws Exception {
        final ClientTestingAdapter adapter = Mockito.mock(ClientTestingAdapter.class);

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.runTests();

        // since there are no tests, callMethod should not be called.
        verifyCallMethodNeverCalled(adapter);
    }

    @Test
    public void addTest() throws Exception {
        final TestingFrameworkRequestHandler mockRequestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                MatcherAssert.assertThat(defaultURI, matchesDefaultURI());

                Assert.assertNotNull("request should not be null", request);

                // The request should be equal to the default request.
                final Map<String, Object> defaultRequest = new FrameworkTest().initRequest();
                Assert.assertEquals("The request does not match the default", defaultRequest, request);

                Assert.assertSame("The request handler should have been passed to the adapter",
                                  mockRequestHandler, requestHandler);

                // The responseExpectations should be equal to the default.
                final Map<String, Object> defaultResponseExpectations = new FrameworkTest().initResponseExpectations();
                Assert.assertEquals("The responseExpectations do not match the defaults",
                                    defaultResponseExpectations, responseExpectations);

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, responseExpectations.get(STATUS));
                response.put(BODY, responseExpectations.get(BODY));
                response.put(CONTENT_TYPE, responseExpectations.get(CONTENT_TYPE));
                response.put(HEADERS, responseExpectations.get(HEADERS));
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);
        framework.setRequestHandler(mockRequestHandler);

        framework.addTest();

        framework.runTests();

        // assertNothingThrown() should have been called.
        Mockito.verify(mockRequestHandler).assertNothingThrown();
    }

    private Matcher<String> matchesDefaultURI() {
        final Matcher<String> matcher = new BaseMatcher<String>() {
            private final String regex = "http://localhost:\\d+/";

            @Override
            public boolean matches(final Object o) {
                return ((String) o).matches(regex);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("matches regex=" + regex);
            }
        };

        return matcher;
    }

    @Test
    public void statusCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assert.assertEquals(200, responseExpectations.get(STATUS));

                // return a different status than expected.
                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, 201);
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    private Map<String, Object> alreadyCheckedResponse() {
        // return an indication that the response has already been checked.
        final Map<String, Object> response = new HashMap<>();
        response.put(STATUS, TestingFramework.ALREADY_CHECKED);
        response.put(BODY, TestingFramework.ALREADY_CHECKED);
        response.put(CONTENT_TYPE, TestingFramework.ALREADY_CHECKED);
        response.put(HEADERS, TestingFramework.ALREADY_CHECKED);
        return response;
    }

    @Test
    public void responseAlreadyChecked() throws Exception {
            final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                return alreadyCheckedResponse();
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        framework.runTests();
    }

    @Test
    public void bodyCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assert.assertEquals(TestingFramework.DEFAULT_RESPONSE_BODY, responseExpectations.get(BODY));

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, TestingFramework.ALREADY_CHECKED);

                // return a different body than expected.
                response.put(BODY, TestingFramework.DEFAULT_RESPONSE_BODY + "junk");
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void responseContentTypeCheck() throws Exception {
       final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assert.assertEquals(TestingFramework.DEFAULT_RESPONSE_CONTENT_TYPE, responseExpectations.get(CONTENT_TYPE));

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, TestingFramework.ALREADY_CHECKED);
                response.put(HEADERS, TestingFramework.ALREADY_CHECKED);

                // return the expected body
                response.put(BODY, TestingFramework.DEFAULT_RESPONSE_BODY);
                // return a different content type than expected.
                response.put(CONTENT_TYPE, ContentType.DEFAULT_TEXT);
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void deepcopy() throws Exception {
        // save a copy of the headers to make sure they haven't changed at the end of this test.
        @SuppressWarnings("unchecked")
        final Map<String, String> headersCopy = (Map<String, String>) TestingFramework.deepcopy(TestingFramework.DEFAULT_RESPONSE_HEADERS);
        Assert.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, headersCopy);

        final Map<String, Object> deepMap = new HashMap<>();
        deepMap.put(HEADERS, TestingFramework.DEFAULT_RESPONSE_HEADERS);

        @SuppressWarnings("unchecked")
        final Map<String, Object> deepMapCopy = (Map<String, Object>) TestingFramework.deepcopy(deepMap);
        Assert.assertEquals(deepMap, deepMapCopy);

        @SuppressWarnings("unchecked")
        final Map<String, String> headersMap = (Map<String, String>) deepMapCopy.get(HEADERS);
        Assert.assertEquals(headersCopy, headersMap);

        // now make sure the default headers have not changed for some unexpected reason.
        Assert.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, headersCopy);
    }

    @Test
    public void removedHeaderCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assert.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, responseExpectations.get(HEADERS));

                @SuppressWarnings("unchecked")
                final Map<String, String> headersCopy = (Map<String, String>) deepcopy(responseExpectations.get(HEADERS));

                // remove a header to force an error
                final String headerName = (String) headersCopy.keySet().toArray()[0];
                headersCopy.remove(headerName);

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, TestingFramework.ALREADY_CHECKED);
                response.put(BODY, TestingFramework.ALREADY_CHECKED);

                // return different headers than expected.
                response.put(HEADERS, headersCopy);
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changedHeaderCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assert.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, responseExpectations.get(HEADERS));

                @SuppressWarnings("unchecked")
                final Map<String, String> headersCopy = (Map<String, String>) deepcopy(responseExpectations.get(HEADERS));

                // change a header to force an error
                final String headerName = (String) headersCopy.keySet().toArray()[0];
                headersCopy.put(headerName, headersCopy.get(headerName) + "junk");

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, TestingFramework.ALREADY_CHECKED);
                response.put(BODY, TestingFramework.ALREADY_CHECKED);

                // return different headers than expected.
                response.put(HEADERS, headersCopy);
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    private Object deepcopy(final Object obj) {
        try {
            return TestingFramework.deepcopy(obj);
        } catch (final Exception e) {
            Assert.fail("deepcopy failed: " + e.getMessage());
            return null;
        }
    }

    @Test
    public void requestMethodUnexpected() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                Assert.assertEquals("GET", request.get(METHOD));
                request.put(METHOD, "POST");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        final Map<String, Object> test = new HashMap<>();
        final Map<String, Object> request = new HashMap<>();
        test.put(REQUEST, request);
        request.put(NAME, "MyName");

        framework.addTest(test);

        final TestingFrameworkException exception = Assert.assertThrows(TestingFrameworkException.class, () ->
                framework.runTests());
        // make sure the HTTP Client name is in the message.
        final String message = exception.getMessage();
        final ClientPOJOAdapter pojoAdapter = adapter.getClientPOJOAdapter();
        final String httpClientName = pojoAdapter == null ?
                TestingFrameworkException.NO_HTTP_CLIENT :
                pojoAdapter.getClientName();
        Assert.assertTrue(
                "Message should contain httpClientName of " + httpClientName + "; message=" + message,
                message.contains(httpClientName));

        Assert.assertTrue(
                "Message should contain the test. message=" + message,
                message.contains("MyName"));
    }

    @Test
    public void status201() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        final Map<String, Object> test = new HashMap<>();
        final Map<String, Object> response = new HashMap<>();
        test.put(RESPONSE, response);
        response.put(STATUS, 201);

        framework.addTest(test);

        framework.runTests();
    }

    @Test
    public void deepcopyOfTest() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {

            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations)
                       throws TestingFrameworkException {
                Assert.assertEquals(201, responseExpectations.get(STATUS));
                return alreadyCheckedResponse();
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        final Map<String, Object> test = new HashMap<>();
        final Map<String, Object> response = new HashMap<>();
        test.put(RESPONSE, response);
        response.put(STATUS, 201);

        framework.addTest(test);

        // Make sure the framework makes a copy of the test for itself.
        // This put should be ignored by the framework.
        response.put(STATUS, 300);

        framework.runTests();
    }

    @Test
    public void removeParameter() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                @SuppressWarnings("unchecked")
                final Map<String, String> query = (Map<String, String>) request.get(QUERY);
                Assert.assertTrue(query.containsKey("p1"));
                query.remove("p1");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changeParameter() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                @SuppressWarnings("unchecked")
                final Map<String, String> query = (Map<String, String>) request.get(QUERY);
                Assert.assertTrue(query.containsKey("p1"));
                query.put("p1", query.get("p1") + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void removeHeader() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                @SuppressWarnings("unchecked")
                final Map<String, String> headers = (Map<String, String>) request.get(HEADERS);
                Assert.assertTrue(headers.containsKey("header1"));
                headers.remove("header1");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changeHeader() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                @SuppressWarnings("unchecked")
                final Map<String, String> headers = (Map<String, String>) request.get(HEADERS);
                Assert.assertTrue(headers.containsKey("header1"));
                headers.put("header1", headers.get("header1") + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changeBody() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                final String body = (String) request.get(BODY);
                Assert.assertNotNull(body);
                request.put(BODY, request.get(BODY) + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changeContentType() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                final String contentType = (String) request.get(CONTENT_TYPE);
                Assert.assertNotNull(contentType);
                request.put(CONTENT_TYPE, request.get(CONTENT_TYPE) + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changeProtocolVersion() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                final ProtocolVersion protocolVersion = (ProtocolVersion) request.get(PROTOCOL_VERSION);
                Assert.assertNotNull(protocolVersion);
                request.put(PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changeResponseExpectationsFails() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {

                /*
                 * The adapter should change the responseExpectations in the modifyResponseExpectations()
                 * method before they are sent to the request handler.  The expectations should not
                 * be changed here.
                 */
                // the next command should throw.
                responseExpectations.put(STATUS, 201);
                return null;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assert.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    public void changeResponseStatus() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the responseExpectations from what is expected.  The change should be ignored
                // by the request handler, and a 200 should actually be returned.
                Assert.assertEquals(200, responseExpectations.get(STATUS));

                // The next line is needed because we have to make a copy of the responseExpectations.
                // It is an unmodifiable map.
                final Map<String, Object> tempResponseExpectations = new HashMap<>(responseExpectations);
                tempResponseExpectations.put(STATUS, 201);
                final Map<String, Object> response = super.execute(defaultURI, request, requestHandler, tempResponseExpectations);
                Assert.assertEquals(200,  response.get(STATUS));

                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        framework.runTests();
    }

    @Test
    public void modifyRequestCalled() throws Exception {
        final TestingFrameworkRequestHandler mockRequestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final String UNLIKELY_ITEM = "something_unlikely_to_be_in_a_real_request";

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // make sure the modifyRequest method was called by seeing if the request was modified.
                Assert.assertTrue("modifyRequest should have been called.", request.containsKey(UNLIKELY_ITEM));

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, responseExpectations.get(STATUS));
                response.put(BODY, responseExpectations.get(BODY));
                response.put(CONTENT_TYPE, responseExpectations.get(CONTENT_TYPE));
                response.put(HEADERS, responseExpectations.get(HEADERS));
                return response;
            }

            @Override
            public Map<String, Object> modifyRequest(final Map<String, Object> request) {
                // let the adapter change the request if needed.
                request.put(UNLIKELY_ITEM, new Object());
                return super.modifyRequest(request);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);
        framework.setRequestHandler(mockRequestHandler);

        framework.addTest();

        framework.runTests();

        // assertNothingThrown() should have been called.
        Mockito.verify(mockRequestHandler).assertNothingThrown();
    }

    @Test
    public void modifyResponseExpectationsCalled() throws Exception {
        final TestingFrameworkRequestHandler mockRequestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final String UNLIKELY_ITEM = "something_unlikely_to_be_in_a_real_response";

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // make sure the modifyRequest method was called by seeing if the request was modified.
                Assert.assertTrue("modifyResponseExpectations should have been called.", responseExpectations.containsKey(UNLIKELY_ITEM));

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, responseExpectations.get(STATUS));
                response.put(BODY, responseExpectations.get(BODY));
                response.put(CONTENT_TYPE, responseExpectations.get(CONTENT_TYPE));
                response.put(HEADERS, responseExpectations.get(HEADERS));
                return response;
            }

            @Override
            public Map<String, Object> modifyResponseExpectations(
                    final Map<String, Object> request,
                    final Map<String, Object> responseExpectations) {
                // let the adapter change the request if needed.
                responseExpectations.put(UNLIKELY_ITEM, new Object());
                return super.modifyResponseExpectations(request, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);
        framework.setRequestHandler(mockRequestHandler);

        framework.addTest();

        framework.runTests();

        // assertNothingThrown() should have been called.
        Mockito.verify(mockRequestHandler).assertNothingThrown();
    }

    @Test
    public void adapterDoesNotSupport() throws Exception {

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) throws TestingFrameworkException {

                Assert.fail("callMethod should not have been called");
                return null;
            }

            @Override
            public boolean isRequestSupported(final Map<String, Object> request) {
                return false;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        framework.runTests();
    }

    @Test
    public void defaultTestsWithMockedAdapter() throws Exception {
        final Set<String> calledMethodSet = new HashSet<>();

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) throws TestingFrameworkException {

                calledMethodSet.add((String) request.get(METHOD));
                return alreadyCheckedResponse();
            }
        };

        // create the framework without deleting the default tests.
        final TestingFramework framework = new TestingFramework();
        framework.setAdapter(adapter);

        framework.runTests();

        for (final String method : TestingFramework.ALL_METHODS) {
            Assert.assertTrue("Method not in default tests.  method=" + method, calledMethodSet.contains(method));
        }
    }

    @Test
    public void defaultTests() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        // create the framework without deleting the default tests.
        final TestingFramework framework = new TestingFramework();
        framework.setAdapter(adapter);

        framework.runTests();

    }

    @Test
    public void addTestNoMocks() throws TestingFrameworkException {

        final TestingFramework framework = new TestingFramework(new ClassicTestClientTestingAdapter());

//      The following test could be constructed in Groovy/Spock like this:
//
//      HttpServerTestingFramework.ALL_METHODS.each { method ->
//          framework.addTest(
//                              request: [
//                                  path: '/stuff',
//                                  method:method,
//                                  query: [param : 'something'],
//                                  headers: [header1:'stuff', header2:'more-stuff'],
//                                  contentType: 'text/plain; charset=us-ascii',
//                                  body: 'What is the meaning of life?',
//                              ],
//                              response: [
//                                  status:201,
//                                  headers: [header3:'header_stuff',],
//                                  contentType: 'text/html; charset=us-ascii',
//                                  body: responseBody,
//                              ],
//          )

        final Map<String, Object> test = new HashMap<>();

        // Add request.
        final Map<String, Object> request = new HashMap<>();
        test.put(REQUEST, request);

        request.put(PATH, "stuff");

        final Map<String, Object> queryMap = new HashMap<>();
        request.put(QUERY, queryMap);

        queryMap.put("param", "something");

        final Map<String, Object> requestHeadersMap = new HashMap<>();
        request.put(HEADERS, requestHeadersMap);

        requestHeadersMap.put("header1", "stuff");
        requestHeadersMap.put("header2", "more-stuff");

        request.put(CONTENT_TYPE, "text/plain; charset=us-ascii");
        request.put(BODY, "What is the meaning of life?");

        // Response
        final Map<String, Object> response = new HashMap<>();
        test.put(RESPONSE, response);

        response.put(STATUS, 201);

        final Map<String, Object> responseHeadersMap = new HashMap<>();
        response.put(HEADERS, responseHeadersMap);

        responseHeadersMap.put("header3", "header_stuff");

        response.put(CONTENT_TYPE, "text/html; charset=us-ascii");
        response.put(BODY, "<HTML>42</HTML>");

        for (final String method : TestingFramework.ALL_METHODS) {
            request.put(METHOD, method);

            framework.addTest(test);
        }
        framework.runTests();
    }

    @Test
    public void nulls() throws TestingFrameworkException {

        final TestingFramework framework = new TestingFramework(new ClassicTestClientTestingAdapter());

//      The following test could be constructed in Groovy/Spock like this:
//
//      WebServerTestingFramework.ALL_METHODS.each { method ->
//           framework.addTest(
//                              request: [
//                                  path: null,
//                                  method:method,
//                                  query: null,
//                                  headers: null,
//                                  contentType: null,
//                                  body: null,
//                              ],
//                              response: [
//                                  status:null,
//                                  headers: null,
//                                  contentType: null,
//                                  body: null,
//                              ],
//          )

        final Map<String, Object> test = new HashMap<>();

        // Add request.
        final Map<String, Object> request = new HashMap<>();
        test.put(REQUEST, request);

        request.put(PATH, null);

        request.put(QUERY, null);


        request.put(HEADERS, null);

        request.put(CONTENT_TYPE, null);
        request.put(BODY, null);

        // Response
        final Map<String, Object> response = new HashMap<>();
        test.put(RESPONSE, response);

        response.put(STATUS, null);

        response.put(HEADERS, null);

        response.put(CONTENT_TYPE, null);
        response.put(BODY, null);

        for (final String method : TestingFramework.ALL_METHODS) {
            request.put(METHOD, method);

            framework.addTest(test);
        }
        framework.runTests();
    }

    @Test
    public void parameterInPath() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                @SuppressWarnings("unchecked")
                final Map<String, String> query = (Map<String, String>) request.get(QUERY);
                Assert.assertTrue("Parameters appended to the path should have been put in the query.",
                                   query.containsKey("stuffParm"));

                Assert.assertTrue(query.containsKey("stuffParm2"));
                Assert.assertEquals("stuff", query.get("stuffParm"));
                Assert.assertEquals("stuff2", query.get("stuffParm2"));

                Assert.assertEquals("/stuff", request.get(PATH));
                return alreadyCheckedResponse();
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        final Map<String, Object> test = new HashMap<>();

        // Add request.
        final Map<String, Object> request = new HashMap<>();
        test.put(REQUEST, request);

        request.put(PATH, "/stuff?stuffParm=stuff&stuffParm2=stuff2");

        framework.addTest(test);

        framework.runTests();
    }

}