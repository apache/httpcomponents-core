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

package org.apache.hc.core5.http.integration;

import java.io.IOException;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.nio.entity.NStringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

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
        final HttpEntity incomingEntity = request.getEntity();
        if (incomingEntity != null) {
            content = EntityUtils.toString(incomingEntity);
        } else {
            final StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < count; i++) {
                buffer.append(pattern);
            }
            content = buffer.toString();
        }
        final NStringEntity outgoingEntity = new NStringEntity(content, ContentType.DEFAULT_TEXT);
        outgoingEntity.setChunked(this.chunking);
        response.setEntity(outgoingEntity);
    }

}
