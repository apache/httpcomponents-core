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

package org.apache.hc.core5.http2.impl.nio.bootstrap;

import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.pool.ControlledConnPool;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;

/**
 * @since 5.0
 */
public class Http2AsyncRequester extends HttpAsyncRequester {

    private final HttpVersionPolicy versionPolicy;

    public Http2AsyncRequester(
            final HttpVersionPolicy versionPolicy,
            final IOReactorConfig ioReactorConfig,
            final IOEventHandlerFactory eventHandlerFactory,
            final ControlledConnPool<HttpHost, IOSession> connPool,
            final TlsStrategy tlsStrategy,
            final ExceptionListener exceptionListener) throws IOReactorException {
        super(ioReactorConfig, eventHandlerFactory, connPool, tlsStrategy, exceptionListener);
        this.versionPolicy = versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE;
    }

    @Override
    protected Future<AsyncClientEndpoint> doConnect(
            final HttpHost host,
            final TimeValue timeout,
            final Object attachment,
            final FutureCallback<AsyncClientEndpoint> callback) {
        return super.doConnect(host, timeout, attachment != null ? attachment : versionPolicy, callback);
    }

}
