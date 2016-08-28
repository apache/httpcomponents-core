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

package org.apache.hc.core5.http.integration;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestHandler;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.nio.NHttpConnection;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.testserver.nio.ClientConnectionFactory;
import org.apache.hc.core5.http.testserver.nio.HttpCoreNIOTestBase;
import org.apache.hc.core5.http.testserver.nio.ServerConnectionFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLSetupHandler;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCustomSSL extends HttpCoreNIOTestBase {

    public TestCustomSSL() {
        super(ProtocolScheme.https);
    }

    @Override
    protected ServerConnectionFactory createServerConnectionFactory() throws Exception {
        final SSLSetupHandler sslSetupHandler = new SSLSetupHandler() {

            @Override
            public void initalize(
                    final SSLEngine sslengine) throws SSLException {
            }

            @Override
            public void verify(
                    final IOSession iosession, final SSLSession sslsession) throws SSLException {
                final BigInteger sslid = new BigInteger(sslsession.getId());
                iosession.setAttribute("ssl-id", sslid);
            }

        };
        final URL keyStoreURL = getClass().getResource("/test.keystore");
        final String storePassword = "nopassword";
        final SSLContext serverSSLContext = SSLContextBuilder.create()
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                .build();
        return new ServerConnectionFactory(serverSSLContext, sslSetupHandler);
    }

    @Override
    protected ClientConnectionFactory createClientConnectionFactory() throws Exception {
        final URL keyStoreURL = getClass().getResource("/test.keystore");
        final String storePassword = "nopassword";
        final SSLContext clientSSLContext = SSLContextBuilder.create()
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .build();

        return new ClientConnectionFactory(clientSSLContext, null);
    }

    @Before
    public void setUp() throws Exception {
        initServer();
        initClient();
    }

    @After
    public void tearDown() throws Exception {
        shutDownClient();
        shutDownServer();
    }

    @Test
    public void testCustomSSLContext() throws Exception {
        final HttpRequestHandler requestHandler = new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpCoreContext.HTTP_CONNECTION);
                final BigInteger sslid = (BigInteger) conn.getContext().getAttribute(
                        "ssl-id");
                Assert.assertNotNull(sslid);
            }

        };
        this.server.registerHandler("*", new BasicAsyncRequestHandler(requestHandler));

        this.server.start();
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final Future<ClassicHttpResponse> future = this.client.execute(target, request);
        final ClassicHttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
    }

}
