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

package org.apache.http;

import org.apache.http.impl.nio.SSLNHttpClientConnectionFactory;
import org.apache.http.impl.nio.SSLNHttpServerConnectionFactory;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.testserver.HttpClientNio;
import org.apache.http.testserver.HttpServerNio;
import org.apache.http.testserver.SSLTestContexts;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for all HttpCore NIO tests
 */
public class HttpCoreNIOSSLTestBase {

    protected HttpParams serverParams;
    protected HttpParams clientParams;
    protected HttpServerNio server;
    protected HttpClientNio client;

    @Before
    public void initServer() throws Exception {
        this.serverParams = new SyncBasicHttpParams();
        this.serverParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 120000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "TEST-SERVER/1.1");
        this.server = new HttpServerNio(new SSLNHttpServerConnectionFactory(
                SSLTestContexts.createServerSSLContext(), null, this.serverParams));
        this.server.setExceptionHandler(new SimpleIOReactorExceptionHandler());
    }

    @Before
    public void initClient() throws Exception {
        this.clientParams = new SyncBasicHttpParams();
        this.clientParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 120000)
            .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 120000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.USER_AGENT, "TEST-CLIENT/1.1");
        this.client = new HttpClientNio(new SSLNHttpClientConnectionFactory(
                SSLTestContexts.createClientSSLContext(), null, this.clientParams));
        this.client.setExceptionHandler(new SimpleIOReactorExceptionHandler());
    }

    @After
    public void shutDownClient() throws Exception {
        if (this.client != null) {
            this.client.shutdown();
        }
    }

    @After
    public void shutDownServer() throws Exception {
        if (this.server != null) {
            this.server.shutdown();
        }
    }

}
