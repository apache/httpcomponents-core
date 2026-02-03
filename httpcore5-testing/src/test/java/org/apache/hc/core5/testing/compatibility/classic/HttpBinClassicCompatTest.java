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

package org.apache.hc.core5.testing.compatibility.classic;


import org.junit.jupiter.api.Assertions;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.testing.extension.classic.HttpRequesterResource;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class HttpBinClassicCompatTest {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpHost target;
    @RegisterExtension
    private final HttpRequesterResource clientResource;

    public HttpBinClassicCompatTest(final HttpHost target) {
        this.target = target;
        this.clientResource = new HttpRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    void configure(final Consumer<RequesterBootstrap> customizer) {
        clientResource.configure(customizer);
    }

    HttpRequester client() {
        return clientResource.start();
    }

    @Test
    void test_sequential_request_execution() throws Exception {
        final HttpRequester client = client();
        final List<ClassicHttpRequest> requestMessages = Arrays.asList(
                ClassicRequestBuilder.get("/headers")
                        .setHttpHost(target)
                        .build(),
                ClassicRequestBuilder.post("/anything")
                        .setHttpHost(target)
                        .setEntity(new StringEntity("some important message", ContentType.TEXT_PLAIN))
                        .build(),
                ClassicRequestBuilder.put("/anything")
                        .setHttpHost(target)
                        .setEntity(new StringEntity("some important message", ContentType.TEXT_PLAIN))
                        .build(),
                ClassicRequestBuilder.get("/drip")
                        .setHttpHost(target)
                        .build(),
                ClassicRequestBuilder.get("/bytes/20000")
                        .setHttpHost(target)
                        .build(),
                ClassicRequestBuilder.get("/delay/2")
                        .setHttpHost(target)
                        .build(),
                ClassicRequestBuilder.post("/delay/2")
                        .setHttpHost(target)
                        .setEntity(new StringEntity("some important message", ContentType.TEXT_PLAIN))
                        .build(),
                ClassicRequestBuilder.put("/delay/2")
                        .setHttpHost(target)
                        .setEntity(new StringEntity("some important message", ContentType.TEXT_PLAIN))
                        .build()
        );

        for (final ClassicHttpRequest request : requestMessages) {
            final HttpCoreContext context = HttpCoreContext.create();
            client.execute(target, request, TIMEOUT, context, response -> {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                return null;
            });
        }
    }

}
