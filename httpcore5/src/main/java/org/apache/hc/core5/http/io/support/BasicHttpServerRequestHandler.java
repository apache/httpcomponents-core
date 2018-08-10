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

package org.apache.hc.core5.http.io.support;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Basic {@link HttpServerRequestHandler} implementation that makes use of
 * {@link HttpRequestMapper} to dispatch the request to a particular
 * {@link HttpRequestHandler} for processing.
 *
 * @since 5.0
 */
public class BasicHttpServerRequestHandler implements HttpServerRequestHandler {

    private final HttpRequestMapper<HttpRequestHandler> handlerMapper;
    private final HttpResponseFactory<ClassicHttpResponse> responseFactory;

    public BasicHttpServerRequestHandler(
            final HttpRequestMapper<HttpRequestHandler> handlerMapper,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        this.handlerMapper = Args.notNull(handlerMapper, "Handler mapper");
        this.responseFactory = responseFactory != null ? responseFactory : DefaultClassicHttpResponseFactory.INSTANCE;
    }

    public BasicHttpServerRequestHandler(final HttpRequestMapper<HttpRequestHandler> handlerMapper) {
        this(handlerMapper, null);
    }

    @Override
    public void handle(
            final ClassicHttpRequest request,
            final ResponseTrigger responseTrigger,
            final HttpContext context) throws HttpException, IOException {
        final ClassicHttpResponse response = responseFactory.newHttpResponse(HttpStatus.SC_OK);
        final HttpRequestHandler handler = handlerMapper != null ? handlerMapper.resolve(request, context) : null;
        if (handler != null) {
            handler.handle(request, response, context);
        } else {
            response.setCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
        responseTrigger.submitResponse(response);
    }

}
