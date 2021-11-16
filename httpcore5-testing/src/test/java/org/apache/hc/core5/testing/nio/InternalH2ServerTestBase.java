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

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith({InternalH2ServerTestBase.serverResource.class})
public abstract class InternalH2ServerTestBase {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final URIScheme scheme;

    public InternalH2ServerTestBase(final URIScheme scheme) {
        this.scheme = scheme;
    }

    public InternalH2ServerTestBase() {
        this(URIScheme.HTTP);
    }

    protected H2TestServer server;

    class serverResource implements AfterEachCallback, BeforeEachCallback {

        @Override
        public void beforeEach(final ExtensionContext context) throws Exception {
            log.debug("Starting up test server");
            server = new H2TestServer(IOReactorConfig.DEFAULT,
                    scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null);
        }

        @Override
        public void afterEach(final ExtensionContext context) throws Exception {
            log.debug("Shutting down test server");
            if (server != null) {
                server.shutdown(TimeValue.ofSeconds(5));
            }
        }
    }

}
