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
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.QUERY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.REQUEST;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.RESPONSE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestTestingFramework {

    @Test
    void ensureDefaultMapsUnmodifiable() {
        assertUnmodifiable(TestingFramework.DEFAULT_REQUEST_QUERY);
        assertUnmodifiable(TestingFramework.DEFAULT_RESPONSE_HEADERS);
    }

    private void assertUnmodifiable(final Map<String, String> map) {
        final String aKey = (String) map.keySet().toArray()[0];
        Assertions.assertThrows(UnsupportedOperationException.class, () -> map.remove(aKey));
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
    void runTestsWithoutSettingAdapterThrows() throws Exception {
        final TestingFramework framework = newWebServerTestingFramework();
        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void nullAdapterThrows() throws Exception {
        final ClientTestingAdapter adapter = null;

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void nullSetAdapterThrows() throws Exception {
        final ClientTestingAdapter adapter = null;

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        framework.setAdapter(adapter);
        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void goodAdapterWithConstructor() throws Exception {
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
    void goodAdapterWithSetter() throws Exception {
        final ClientTestingAdapter adapter = Mockito.mock(ClientTestingAdapter.class);

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.runTests();

        // since there are no tests, callMethod should not be called.
        verifyCallMethodNeverCalled(adapter);
    }

    @Test
    void addTest() throws Exception {
        final TestingFrameworkRequestHandler mockRequestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                assertMatchesDefaultURI(defaultURI);

                Assertions.assertNotNull(request, "request should not be null");

                // The request should be equal to the default request.
                final Map<String, Object> defaultRequest = new FrameworkTest().initRequest();
                Assertions.assertEquals(defaultRequest, request, "The request does not match the default");

                Assertions.assertSame(mockRequestHandler, requestHandler, "The request handler should have been passed to the adapter");

                // The responseExpectations should be equal to the default.
                final Map<String, Object> defaultResponseExpectations = new FrameworkTest().initResponseExpectations();
                Assertions.assertEquals(defaultResponseExpectations, responseExpectations, "The responseExpectations do not match the defaults");

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

    private void assertMatchesDefaultURI(final String defaultURI) {
        final String regex = "http://localhost:\\d+/";
        Assertions.assertTrue(defaultURI.matches(regex), "Default URI should match regex=" + regex);
    }

    @Test
    void statusCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assertions.assertEquals(200, responseExpectations.get(STATUS));

                // return a different status than expected.
                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, 201);
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
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
    void responseAlreadyChecked() throws Exception {
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
    void bodyCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_BODY, responseExpectations.get(BODY));

                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, TestingFramework.ALREADY_CHECKED);

                // return a different body than expected.
                response.put(BODY, TestingFramework.DEFAULT_RESPONSE_BODY + "junk");
                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void responseContentTypeCheck() throws Exception {
       final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_CONTENT_TYPE, responseExpectations.get(CONTENT_TYPE));

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

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void deepcopy() throws Exception {
        // save a copy of the headers to make sure they haven't changed at the end of this test.
        @SuppressWarnings("unchecked")
        final Map<String, String> headersCopy = (Map<String, String>) TestingFramework.deepcopy(TestingFramework.DEFAULT_RESPONSE_HEADERS);
        Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, headersCopy);

        final Map<String, Object> deepMap = new HashMap<>();
        deepMap.put(HEADERS, TestingFramework.DEFAULT_RESPONSE_HEADERS);

        @SuppressWarnings("unchecked")
        final Map<String, Object> deepMapCopy = (Map<String, Object>) TestingFramework.deepcopy(deepMap);
        Assertions.assertEquals(deepMap, deepMapCopy);

        @SuppressWarnings("unchecked")
        final Map<String, String> headersMap = (Map<String, String>) deepMapCopy.get(HEADERS);
        Assertions.assertEquals(headersCopy, headersMap);

        // now make sure the default headers have not changed for some unexpected reason.
        Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, headersCopy);
    }

    @Test
    void removedHeaderCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, responseExpectations.get(HEADERS));

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

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void changedHeaderCheck() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) {

                Assertions.assertEquals(TestingFramework.DEFAULT_RESPONSE_HEADERS, responseExpectations.get(HEADERS));

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

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    private Object deepcopy(final Object obj) {
        try {
            return TestingFramework.deepcopy(obj);
        } catch (final Exception e) {
            Assertions.fail("deepcopy failed: " + e.getMessage());
            return null;
        }
    }

    @Test
    void requestMethodUnexpected() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                Assertions.assertEquals("GET", request.get(METHOD));
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

        final TestingFrameworkException exception = Assertions.assertThrows(TestingFrameworkException.class, () ->
                framework.runTests());
        // make sure the HTTP Client name is in the message.
        final String message = exception.getMessage();
        final ClientPOJOAdapter pojoAdapter = adapter.getClientPOJOAdapter();
        final String httpClientName = pojoAdapter == null ?
                TestingFrameworkException.NO_HTTP_CLIENT :
                pojoAdapter.getClientName();
        Assertions.assertTrue(message.contains(httpClientName), "Message should contain httpClientName of " + httpClientName + "; message=" + message);

        Assertions.assertTrue(message.contains("MyName"), "Message should contain the test. message=" + message);
    }

    @Test
    void status201() throws Exception {
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
    void deepcopyOfTest() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {

            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations)
                       throws TestingFrameworkException {
                Assertions.assertEquals(201, responseExpectations.get(STATUS));
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
    void removeParameter() throws Exception {
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
                Assertions.assertTrue(query.containsKey("p1"));
                query.remove("p1");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void changeParameter() throws Exception {
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
                Assertions.assertTrue(query.containsKey("p1"));
                query.put("p1", query.get("p1") + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void removeHeader() throws Exception {
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
                Assertions.assertTrue(headers.containsKey("header1"));
                headers.remove("header1");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void changeHeader() throws Exception {
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
                Assertions.assertTrue(headers.containsKey("header1"));
                headers.put("header1", headers.get("header1") + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void changeBody() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                final String body = (String) request.get(BODY);
                Assertions.assertNotNull(body);
                request.put(BODY, request.get(BODY) + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void changeContentType() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the request from what is expected.
                final String contentType = (String) request.get(CONTENT_TYPE);
                Assertions.assertNotNull(contentType);
                request.put(CONTENT_TYPE, request.get(CONTENT_TYPE) + "junk");
                return super.execute(defaultURI, request, requestHandler, responseExpectations);
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void changeResponseExpectationsFails() throws Exception {
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

        Assertions.assertThrows(TestingFrameworkException.class, () -> framework.runTests());
    }

    @Test
    void changeResponseStatus() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                    final String defaultURI,
                    final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                // change the responseExpectations from what is expected.  The change should be ignored
                // by the request handler, and a 200 should actually be returned.
                Assertions.assertEquals(200, responseExpectations.get(STATUS));

                // The next line is needed because we have to make a copy of the responseExpectations.
                // It is an unmodifiable map.
                final Map<String, Object> tempResponseExpectations = new HashMap<>(responseExpectations);
                tempResponseExpectations.put(STATUS, 201);
                final Map<String, Object> response = super.execute(defaultURI, request, requestHandler, tempResponseExpectations);
                Assertions.assertEquals(200, response.get(STATUS));

                return response;
            }
        };

        final TestingFramework framework = newFrameworkAndSetAdapter(adapter);

        framework.addTest();

        framework.runTests();
    }

    @Test
    void modifyRequestCalled() throws Exception {
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
                Assertions.assertTrue(request.containsKey(UNLIKELY_ITEM), "modifyRequest should have been called.");

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
    void modifyResponseExpectationsCalled() throws Exception {
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
                Assertions.assertTrue(responseExpectations.containsKey(UNLIKELY_ITEM), "modifyResponseExpectations should have been called.");

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
    void adapterDoesNotSupport() throws Exception {

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(
                                   final String defaultURI,
                                   final Map<String, Object> request,
                                   final TestingFrameworkRequestHandler requestHandler,
                                   final Map<String, Object> responseExpectations) throws TestingFrameworkException {

                Assertions.fail("callMethod should not have been called");
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
    void defaultTestsWithMockedAdapter() throws Exception {
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
            Assertions.assertTrue(calledMethodSet.contains(method), "Method not in default tests.  method=" + method);
        }
    }

    @Test
    void defaultTests() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        // create the framework without deleting the default tests.
        final TestingFramework framework = new TestingFramework();
        framework.setAdapter(adapter);

        framework.runTests();

    }

    @Test
    void addTestNoMocks() throws TestingFrameworkException {

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
    void nulls() throws TestingFrameworkException {

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
    void parameterInPath() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                @SuppressWarnings("unchecked")
                final Map<String, String> query = (Map<String, String>) request.get(QUERY);
                Assertions.assertTrue(query.containsKey("stuffParm"), "Parameters appended to the path should have been put in the query.");

                Assertions.assertTrue(query.containsKey("stuffParm2"));
                Assertions.assertEquals("stuff", query.get("stuffParm"));
                Assertions.assertEquals("stuff2", query.get("stuffParm2"));

                Assertions.assertEquals("/stuff", request.get(PATH));
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

    @Test
    void deepcopyRejectsNonSerializable() {
        Assertions.assertThrows(TestingFrameworkException.class, () -> TestingFramework.deepcopy(new Object()));
    }

    @Test
    void addTestUsesDeepCopyAndLocksExpectations() throws Exception {
        final Map<String, Object> test = new HashMap<>();
        final Map<String, Object> request = new HashMap<>();
        request.put(METHOD, "POST");
        request.put(PATH, "copy");
        test.put(REQUEST, request);

        final Map<String, Object> response = new HashMap<>();
        response.put(STATUS, 200);
        response.put(BODY, "ok");
        response.put(CONTENT_TYPE, "text/plain");
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-A", "v1");
        response.put(HEADERS, headers);
        test.put(RESPONSE, response);

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) throws TestingFrameworkException {
                Assertions.assertEquals("POST", request.get(METHOD));
                Assertions.assertEquals("copy", request.get(PATH));
                Assertions.assertEquals(200, responseExpectations.get(STATUS));
                Assertions.assertEquals("ok", responseExpectations.get(BODY));
                Assertions.assertEquals("text/plain", responseExpectations.get(CONTENT_TYPE));
                @SuppressWarnings("unchecked")
                final Map<String, String> expectedHeaders = (Map<String, String>) responseExpectations.get(HEADERS);
                Assertions.assertEquals("v1", expectedHeaders.get("X-A"));
                Assertions.assertThrows(UnsupportedOperationException.class, () -> responseExpectations.put("x", "y"));

                final Map<String, Object> actual = new HashMap<>();
                actual.put(STATUS, responseExpectations.get(STATUS));
                actual.put(BODY, responseExpectations.get(BODY));
                actual.put(CONTENT_TYPE, responseExpectations.get(CONTENT_TYPE));
                actual.put(HEADERS, responseExpectations.get(HEADERS));
                return actual;
            }
        };

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        framework.addTest(test);

        request.put(METHOD, "GET");
        response.put(STATUS, 500);

        framework.runTests();
    }

    @Test
    void headSkipsBodyAndContentTypeChecks() throws Exception {
        final Map<String, Object> test = new HashMap<>();
        final Map<String, Object> request = new HashMap<>();
        request.put(METHOD, "HEAD");
        request.put(PATH, "head");
        test.put(REQUEST, request);

        final Map<String, Object> response = new HashMap<>();
        response.put(STATUS, 200);
        response.put(BODY, "ignored");
        response.put(CONTENT_TYPE, "text/plain");
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-Ok", "yes");
        response.put(HEADERS, headers);
        test.put(RESPONSE, response);

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) {
                final Map<String, Object> actual = new HashMap<>();
                actual.put(STATUS, 200);
                actual.put(HEADERS, responseExpectations.get(HEADERS));
                return actual;
            }
        };

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        framework.addTest(test);
        framework.runTests();
    }

    @Test
    void missingHeaderThrows() throws Exception {
        final Map<String, Object> test = new HashMap<>();
        final Map<String, Object> request = new HashMap<>();
        request.put(METHOD, "GET");
        request.put(PATH, "header-check");
        test.put(REQUEST, request);

        final Map<String, Object> response = new HashMap<>();
        response.put(STATUS, 200);
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-Need", "value");
        response.put(HEADERS, headers);
        test.put(RESPONSE, response);

        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) {
                final Map<String, Object> actual = new HashMap<>();
                actual.put(STATUS, 200);
                actual.put(HEADERS, new HashMap<String, String>());
                return actual;
            }
        };

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        framework.addTest(test);
        Assertions.assertThrows(TestingFrameworkException.class, framework::runTests);
    }

    @Test
    void nullResponseThrows() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) {
                return null;
            }
        };

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        framework.addTest();
        Assertions.assertThrows(TestingFrameworkException.class, framework::runTests);
    }

    @Test
    void nullStatusThrows() throws Exception {
        final ClientTestingAdapter adapter = new ClientTestingAdapter() {
            @Override
            public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
                    final TestingFrameworkRequestHandler requestHandler,
                    final Map<String, Object> responseExpectations) {
                final Map<String, Object> response = new HashMap<>();
                response.put(STATUS, null);
                return response;
            }
        };

        final TestingFramework framework = newWebServerTestingFramework(adapter);
        framework.addTest();
        Assertions.assertThrows(TestingFrameworkException.class, framework::runTests);
    }

}