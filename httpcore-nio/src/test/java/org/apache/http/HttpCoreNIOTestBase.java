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

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.http.mockup.HttpClientNio;
import org.apache.http.mockup.HttpServerNio;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;

/**
 * Base class for all HttpCore NIO tests
 *
 */
public class HttpCoreNIOTestBase extends TestCase {

    public HttpCoreNIOTestBase(String testName) {
        super(testName);
    }

    protected HttpServerNio server;
    protected HttpClientNio client;

    @Override
    protected void setUp() throws Exception {
        HttpParams serverParams = new SyncBasicHttpParams();
        serverParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 60000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "TEST-SERVER/1.1");

        this.server = new HttpServerNio(serverParams);
        this.server.setExceptionHandler(new SimpleIOReactorExceptionHandler());

        HttpParams clientParams = new SyncBasicHttpParams();
        clientParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 60000)
            .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 60000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.USER_AGENT, "TEST-CLIENT/1.1");

        this.client = new HttpClientNio(clientParams);
        this.client.setExceptionHandler(new SimpleIOReactorExceptionHandler());
    }

    @Override
    protected void tearDown() {
        try {
            this.client.shutdown();
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
        }
        try {
            this.server.shutdown();
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
        }
    }

}
