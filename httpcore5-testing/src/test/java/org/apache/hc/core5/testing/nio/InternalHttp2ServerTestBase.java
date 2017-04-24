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

import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.ProtocolScheme;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

public abstract class InternalHttp2ServerTestBase {

    protected final ProtocolScheme scheme;

    public InternalHttp2ServerTestBase(final ProtocolScheme scheme) {
        this.scheme = scheme;
    }

    public InternalHttp2ServerTestBase() {
        this(ProtocolScheme.HTTP);
    }

    protected Http2TestServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            server = new Http2TestServer(IOReactorConfig.DEFAULT,
                    scheme == ProtocolScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null);
        }

        @Override
        protected void after() {
            if (server != null) {
                try {
                    server.shutdown(TimeValue.ofSeconds(5));
                    server = null;
                } catch (final Exception ignore) {
                }
            }
        }

    };

}
