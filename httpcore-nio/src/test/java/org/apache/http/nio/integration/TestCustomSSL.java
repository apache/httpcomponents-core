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
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
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
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class TestCustomSSL {

    protected HttpServerNio server;
    protected HttpClientNio client;

    @After
    public void shutDownClient() throws Exception {
        if (this.client != null) {
            this.client.shutdown();
            this.client = null;
        }
    }

    @After
    public void shutDownServer() throws Exception {
        if (this.server != null) {
            this.server.shutdown();
            this.server = null;
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
                    final IOSession iosession, final SSLSession sslsession) throws SSLException {
                final BigInteger sslid = new BigInteger(sslsession.getId());
                iosession.setAttribute("ssl-id", sslid);
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

        final URL keyStoreURL = getClass().getResource("/test.keystore");
        final String storePassword = "nopassword";
        final SSLContext serverSSLContext = SSLContextBuilder.create()
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                .build();
        this.server = new HttpServerNio();
        this.server.setConnectionFactory(new ServerConnectionFactory(serverSSLContext, sslSetupHandler));
        this.server.setTimeout(5000);

        final SSLContext clientSSLContext = SSLContextBuilder.create()
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .build();

        this.client = new HttpClientNio(new BasicNIOConnFactory(new ClientConnectionFactory(clientSSLContext), null));
        this.client.setTimeout(5000);

        this.server.registerHandler("*", new BasicAsyncRequestHandler(requestHandler));

        this.server.start();
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        final Future<HttpResponse> future = this.client.execute(target, request);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

}
