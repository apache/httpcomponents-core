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

package org.apache.hc.core5.testing.nio;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;


//    @Parameterized.Parameters(name = "{0} {1}")
//    public static Collection<Object[]> protocols() {
//        return Arrays.asList(new Object[][]{
//                {"Oracle", null},
//                {"Conscrypt", "TLSv1.2"},
//                {"Conscrypt", "TLSv1.3"},
//        });
//    }


public abstract class JSSEProviderIntegrationTest {

    private final String securityProviderName;
    private final String protocolVersion;

    public JSSEProviderIntegrationTest(final String securityProviderName, final String protocolVersion) {
        super();
        this.securityProviderName = securityProviderName;
        this.protocolVersion = protocolVersion;
    }

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);
    private static final int REQ_NUM = 25;

    private Provider securityProvider;

    class SecurityProviderResource implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(final ExtensionContext context) throws Exception {
            if ("Conscrypt".equalsIgnoreCase(securityProviderName)) {
                try {
                    securityProvider = Conscrypt.newProviderBuilder().provideTrustManager(true).build();
                } catch (final UnsatisfiedLinkError e) {
                    Assertions.fail("Conscrypt provider failed to be loaded: " + e.getMessage());
                }
            } else {
                securityProvider = null;
            }
            if (securityProvider != null) {
                Security.insertProviderAt(securityProvider, 1);
            }
        }

        @Override
        public void afterEach(final ExtensionContext context) throws Exception {
            if (securityProvider != null) {
                Security.removeProvider(securityProvider.getName());
                securityProvider = null;
            }
        }

    }

    @RegisterExtension
    @Order(1)
    private final SecurityProviderResource securityProviderResource = new SecurityProviderResource();

    private Http1TestServer server;

    class ServerResource implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(final ExtensionContext context) throws Exception {
            final URL keyStoreURL = getClass().getResource("/test-server.p12");
            final String storePassword = "nopassword";

            server = new Http1TestServer(
                    IOReactorConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build(),
                    SSLContextBuilder.create()
                            .setProvider(securityProvider)
                            .setKeyStoreType("pkcs12")
                            .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                            .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                            .setSecureRandom(new SecureRandom())
                            .build(),
                    (endpoint, sslEngine) -> {
                        if (protocolVersion != null) {
                            sslEngine.setEnabledProtocols(new String[]{protocolVersion});
                        }
                    },
                    null);
        }

        @Override
        public void afterEach(final ExtensionContext context) throws Exception {
            if (server != null) {
                server.shutdown(TimeValue.ofSeconds(5));
            }
        }

    }

    @RegisterExtension
    @Order(2)
    private final ServerResource serverResource = new ServerResource();

    private Http1TestClient client;

    class ClientResource implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(final ExtensionContext context) throws Exception {
            final URL keyStoreURL = getClass().getResource("/test-client.p12");
            final String storePassword = "nopassword";

            client = new Http1TestClient(
                    IOReactorConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build(),
                    SSLContextBuilder.create()
                            .setProvider(securityProvider)
                            .setKeyStoreType("pkcs12")
                            .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                            .setSecureRandom(new SecureRandom())
                            .build(),
                    (endpoint, sslEngine) -> {
                        if (protocolVersion != null) {
                            sslEngine.setEnabledProtocols(new String[]{protocolVersion});
                        }
                    },
                    null);
        }

        @Override
        public void afterEach(final ExtensionContext context) throws Exception {
            if (client != null) {
                client.shutdown(TimeValue.ofSeconds(5));
            }
        }

    }

    @RegisterExtension
    @Order(3)
    private final ClientResource clientResource = new ClientResource();

    private URI createRequestURI(final InetSocketAddress serverEndpoint, final String path) {
        try {
            return new URI("https", null, "localhost", serverEndpoint.getPort(), path, null, null);
        } catch (final URISyntaxException e) {
            throw new IllegalStateException();
        }
    }

    @Test
    public void testSimpleGet() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < REQ_NUM; i++) {
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi there", entity1);
        }
    }

    @Test
    public void testSimpleGetConnectionClose() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        client.start();
        final URI requestURI = createRequestURI(serverEndpoint, "/hello");
        for (int i = 0; i < REQ_NUM; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(
                    "localhost", serverEndpoint.getPort(), TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        AsyncRequestBuilder.get(requestURI)
                                .addHeader(HttpHeaders.CONNECTION, "close")
                                .build(),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
                final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertNotNull(result);
                final HttpResponse response1 = result.getHead();
                final String entity1 = result.getBody();
                Assertions.assertNotNull(response1);
                Assertions.assertEquals(200, response1.getCode());
                Assertions.assertEquals("Hi there", entity1);
            }
        }
    }

    @Test
    public void testSimpleGetIdentityTransfer() throws Exception {
        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final HttpProcessor httpProcessor = new DefaultHttpProcessor(new RequestValidateHost());
        final InetSocketAddress serverEndpoint = server.start(httpProcessor, Http1Config.DEFAULT);

        client.start();

        for (int i = 0; i < REQ_NUM; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(
                    "localhost", serverEndpoint.getPort(), TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        new BasicRequestProducer(Method.GET, createRequestURI(serverEndpoint, "/hello")),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
                final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertNotNull(result);
                final HttpResponse response = result.getHead();
                final String entity = result.getBody();
                Assertions.assertNotNull(response);
                Assertions.assertEquals(200, response.getCode());
                Assertions.assertEquals("Hi there", entity);
            }
        }
    }

}
