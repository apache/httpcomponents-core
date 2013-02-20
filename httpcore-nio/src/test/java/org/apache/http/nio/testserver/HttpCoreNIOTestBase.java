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

package org.apache.http.nio.testserver;

import org.apache.http.HttpHost;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.pool.NIOConnFactory;
import org.junit.After;

/**
 * Base class for all HttpCore NIO tests
 *
 */
public abstract class HttpCoreNIOTestBase {

    protected HttpServerNio server;
    protected HttpClientNio client;

    protected abstract NHttpConnectionFactory<DefaultNHttpServerConnection>
        createServerConnectionFactory() throws Exception;

    protected abstract NHttpConnectionFactory<DefaultNHttpClientConnection>
        createClientConnectionFactory() throws Exception;

    protected NIOConnFactory<HttpHost, NHttpClientConnection> createPoolConnectionFactory()
        throws Exception {
        return new BasicNIOConnFactory(createClientConnectionFactory(), null);
    }

    public void initServer() throws Exception {
        this.server = new HttpServerNio(createServerConnectionFactory());
        this.server.setExceptionHandler(new SimpleIOReactorExceptionHandler());
        this.server.setTimeout(5000);
    }

    public void initClient() throws Exception {
        this.client = new HttpClientNio(createPoolConnectionFactory());
        this.client.setExceptionHandler(new SimpleIOReactorExceptionHandler());
        this.client.setTimeout(5000);
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
