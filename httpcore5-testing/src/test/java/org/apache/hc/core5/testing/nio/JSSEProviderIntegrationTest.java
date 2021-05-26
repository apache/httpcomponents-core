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
import java.util.Arrays;
import java.util.Collection;
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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class JSSEProviderIntegrationTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Parameterized.Parameters(name = "{0} {1}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {"Oracle", null},
                {"Conscrypt", "TLSv1.2"},
                {"Conscrypt", "TLSv1.3"},
        });
    }

    private final String securityProviderName;
    private final String protocolVersion;

    public JSSEProviderIntegrationTest(final String securityProviderName, final String protocolVersion) {
        super();
        this.securityProviderName = securityProviderName;
        this.protocolVersion = protocolVersion;
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);
    private static final int REQ_NUM = 25;

    private Provider securityProvider;
    private Http1TestServer server;
    private Http1TestClient client;

    @Rule
    public TestRule resourceRules = RuleChain.outerRule(new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            if ("Conscrypt".equalsIgnoreCase(securityProviderName)) {
                try {
                    securityProvider = Conscrypt.newProviderBuilder().provideTrustManager(true).build();
                } catch (final UnsatisfiedLinkError e) {
                    Assume.assumeFalse("Conscrypt provider failed to be loaded: " + e.getMessage(), true);
                }
            } else {
                securityProvider = null;
            }
            if (securityProvider != null) {
                Security.insertProviderAt(securityProvider, 1);
            }
        }

        @Override
        protected void after() {
            if (securityProvider != null) {
                Security.removeProvider(securityProvider.getName());
                securityProvider = null;
            }
        }

    }).around(new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");

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
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                server.shutdown(TimeValue.ofSeconds(5));
            }
        }

    }).around(new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");

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
        protected void after() {
            log.debug("Shutting down test client");
            if (client != null) {
                client.shutdown(TimeValue.ofSeconds(5));
            }
        }

    });

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
            Assert.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assert.assertNotNull(response1);
            Assert.assertEquals(200, response1.getCode());
            Assert.assertEquals("Hi there", entity1);
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
                Assert.assertNotNull(result);
                final HttpResponse response1 = result.getHead();
                final String entity1 = result.getBody();
                Assert.assertNotNull(response1);
                Assert.assertEquals(200, response1.getCode());
                Assert.assertEquals("Hi there", entity1);
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
                Assert.assertNotNull(result);
                final HttpResponse response = result.getHead();
                final String entity = result.getBody();
                Assert.assertNotNull(response);
                Assert.assertEquals(200, response.getCode());
                Assert.assertEquals("Hi there", entity);
            }
        }
    }

}
