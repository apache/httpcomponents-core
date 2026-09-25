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
package org.apache.hc.core5.http2.nio.pool;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Common abstraction for HTTP/2 connection pools that hand out
 * {@link H2StreamLease} reservations to the requester layer.
 *
 * @since 5.5
 */
@Internal
public interface H2RequesterConnPool extends ModalCloseable {

    Future<H2StreamLease> leaseSession(HttpHost endpoint, Timeout connectTimeout, FutureCallback<H2StreamLease> callback);

    void closeIdle(TimeValue idleTime);

    Set<HttpHost> getRoutes();

    void setValidateAfterInactivity(TimeValue timeValue);

    static H2RequesterConnPool create(
            final ConnectionInitiator connectionInitiator,
            final Resolver<HttpHost, InetSocketAddress> addressResolver,
            final TlsStrategy tlsStrategy,
            final H2PoolPolicy poolPolicy,
            final int defaultMaxPerRoute,
            final int maxTotal,
            final TimeValue validateAfterInactivity) {
        if (poolPolicy == H2PoolPolicy.MULTIPLEXING) {
            return new H2MultiplexingConnPool(
                    connectionInitiator,
                    addressResolver,
                    tlsStrategy,
                    defaultMaxPerRoute,
                    maxTotal,
                    validateAfterInactivity);
        }
        return new H2ConnPool(connectionInitiator, addressResolver, tlsStrategy, validateAfterInactivity);
    }
}