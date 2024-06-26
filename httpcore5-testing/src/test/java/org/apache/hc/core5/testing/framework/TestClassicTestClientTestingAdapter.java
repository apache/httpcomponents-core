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
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.HEADERS;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.METHOD;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PATH;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.testing.classic.EchoHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestClassicTestClientTestingAdapter {
    private static final String ECHO_PATH = "echo/something";
    private static final String CUSTOM_PATH = "custom/something";

    private ClassicTestServer server;

    @BeforeEach
    void initServer() {
       this.server = new ClassicTestServer(SocketConfig.custom()
               .setSoTimeout(5, TimeUnit.SECONDS).build());
    }

    @AfterEach
    void shutDownServer() {
        if (this.server != null) {
            this.server.shutdown(CloseMode.IMMEDIATE);
        }
    }

    @Test
    void nullDefaultURI() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = null;
        final Map<String, Object> request = new HashMap<>();
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<>();

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute(defaultURI, request, requestHandler, responseExpectations));
    }

    @Test
    void nullRequest() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = null;
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<>();

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute(defaultURI, request, requestHandler, responseExpectations));
    }

    @Test
    void nullRequestHandler() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = new HashMap<>();
        final TestingFrameworkRequestHandler requestHandler = null;
        final Map<String, Object> responseExpectations = new HashMap<>();

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute(defaultURI, request, requestHandler, responseExpectations));
    }

    @Test
    void nullResponseExpectations() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = new HashMap<>();
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = null;

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute(defaultURI, request, requestHandler, responseExpectations));
    }

    @Test
    void noPath() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = new HashMap<>();
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<>();

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute(defaultURI, request, requestHandler, responseExpectations));
    }

    @Test
    void noMethod() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";

        final Map<String, Object> request = new HashMap<>();
        request.put(PATH, ECHO_PATH);

        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<>();

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute(defaultURI, request, requestHandler, responseExpectations));
    }

    @Test
    void invalidMethod() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";

        final Map<String, Object> request = new HashMap<>();
        request.put(PATH, ECHO_PATH);
        request.put(METHOD, "JUNK");

        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<>();

        Assertions.assertThrows(TestingFrameworkException.class, () ->
                adapter.execute(defaultURI, request, requestHandler, responseExpectations));
    }

    @Test
    void withLiveServerEcho() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        // Initialize the server-side request handler
        server.register("/echo/*", new EchoHandler());


        this.server.start();
        final HttpHost target = new HttpHost("localhost", this.server.getPort());

        final String defaultURI = target.toString();
        final Map<String, Object> request = new HashMap<>();
        request.put(PATH, ECHO_PATH);
        request.put(METHOD, Method.POST.name());
        final String body = "mybody";
        request.put(BODY, body);

        final Map<String, Object> responseExpectations = new HashMap<>();

        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> response = adapter.execute(defaultURI, request, requestHandler, responseExpectations);

        Assertions.assertNotNull(response, "response should not be null");
        Assertions.assertEquals(200, response.get(STATUS), "status unexpected");

        @SuppressWarnings("unchecked")
        final Map<String, Object> headers = (Map<String, Object>) response.get(HEADERS);
        Assertions.assertNotNull(headers, "headers should be in the response");
        Assertions.assertFalse(headers.isEmpty());

        final String returnedBody = (String) response.get(BODY);
        Assertions.assertNotNull(returnedBody, "body should be in the response");
        Assertions.assertEquals(body, returnedBody, "Body should be echoed");

    }

    @Test
    void withLiveServerCustomRequestHandler() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final TestingFrameworkRequestHandler requestHandler = new TestingFrameworkRequestHandler() {
            @Override
            public void handle(final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context)
                    throws HttpException, IOException {
                try {
                    Assertions.assertEquals("junk", request.getMethod(), "method not expected");
                } catch (final Throwable t) {
                    thrown = t;
                }
            }
        };
        server.register("/custom/*", requestHandler);

        this.server.start();
        final HttpHost target = new HttpHost("localhost", this.server.getPort());
        final String defaultURI = target.toString();
        final Map<String, Object> responseExpectations = new HashMap<>();

        final Map<String, Object> request = new HashMap<>();
        request.put(PATH, CUSTOM_PATH);

        for (final String method : TestingFramework.ALL_METHODS) {
            request.put(METHOD, method);

            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
        }
    }

    @Test
    void modifyRequest() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final Map<String, Object> request = new HashMap<>();
        final Map<String, Object> returnedRequest = adapter.modifyRequest(request);

        Assertions.assertSame(request, returnedRequest, "Same request was not returned as expected.");
    }

    @Test
    void modifyResponseExpectations() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final Map<String, Object> responseExpectations = new HashMap<>();
        final Map<String, Object> returnedResponseExpectations = adapter.modifyResponseExpectations(null, responseExpectations);

        Assertions.assertSame(responseExpectations, returnedResponseExpectations, "Same response expectations were not returned as expected.");
    }

}
