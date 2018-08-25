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
package org.apache.hc.core5.http2.nio.support;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Factory for {@link AsyncPushConsumer} instances that make use
 * of {@link HttpRequestMapper} to dispatch
 * the request to a particular {@link AsyncPushConsumer} for processing.
 *
 * @since 5.0
 */
public final class DefaultAsyncPushConsumerFactory implements HandlerFactory<AsyncPushConsumer> {

    private final HttpRequestMapper<Supplier<AsyncPushConsumer>> mapper;

    public DefaultAsyncPushConsumerFactory(final HttpRequestMapper<Supplier<AsyncPushConsumer>> mapper) {
        this.mapper = Args.notNull(mapper, "Request handler mapper");
    }

    @Override
    public AsyncPushConsumer create(final HttpRequest request, final HttpContext context) throws HttpException {
        try {
            final Supplier<AsyncPushConsumer> supplier = mapper.resolve(request, context);
            return supplier != null ? supplier.get() : null;
        } catch (final MisdirectedRequestException ex) {
            return null;
        }
    }
}
