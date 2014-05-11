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

package org.apache.http.nio.integration;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

final class SimpleRequestHandler implements HttpRequestHandler {

    private final boolean chunking;

    SimpleRequestHandler() {
        this(false);
    }

    SimpleRequestHandler(final boolean chunking) {
        super();
        this.chunking = chunking;
    }

    @Override
    public void handle(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {

        final String s = request.getRequestLine().getUri();
        final int idx = s.indexOf('x');
        if (idx == -1) {
            throw new HttpException("Unexpected request-URI format");
        }
        final String pattern = s.substring(0, idx);
        final int count = Integer.parseInt(s.substring(idx + 1, s.length()));
        response.addHeader("Pattern", pattern);

        final String content;
        if (request instanceof HttpEntityEnclosingRequest) {
            final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity != null) {
                content = EntityUtils.toString(entity);
            } else {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                content = "Request entity not available";
            }
        } else {
            final StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < count; i++) {
                buffer.append(pattern);
            }
            content = buffer.toString();
        }
        final NStringEntity entity = new NStringEntity(content, ContentType.DEFAULT_TEXT);
        entity.setChunked(this.chunking);
        response.setEntity(entity);
    }

}
