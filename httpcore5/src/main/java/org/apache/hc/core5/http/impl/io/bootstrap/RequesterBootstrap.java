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
package org.apache.hc.core5.http.impl.io.bootstrap;

import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.protocol.HttpProcessor;

/**
 * @since 5.0
 */
public class RequesterBootstrap {

    private HttpRequestExecutor requestExecutor;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy connStrategy;

    private RequesterBootstrap() {
    }

    public static RequesterBootstrap bootstrap() {
        return new RequesterBootstrap();
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final RequesterBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     */
    public final RequesterBootstrap setConnectionReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        this.connStrategy = connStrategy;
        return this;
    }

    /**
     * Assigns {@link HttpRequestExecutor} instance.
     */
    public final RequesterBootstrap setHttpRequestExecutor(final HttpRequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
        return this;
    }

    public HttpRequester create() {
        return new HttpRequester(
                requestExecutor != null ? requestExecutor : new HttpRequestExecutor(),
                httpProcessor != null ? httpProcessor : HttpProcessors.client(),
                connStrategy != null ? connStrategy : DefaultConnectionReuseStrategy.INSTANCE);
    }

}
