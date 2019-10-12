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
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.nio.testserver.ClientConnectionFactory;
import org.apache.http.nio.testserver.HttpClientNio;
import org.apache.http.nio.testserver.HttpServerNio;
import org.apache.http.nio.testserver.ServerConnectionFactory;
import org.apache.http.nio.util.TestingSupport;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class TestTLSIntegration {

    private final static long RESULT_TIMEOUT_SEC = 30;

    private static int JRE_LEVEL = TestingSupport.determineJRELevel();

    private HttpServerNio server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void after() {
            if (server != null) {
                try {
                    server.shutdown();
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpClientNio client;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void after() {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private static SSLContext createServerSSLContext() throws Exception {
        if (JRE_LEVEL >= 8) {
            final URL keyStoreURL = TestTLSIntegration.class.getResource("/test-server.p12");
            final String storePassword = "nopassword";
            return SSLContextBuilder.create()
                    .setKeyStoreType("pkcs12")
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                    .build();
        } else {
            final URL keyStoreURL = TestTLSIntegration.class.getResource("/test.keystore");
            final String storePassword = "nopassword";
            return SSLContextBuilder.create()
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                    .build();
        }
    }

    private static SSLContext createClientSSLContext() throws Exception {
        if (JRE_LEVEL >= 8) {
            final URL keyStoreURL = TestTLSIntegration.class.getResource("/test-client.p12");
            final String storePassword = "nopassword";
            return SSLContextBuilder.create()
                    .setKeyStoreType("pkcs12")
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .build();
        } else {
            final URL keyStoreURL = TestTLSIntegration.class.getResource("/test.keystore");
            final String storePassword = "nopassword";
            return SSLContextBuilder.create()
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .build();
        }
    }

    @Test
    public void testTLSSuccess() throws Exception {
        server = new HttpServerNio();
        server.setConnectionFactory(new ServerConnectionFactory(createServerSSLContext(), null));
        server.setTimeout(5000);
        server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        server.start();

        final AtomicReference<SSLSession> sslSessionRef = new AtomicReference<SSLSession>(null);

        this.client = new HttpClientNio(new BasicNIOConnFactory(createClientSSLContext(), new SSLSetupHandler() {

            @Override
            public void initalize(final SSLEngine sslEngine) throws SSLException {

            }

            @Override
            public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
                sslSessionRef.set(sslSession);
            }

        }, ConnectionConfig.DEFAULT));
        client.setTimeout(5000);
        client.start();

        final ListenerEndpoint endpoint = server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");

        final BasicHttpRequest request = new BasicHttpRequest("GET", "BLAHx200");
        final Future<HttpResponse> future = client.execute(target, request);
        final HttpResponse response = future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
        Assert.assertThat(response, CoreMatchers.notNullValue());
        Assert.assertThat(response.getStatusLine().getStatusCode(), CoreMatchers.equalTo(200));

        final SSLSession sslSession = sslSessionRef.getAndSet(null);
        if (JRE_LEVEL >= 8) {
            Assert.assertThat(sslSession.getPeerPrincipal().getName(),
                    CoreMatchers.equalTo("CN=Test Server,OU=HttpComponents Project,O=Apache Software Foundation"));
        } else {
            Assert.assertThat(sslSession.getPeerPrincipal().getName(),
                    CoreMatchers.equalTo("CN=localhost,OU=Apache HttpComponents,O=Apache Software Foundation"));
        }
    }

    @Test
    public void testTLSTrustFailure() throws Exception {
        server = new HttpServerNio();
        server.setConnectionFactory(new ServerConnectionFactory(createServerSSLContext(), null));
        server.setTimeout(5000);
        server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        server.start();

        this.client = new HttpClientNio(new BasicNIOConnFactory(SSLContexts.createDefault(), null, ConnectionConfig.DEFAULT));
        client.setTimeout(5000);
        client.start();

        final ListenerEndpoint endpoint = server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");

        final BasicHttpRequest request = new BasicHttpRequest("GET", "BLAHx200");
        final Future<HttpResponse> future = client.execute(target, request);
        try {
            future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            Assert.fail("ExecutionException expected");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertThat(cause, CoreMatchers.<Throwable>instanceOf(SSLHandshakeException.class));
        }
    }

    @Test
    public void testTLSClientAuthFailure() throws Exception {
        server = new HttpServerNio();
        server.setConnectionFactory(new ServerConnectionFactory(createServerSSLContext(), new SSLSetupHandler() {

            @Override
            public void initalize(final SSLEngine sslEngine) throws SSLException {
                sslEngine.setNeedClientAuth(true);
            }

            @Override
            public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
            }

        }));
        server.setTimeout(5000);
        server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        server.start();

        this.client = new HttpClientNio(new BasicNIOConnFactory(createClientSSLContext(), null, ConnectionConfig.DEFAULT));
        client.setTimeout(5000);
        client.start();

        final ListenerEndpoint endpoint = server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");

        final BasicHttpRequest request = new BasicHttpRequest("GET", "BLAHx200");
        final Future<HttpResponse> future = client.execute(target, request);
        try {
            future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            Assert.fail("ExecutionException expected");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertThat(cause, CoreMatchers.<Throwable>instanceOf(IOException.class));
        }
    }

    @Test
    public void testTLSProtocolMismatch() throws Exception {
        server = new HttpServerNio();
        server.setConnectionFactory(new ServerConnectionFactory(createServerSSLContext(), new SSLSetupHandler() {

            @Override
            public void initalize(final SSLEngine sslEngine) throws SSLException {
                sslEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
            }

            @Override
            public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
            }

        }));
        server.setTimeout(5000);
        server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        server.start();

        this.client = new HttpClientNio(new BasicNIOConnFactory(createClientSSLContext(), new SSLSetupHandler() {

            @Override
            public void initalize(final SSLEngine sslEngine) throws SSLException {
                sslEngine.setEnabledProtocols(new String[]{"SSLv3"});
            }

            @Override
            public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
            }

        }, ConnectionConfig.DEFAULT));
        client.setTimeout(5000);
        client.start();

        final ListenerEndpoint endpoint = server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");

        final BasicHttpRequest request = new BasicHttpRequest("GET", "BLAHx200");
        final Future<HttpResponse> future = client.execute(target, request);
        try {
            future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            Assert.fail("ExecutionException expected");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertThat(cause, CoreMatchers.<Throwable>instanceOf(IOException.class));
        }
    }

    @Test
    public void testTLSCipherMismatch() throws Exception {
        server = new HttpServerNio();
        server.setConnectionFactory(new ServerConnectionFactory(createServerSSLContext(), new SSLSetupHandler() {

            @Override
            public void initalize(final SSLEngine sslEngine) throws SSLException {
                sslEngine.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"});
            }

            @Override
            public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
            }

        }));
        server.setTimeout(5000);
        server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        server.start();

        this.client = new HttpClientNio(new BasicNIOConnFactory(createClientSSLContext(), new SSLSetupHandler() {

            @Override
            public void initalize(final SSLEngine sslEngine) throws SSLException {
                sslEngine.setEnabledCipherSuites(new String[]{"SSL_RSA_EXPORT_WITH_RC4_40_MD5"});
            }

            @Override
            public void verify(final IOSession ioSession, final SSLSession sslSession) throws SSLException {
            }

        }, ConnectionConfig.DEFAULT));
        client.setTimeout(5000);
        client.start();

        final ListenerEndpoint endpoint = server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");

        final BasicHttpRequest request = new BasicHttpRequest("GET", "BLAHx200");
        final Future<HttpResponse> future = client.execute(target, request);
        try {
            future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            Assert.fail("ExecutionException expected");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertThat(cause, CoreMatchers.<Throwable>instanceOf(IOException.class));
        }
    }

    @Test
    public void testCustomSSLContext() throws Exception {
        final SSLSetupHandler sslSetupHandler = new SSLSetupHandler() {

            @Override
            public void initalize(
                    final SSLEngine sslengine) throws SSLException {
            }

            @Override
            public void verify(
                    final IOSession ioSession, final SSLSession sslsession) throws SSLException {
                final BigInteger sslid = new BigInteger(sslsession.getId());
                ioSession.setAttribute("ssl-id", sslid);
            }

        };

        final HttpRequestHandler requestHandler = new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpCoreContext.HTTP_CONNECTION);
                final BigInteger sslid = (BigInteger) conn.getContext().getAttribute(
                        "ssl-id");
                Assert.assertNotNull(sslid);
            }

        };

        this.server = new HttpServerNio();
        this.server.setConnectionFactory(new ServerConnectionFactory(createServerSSLContext(), sslSetupHandler));
        this.server.setTimeout(5000);
        this.server.registerHandler("*", new BasicAsyncRequestHandler(requestHandler));
        this.server.start();

        this.client = new HttpClientNio(new BasicNIOConnFactory(new ClientConnectionFactory(createClientSSLContext()), null));
        this.client.setTimeout(5000);
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final Future<HttpResponse> future = this.client.execute(target, request);
        final HttpResponse response = future.get(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

}
