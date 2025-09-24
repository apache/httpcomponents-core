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

package org.apache.hc.core5.testing.extension.nio;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.testing.nio.LoggingExceptionCallback;
import org.apache.hc.core5.testing.nio.LoggingHttp1StreamListener;
import org.apache.hc.core5.testing.nio.LoggingIOSessionDecorator;
import org.apache.hc.core5.testing.nio.LoggingIOSessionListener;
import org.apache.hc.core5.testing.nio.LoggingReactorMetricsListener;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpAsyncServerResource implements AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAsyncServerResource.class);

    private final AsyncServerBootstrap bootstrap;

    private HttpAsyncServer server;

    public HttpAsyncServerResource() {
        this.bootstrap = AsyncServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                .setIOSessionDecorator(LoggingIOSessionDecorator.INSTANCE)
                .setExceptionCallback(LoggingExceptionCallback.INSTANCE)
                .setIOSessionListener(LoggingIOSessionListener.INSTANCE)
                .setIOReactorMetricsListener(LoggingReactorMetricsListener.INSTANCE);
    }

    public void configure(final Consumer<AsyncServerBootstrap> customizer) {
        customizer.accept(bootstrap);
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Shutting down test server");
        if (server != null) {
            try {
                server.close(CloseMode.GRACEFUL);
            } catch (final Exception ignore) {
            }
        }
    }

    public HttpAsyncServer start() throws IOException {
        if (server == null) {
            LOG.debug("Starting up test server");
            server = bootstrap.create();
            server.start();
        }
        return server;
    }

}
