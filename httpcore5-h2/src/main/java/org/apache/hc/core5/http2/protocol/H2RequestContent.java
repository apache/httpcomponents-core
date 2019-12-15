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

package org.apache.hc.core5.http2.protocol;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.util.Args;

/**
 * HTTP/2 compatible extension of {@link RequestContent}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class H2RequestContent extends RequestContent {

    public H2RequestContent() {
        super();
    }

    public H2RequestContent(final boolean overwrite) {
        super(overwrite);
    }

    @Override
    public void process(
            final HttpRequest request,
            final EntityDetails entity,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(context, "HTTP context");
        final ProtocolVersion ver = context.getProtocolVersion();
        if (ver.getMajor() < 2) {
            super.process(request, entity, context);
        } else if (entity != null) {
            final String method = request.getMethod();
            if (Method.TRACE.isSame(method)) {
                throw new ProtocolException("TRACE request may not enclose an entity");
            }
            MessageSupport.addContentTypeHeader(request, entity);
            MessageSupport.addContentEncodingHeader(request, entity);
            MessageSupport.addTrailerHeader(request, entity);
        }
    }
}
