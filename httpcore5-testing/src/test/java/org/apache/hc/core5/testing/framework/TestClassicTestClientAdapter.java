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
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestClassicTestClientAdapter {

    private ClassicTestServer server;

    @BeforeEach
    void initServer() {
        this.server = new ClassicTestServer(SocketConfig.custom()
                .setSoTimeout(5, TimeUnit.SECONDS)
                .build());
    }

    @AfterEach
    void shutDownServer() {
        if (this.server != null) {
            this.server.shutdown(CloseMode.IMMEDIATE);
        }
    }

    @Test
    void executeBuildsRequestAndParsesResponse() throws Exception {
        final Map<String, Object> captured = new HashMap<>();
        server.register("*", (request, response, context) -> {
            captured.put("method", request.getMethod());
            captured.put("version", request.getVersion());
            final URI requestUri;
            try {
                requestUri = request.getUri();
            } catch (final URISyntaxException ex) {
                captured.put("uri-error", ex);
                response.setEntity(new StringEntity("bad uri", ContentType.TEXT_PLAIN));
                return;
            }
            captured.put("path", requestUri.getPath());
            captured.put("query", requestUri.getQuery());
            captured.put("header", request.getFirstHeader("X-Test") != null
                    ? request.getFirstHeader("X-Test").getValue()
                    : null);
            if (request.getEntity() != null) {
                captured.put("content-type", request.getEntity().getContentType());
                captured.put("body", EntityUtils.toString(request.getEntity()));
            } else {
                captured.put("content-type", null);
                captured.put("body", null);
            }

            response.addHeader("X-Reply", "ok");
            response.setEntity(new StringEntity("pong", ContentType.TEXT_PLAIN));
        });

        server.start();
        final String defaultURI = new HttpHost("localhost", server.getPort()).toString();

        final Map<String, Object> request = new HashMap<>();
        request.put(PATH, "echo");
        request.put(METHOD, Method.POST.name());
        request.put(BODY, "ping");
        request.put(CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
        request.put(PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "value");
        request.put(HEADERS, headers);
        final Map<String, String> query = new HashMap<>();
        query.put("a", "b");
        query.put("c", "d");
        request.put(QUERY, query);

        final ClassicTestClientAdapter adapter = new ClassicTestClientAdapter();
        final Map<String, Object> response = adapter.execute(defaultURI, request);

        Assertions.assertNull(captured.get("uri-error"));
        Assertions.assertEquals(Method.POST.name(), captured.get("method"));
        final Object version = captured.get("version");
        Assertions.assertTrue(HttpVersion.HTTP_1_0.equals(version) || HttpVersion.HTTP_1_1.equals(version));
        final String path = (String) captured.get("path");
        Assertions.assertNotNull(path);
        Assertions.assertTrue(path.endsWith("/echo"));
        final String queryString = (String) captured.get("query");
        Assertions.assertNotNull(queryString);
        Assertions.assertTrue(queryString.contains("a=b"));
        Assertions.assertTrue(queryString.contains("c=d"));
        Assertions.assertEquals("value", captured.get("header"));
        final String capturedContentType = (String) captured.get("content-type");
        Assertions.assertNotNull(capturedContentType);
        Assertions.assertTrue(capturedContentType.contains("text/plain"));
        Assertions.assertEquals("ping", captured.get("body"));

        Assertions.assertEquals(200, response.get(STATUS));
        Assertions.assertEquals("pong", response.get(BODY));
        final String contentType = (String) response.get(CONTENT_TYPE);
        Assertions.assertNotNull(contentType);
        Assertions.assertTrue(contentType.contains("text/plain"));
        @SuppressWarnings("unchecked")
        final Map<String, Object> responseHeaders = (Map<String, Object>) response.get(HEADERS);
        Assertions.assertEquals("ok", responseHeaders.get("X-Reply"));
    }

    @Test
    void executeWithoutEntityReturnsNullBody() throws Exception {
        server.register("/empty", (request, response, context) -> {
            Assertions.assertNull(request.getEntity());
            response.addHeader("X-Empty", "yes");
            response.setCode(204);
        });

        server.start();
        final String defaultURI = new HttpHost("localhost", server.getPort()).toString();

        final Map<String, Object> request = new HashMap<>();
        request.put(PATH, "empty");
        request.put(METHOD, Method.GET.name());

        final ClassicTestClientAdapter adapter = new ClassicTestClientAdapter();
        final Map<String, Object> response = adapter.execute(defaultURI, request);

        Assertions.assertEquals(204, response.get(STATUS));
        Assertions.assertNull(response.get(BODY));
        Assertions.assertNull(response.get(CONTENT_TYPE));
        @SuppressWarnings("unchecked")
        final Map<String, Object> responseHeaders = (Map<String, Object>) response.get(HEADERS);
        Assertions.assertEquals("yes", responseHeaders.get("X-Empty"));
    }

    @Test
    void getClientName() {
        final ClassicTestClientAdapter adapter = new ClassicTestClientAdapter();
        Assertions.assertEquals("ClassicTestClient", adapter.getClientName());
    }

}
