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

package org.apache.hc.core5.testing.compatibility.nio;

import java.util.function.Consumer;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2AsyncRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.compatibility.TLSTestContexts;
import org.apache.hc.core5.testing.extension.nio.H2AsyncRequesterResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AsyncHttp1CompatTest extends AsyncHttpCompatTest<H2AsyncRequester> {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    @RegisterExtension
    private final H2AsyncRequesterResource clientResource;

    public AsyncHttp1CompatTest(final HttpHost target) {
        super(target);
        this.clientResource = new H2AsyncRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setTlsStrategy(new BasicClientTlsStrategy(TLSTestContexts.createClientSSLContext()))
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1));
    }

    void configure(final Consumer<H2RequesterBootstrap> customizer) {
        clientResource.configure(customizer);
    }

    @Override
    H2AsyncRequester client() {
        return clientResource.start();
    }

}
