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

package org.apache.http.nio.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.nio.testserver.HttpClientNio;
import org.apache.http.nio.testserver.HttpServerNio;
import org.apache.http.nio.testserver.ServerConnectionFactory;
import org.apache.http.nio.util.TestingSupport;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.conscrypt.Conscrypt;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestJSSEProviderIntegration {

    private final static long RESULT_TIMEOUT_SEC = 30;
    private final static int REQ_NUM = 25;

    @Parameterized.Parameters(name = "{0} {1}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {"Oracle", null},
                {"Conscrypt", "TLSv1.2"},
                {"Conscrypt", "TLSv1.3"}
        });
    }

    private final String securityProviderName;
    private final String protocolVersion;

    private Provider securityProvider;
    private HttpServerNio server;
    private HttpClientNio client;

    public TestJSSEProviderIntegration(final String securityProviderName, final String protocolVersion) {
        super();
        this.securityProviderName = securityProviderName;
        this.protocolVersion = protocolVersion;
    }

    @BeforeClass
    public static void determineJavaVersion() {
        Assume.assumeTrue("Java version must be 8 or greater", TestingSupport.determineJRELevel() >= 8);
    }

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
            final URL keyStoreURL = TestJSSEProviderIntegration.class.getResource("/test-server.p12");
            final String storePassword = "nopassword";
            final SSLContext sslContext = SSLContextBuilder.create()
                    .setKeyStoreType("pkcs12")
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                    .setSecureRandom(new SecureRandom())
                    .build();

            server = new HttpServerNio();
            server.setConnectionFactory(new ServerConnectionFactory(sslContext, new SSLSetupHandler() {

                @Override
                public void initalize(final SSLEngine sslEngine) throws SSLException {
                    if (protocolVersion != null) {
                        sslEngine.setEnabledProtocols(new String[]{protocolVersion});
                    }
                }

                @Override
                public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
                }

            }));
            server.setTimeout(5000);
        }

        @Override
        protected void after() {
            if (server != null) {
                try {
                    server.shutdown();
                } catch (final Exception ignore) {
                }
            }
        }

    }).around(new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            final URL keyStoreURL = TestJSSEProviderIntegration.class.getResource("/test-client.p12");
            final String storePassword = "nopassword";
            final SSLContext sslContext = SSLContextBuilder.create()
                    .setKeyStoreType("pkcs12")
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .setSecureRandom(new SecureRandom())
                    .build();

            client = new HttpClientNio(new BasicNIOConnFactory(sslContext, new SSLSetupHandler() {

                @Override
                public void initalize(final SSLEngine sslEngine) throws SSLException {
                    if (protocolVersion != null) {
                        sslEngine.setEnabledProtocols(new String[]{protocolVersion});
                    }
                }

                @Override
                public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
                }

            }, ConnectionConfig.DEFAULT));
            client.setTimeout(5000);
        }

        @Override
        protected void after() {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (final Exception ignore) {
                }
            }
        }

    });

    private HttpHost start() throws IOException, InterruptedException {
        this.server.start();
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        return new HttpHost("localhost", address.getPort(), "https");
    }

    @Test
    public void testHttpGets() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", pattern + "x1");
            final Future<HttpResponse> future = this.client.execute(target, request);
            final HttpResponse response = future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            Assert.assertNotNull(response);
            Assert.assertEquals(pattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpGetsCloseConnection() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", pattern + "x1");
            request.addHeader(HttpHeaders.CONNECTION, "Close");
            final Future<HttpResponse> future = this.client.execute(target, request);
            final HttpResponse response = future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            Assert.assertNotNull(response);
            Assert.assertEquals(pattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpGetIdentityTransfer() throws Exception {
        this.server.setHttpProcessor(new ImmutableHttpProcessor(new ResponseServer("TEST-SERVER/1.1")));
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final HttpHost target = start();

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();

        for (int i = 0; i < REQ_NUM; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", pattern + "x1");
            final Future<HttpResponse> future = this.client.execute(target, request);
            final HttpResponse response = future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            Assert.assertNotNull(response);
            Assert.assertEquals(pattern, EntityUtils.toString(response.getEntity()));
        }
    }

}
