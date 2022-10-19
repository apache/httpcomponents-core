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

import java.util.function.Consumer;

import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.ssl.BasicClientTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.classic.LoggingConnPoolListener;
import org.apache.hc.core5.testing.nio.LoggingHttp1StreamListener;
import org.apache.hc.core5.testing.nio.LoggingIOSessionDecorator;
import org.apache.hc.core5.testing.nio.LoggingIOSessionListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpAsyncRequesterResource implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAsyncRequesterResource.class);

    private final Consumer<AsyncRequesterBootstrap> bootstrapCustomizer;

    private HttpAsyncRequester requester;

    public HttpAsyncRequesterResource(final Consumer<AsyncRequesterBootstrap> bootstrapCustomizer) {
        this.bootstrapCustomizer = bootstrapCustomizer;
    }

    @Override
    public void beforeEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Starting up test client");
        final AsyncRequesterBootstrap bootstrap = AsyncRequesterBootstrap.bootstrap()
                .setTlsStrategy(new BasicClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setMaxTotal(2)
                .setDefaultMaxPerRoute(2)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE);
        bootstrapCustomizer.accept(bootstrap);
        requester = bootstrap.create();
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Shutting down test client");
        if (requester != null) {
            try {
                requester.close(CloseMode.GRACEFUL);
            } catch (final Exception ignore) {
            }
        }
    }

    public HttpAsyncRequester start() {
        Assertions.assertNotNull(requester);
        requester.start();
        return requester;
    }

}
