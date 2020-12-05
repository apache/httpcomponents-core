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
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * An element in a request processing chain.
 *
 * @since 5.0
 */
public final class HttpServerFilterChainElement {

    private final HttpFilterHandler handler;
    private final HttpServerFilterChainElement next;
    private final HttpFilterChain filterChain;

    public HttpServerFilterChainElement(final HttpFilterHandler handler, final HttpServerFilterChainElement next) {
        this.handler = handler;
        this.next = next;
        this.filterChain = next != null ? next::handle : null;
    }

    public void handle(
            final ClassicHttpRequest request,
            final HttpFilterChain.ResponseTrigger responseTrigger,
            final HttpContext context) throws IOException, HttpException {
        handler.handle(request, responseTrigger, context, filterChain);
    }

    @Override
    public String toString() {
        return "{" +
                "handler=" + handler.getClass() +
                ", next=" + (next != null ? next.handler.getClass() : "null") +
                '}';
    }

}