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

package org.apache.hc.core5.testing.nio.extension;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.nio.Http1TestClient;
import org.apache.hc.core5.testing.nio.Http1TestServer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http1TestResources implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(Http1TestResources.class);

    private final URIScheme scheme;
    private final Timeout socketTimeout;

    private Http1TestServer server;
    private Http1TestClient client;

    public Http1TestResources(final URIScheme scheme, final Timeout socketTimeout) {
        this.scheme = scheme;
        this.socketTimeout = socketTimeout;
    }

    @Override
    public void beforeEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Starting up test server");
        server = new Http1TestServer(
                IOReactorConfig.custom()
                        .setSoTimeout(socketTimeout)
                        .build(),
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null);

        LOG.debug("Starting up test client");
        client = new Http1TestClient(
                IOReactorConfig.custom()
                        .setSoTimeout(socketTimeout)
                        .build(),
                scheme == URIScheme.HTTPS ? SSLTestContexts.createClientSSLContext() : null, null, null);
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Shutting down test client");
        if (client != null) {
            client.shutdown(TimeValue.ofSeconds(5));
        }

        LOG.debug("Shutting down test server");
        if (server != null) {
            server.shutdown(TimeValue.ofSeconds(5));
        }
    }

    public Http1TestClient client() {
        Assertions.assertNotNull(client);
        return client;
    }

    public Http1TestServer server() {
        Assertions.assertNotNull(server);
        return server;
    }

}
