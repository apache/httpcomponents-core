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
package org.apache.hc.core5.http.nio.support;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Factory for {@link AsyncServerExchangeHandler} instances that make use
 * of {@link HttpRequestMapper} to dispatch
 * the request to a particular {@link AsyncServerExchangeHandler} for processing.
 *
 * @since 5.0
 */
public final class DefaultAsyncResponseExchangeHandlerFactory implements HandlerFactory<AsyncServerExchangeHandler> {

    private final HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> mapper;
    private final Decorator<AsyncServerExchangeHandler> decorator;

    public DefaultAsyncResponseExchangeHandlerFactory(
            final HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> mapper,
            final Decorator<AsyncServerExchangeHandler> decorator) {
        this.mapper = Args.notNull(mapper, "Request handler mapper");
        this.decorator = decorator;
    }

    public DefaultAsyncResponseExchangeHandlerFactory(final HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> mapper) {
        this(mapper, null);
    }

    private AsyncServerExchangeHandler createHandler(final HttpRequest request,
                    final HttpContext context) throws HttpException {
        try {
            final Supplier<AsyncServerExchangeHandler> supplier = mapper.resolve(request, context);
            return supplier != null
                            ? supplier.get()
                            : new ImmediateResponseExchangeHandler(HttpStatus.SC_NOT_FOUND, "Resource not found");
        } catch (final MisdirectedRequestException ex) {
            return new ImmediateResponseExchangeHandler(HttpStatus.SC_MISDIRECTED_REQUEST,
                            "Not authoritative");
        }
    }

    @Override
    public AsyncServerExchangeHandler create(final HttpRequest request, final HttpContext context) throws HttpException {
        final AsyncServerExchangeHandler handler = createHandler(request, context);
        if (handler != null) {
            return decorator != null ? decorator.decorate(handler) : handler;
        }
        return null;
    }

}
