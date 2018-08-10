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
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * {@link HttpServerRequestHandler} implementation that delegates request processing
 * to a {@link HttpServerFilterChainElement}.
 *
 * @since 5.0
 */
public class HttpServerFilterChainRequestHandler implements HttpServerRequestHandler {

    private final HttpServerFilterChainElement filterChain;

    public HttpServerFilterChainRequestHandler(final HttpServerFilterChainElement filterChain) {
        this.filterChain = Args.notNull(filterChain, "Filter chain");
    }

    @Override
    public void handle(
            final ClassicHttpRequest request,
            final ResponseTrigger trigger,
            final HttpContext context) throws HttpException, IOException {
        filterChain.handle(request, new HttpFilterChain.ResponseTrigger() {

            @Override
            public void sendInformation(final ClassicHttpResponse response) throws HttpException, IOException {
                trigger.sendInformation(response);
            }

            @Override
            public void submitResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                trigger.submitResponse(response);

            }

        }, context);
    }

}
