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
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.reactor.DefaultListeningIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ListenerEndpoint;

public class AsyncServer extends IOReactorExecutor<DefaultListeningIOReactor> {

    public AsyncServer(final IOReactorConfig ioReactorConfig) {
        super(ioReactorConfig, null);
    }

    @Override
    DefaultListeningIOReactor createIOReactor(
            final IOEventHandlerFactory ioEventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final ThreadFactory threadFactory,
            final Callback<IOSession> sessionShutdownCallback) throws IOException {
        return new DefaultListeningIOReactor(
                ioEventHandlerFactory,
                ioReactorConfig,
                threadFactory,
                threadFactory,
                LoggingIOSessionDecorator.INSTANCE,
                LoggingExceptionCallback.INSTANCE,
                LoggingIOSessionListener.INSTANCE,
                sessionShutdownCallback);
    }

    public Future<ListenerEndpoint> listen(final InetSocketAddress address) {
        return reactor().listen(address, null);
    }

    public Set<ListenerEndpoint> getEndpoints() {
        return reactor().getEndpoints();
    }

}
