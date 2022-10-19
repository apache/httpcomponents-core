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

import java.io.IOException;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.extension.H2AsyncRequesterResource;
import org.apache.hc.core5.testing.nio.extension.H2AsyncServerResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class H2CoreTransportTest extends HttpCoreTransportTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final H2AsyncServerResource serverResource;
    @RegisterExtension
    private final H2AsyncRequesterResource clientResource;

    public H2CoreTransportTest(final URIScheme scheme) {
        super(scheme);
        this.serverResource = new H2AsyncServerResource(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setIOReactorConfig(
                        IOReactorConfig.custom()
                                .setSoTimeout(TIMEOUT)
                                .build())
                .setLookupRegistry(new UriPatternMatcher<>())
                .register("*", () -> new EchoHandler(2048))
        );
        this.clientResource = new H2AsyncRequesterResource(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    @Override
    HttpAsyncServer serverStart() throws IOException {
        return serverResource.start();
    }

    @Override
    HttpAsyncRequester clientStart() {
        return clientResource.start();
    }

}
