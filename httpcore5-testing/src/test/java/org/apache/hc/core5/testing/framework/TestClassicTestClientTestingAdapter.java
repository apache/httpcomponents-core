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
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestClassicTestClientTestingAdapter {
    private static final String ECHO_PATH = "echo/something";
    private static final String CUSTOM_PATH = "custom/something";

    private ClassicTestServer server;

   @Before
    public void initServer() throws Exception {
        this.server = new ClassicTestServer();
        this.server.setTimeout(5000);
    }

    @After
    public void shutDownServer() throws Exception {
        if (this.server != null) {
            this.server.shutdown(0, TimeUnit.SECONDS);
        }
    }

    @Test
    public void nullDefaultURI() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = null;
        final Map<String, Object> request = new HashMap<String, Object>();
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        try {
            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
            Assert.fail("WebServerTestingFrameworkException should have been thrown");
        } catch (TestingFrameworkException e) {
            // expected
        }
    }

    @Test
    public void nullRequest() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = null;
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        try {
            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
            Assert.fail("WebServerTestingFrameworkException should have been thrown");
        } catch (TestingFrameworkException e) {
            // expected
        }
    }

    @Test
    public void nullRequestHandler() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = new HashMap<String, Object>();
        final TestingFrameworkRequestHandler requestHandler = null;
        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        try {
            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
            Assert.fail("WebServerTestingFrameworkException should have been thrown");
        } catch (TestingFrameworkException e) {
            // expected
        }
    }

    @Test
    public void nullResponseExpectations() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = new HashMap<String, Object>();
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = null;

        try {
            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
            Assert.fail("WebServerTestingFrameworkException should have been thrown");
        } catch (TestingFrameworkException e) {
            // expected
        }
    }

    @Test
    public void noPath() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";
        final Map<String, Object> request = new HashMap<String, Object>();
        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        try {
            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
            Assert.fail("WebServerTestingFrameworkException should have been thrown");
        } catch (TestingFrameworkException e) {
            // expected
        }
    }

    @Test
    public void noMethod() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";

        final Map<String, Object> request = new HashMap<String, Object>();
        request.put(PATH, ECHO_PATH);

        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        try {
            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
            Assert.fail("WebServerTestingFrameworkException should have been thrown");
        } catch (TestingFrameworkException e) {
            // expected
        }
    }

    @Test
    public void invalidMethod() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final String defaultURI = "";

        final Map<String, Object> request = new HashMap<String, Object>();
        request.put(PATH, ECHO_PATH);
        request.put(METHOD, "JUNK");

        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        try {
            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
            Assert.fail("WebServerTestingFrameworkException should have been thrown");
        } catch (TestingFrameworkException e) {
            // expected
        }
    }

    @Test
    public void withLiveServerEcho() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        // Initialize the server-side request handler
        server.registerHandler("/echo/*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity entity = request.getEntity();
                // For some reason, just putting the incoming entity into
                // the response will not work. We have to buffer the message.
                final byte[] data;
                if (entity == null) {
                    data = new byte [0];
                } else {
                    data = EntityUtils.toByteArray(entity);
                }

                final ByteArrayEntity bae = new ByteArrayEntity(data);
                if (entity != null) {
                    bae.setContentType(entity.getContentType());
                }

                response.setCode(HttpStatus.SC_OK);
                response.setEntity(bae);
            }

        });


        this.server.start();
        final HttpHost target = new HttpHost("localhost", this.server.getPort());

        final String defaultURI = target.toString();
        final Map<String, Object> request = new HashMap<String, Object>();
        request.put(PATH, ECHO_PATH);
        request.put(METHOD, "POST");
        final String body = "mybody";
        request.put(BODY, body);

        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        final TestingFrameworkRequestHandler requestHandler = Mockito.mock(TestingFrameworkRequestHandler.class);
        final Map<String, Object> response = adapter.execute(defaultURI, request, requestHandler, responseExpectations);

        Assert.assertNotNull("response should not be null", response);
        Assert.assertEquals("status unexpected", 200, response.get(STATUS));

        @SuppressWarnings("unchecked")
        final Map<String, Object> headers = (Map<String, Object>) response.get(HEADERS);
        Assert.assertNotNull("headers should be in the response", headers);
        Assert.assertFalse(headers.isEmpty());

        final String returnedBody = (String) response.get(BODY);
        Assert.assertNotNull("body should be in the response", returnedBody);
        Assert.assertEquals("Body should be echoed", body, returnedBody);

    }

    @Test
    public void withLiveServerCustomRequestHandler() throws Exception {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final TestingFrameworkRequestHandler requestHandler = new TestingFrameworkRequestHandler() {
            @Override
            public void handle(final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context)
                    throws HttpException, IOException {
                try {
                    Assert.assertEquals("method not expected", "junk", request.getMethod());
                } catch (Throwable t) {
                    thrown = t;
                }
            }
        };
        server.registerHandler("/custom/*", requestHandler);

        this.server.start();
        final HttpHost target = new HttpHost("localhost", this.server.getPort());
        final String defaultURI = target.toString();
        final Map<String, Object> responseExpectations = new HashMap<String, Object>();

        final Map<String, Object> request = new HashMap<String, Object>();
        request.put(PATH, CUSTOM_PATH);

        for (String method : TestingFramework.ALL_METHODS) {
            request.put(METHOD, method);

            adapter.execute(defaultURI, request, requestHandler, responseExpectations);
        }
    }

    @Test
    public void modifyRequest() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final Map<String, Object> request = new HashMap<String, Object>();
        final Map<String, Object> returnedRequest = adapter.modifyRequest(request);

        Assert.assertSame("Same request was not returned as expected.", request, returnedRequest);
    }

    @Test
    public void modifyResponseExpectations() {
        final ClientTestingAdapter adapter = new ClassicTestClientTestingAdapter();

        final Map<String, Object> responseExpectations = new HashMap<String, Object>();
        final Map<String, Object> returnedResponseExpectations = adapter.modifyResponseExpectations(null, responseExpectations);

        Assert.assertSame("Same response expectations were not returned as expected.", responseExpectations, returnedResponseExpectations);
    }
}
