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

package org.apache.hc.core5.http.testserver.nio;

import java.net.URL;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.impl.nio.HttpAsyncRequestExecutor;
import org.apache.hc.core5.http.nio.HttpAsyncExpectationVerifier;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.junit.After;

/**
 * Base class for all HttpCore NIO integration tests
 *
 */
public abstract class HttpCoreNIOTestBase {

    public enum ProtocolScheme { http, https }

    private final ProtocolScheme scheme;

    protected HttpServerNio server;
    protected HttpClientNio client;

    public HttpCoreNIOTestBase(final ProtocolScheme scheme) {
        this.scheme = scheme;
    }

    public HttpCoreNIOTestBase() {
        this(ProtocolScheme.http);
    }

    public ProtocolScheme getScheme() {
        return this.scheme;
    }

    protected SSLContext createServerSSLContext() throws Exception {
        final URL keyStoreURL = getClass().getResource("/test.keystore");
        final String storePassword = "nopassword";
        return SSLContextBuilder.create()
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                .build();
    }

    protected SSLContext createClientSSLContext() throws Exception {
        final URL keyStoreURL = getClass().getResource("/test.keystore");
        final String storePassword = "nopassword";
        return SSLContextBuilder.create()
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .build();
    }

    protected HttpProcessor createServerHttpProcessor() {
        return new DefaultHttpProcessor(
                new HttpRequestInterceptor[] {
                        new RequestValidateHost()
                },
                new HttpResponseInterceptor[]{
                        new ResponseDate(),
                        new ResponseServer("TEST-SERVER/1.1"),
                        new ResponseContent(),
                        new ResponseConnControl()
                });
    }

    protected HttpAsyncExpectationVerifier createExpectationVerifier() {
        return null;
    }

    protected HttpProcessor createClientHttpProcessor() {
        return new DefaultHttpProcessor(
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent("TEST-CLIENT/1.1"),
                new RequestExpectContinue());
    }

    protected ServerConnectionFactory createServerConnectionFactory() throws Exception {
        return new ServerConnectionFactory(
                this.scheme.equals(ProtocolScheme.https) ? createServerSSLContext() : null);
    }

    protected IOReactorConfig createServerIOReactorConfig() {
        return IOReactorConfig.custom()
                .setSoTimeout(5000)
                .build();
    }

    protected IOReactorConfig createClientIOReactorConfig() {
        return IOReactorConfig.custom()
                .setConnectTimeout(5000)
                .setSoTimeout(5000)
                .build();
    }

    protected HttpAsyncRequestExecutor createHttpAsyncRequestExecutor() throws Exception {
        return new HttpAsyncRequestExecutor();
    }

    protected ClientConnectionFactory createClientConnectionFactory() throws Exception {
        return new ClientConnectionFactory(
                this.scheme.equals(ProtocolScheme.https) ? createClientSSLContext() : null);
    }

    public void initServer() throws Exception {
        this.server = new HttpServerNio(
                createServerHttpProcessor(),
                createServerConnectionFactory(),
                createExpectationVerifier(),
                createServerIOReactorConfig());
    }

    public void initClient() throws Exception {
        this.client = new HttpClientNio(
                createClientHttpProcessor(),
                createHttpAsyncRequestExecutor(),
                createClientConnectionFactory(),
                createClientIOReactorConfig());
    }

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

}
