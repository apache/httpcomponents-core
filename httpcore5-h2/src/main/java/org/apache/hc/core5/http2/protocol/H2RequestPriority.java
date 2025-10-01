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
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.priority.PriorityFormatter;
import org.apache.hc.core5.http2.priority.PriorityValue;
import org.apache.hc.core5.util.Args;

/**
 * Emits RFC 9218 {@code Priority} request header for HTTP/2+.
 * <p>
 * The priority value is taken from the request context attribute
 * {@link #ATTR_HTTP2_PRIORITY_VALUE}. If the formatted value equals
 * RFC defaults (u=3, i=false) the header is omitted.
 * <p>
 * If {@code overwrite} is {@code false} (default), an existing {@code Priority}
 * header set by the caller is preserved.
 *
 * @since 5.4
 */
@Experimental
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class H2RequestPriority implements HttpRequestInterceptor {

    /**
     * Context attribute to carry a {@link PriorityValue}.
     */
    public static final String ATTR_HTTP2_PRIORITY_VALUE = "http2.priority.value";

    /**
     * Singleton with {@code overwrite=false}.
     */
    public static final H2RequestPriority INSTANCE = new H2RequestPriority(false);

    private final boolean overwrite;

    public H2RequestPriority() {
        this(false);
    }

    public H2RequestPriority(final boolean overwrite) {
        this.overwrite = overwrite;
    }

    @Override
    public void process(final HttpRequest request, final EntityDetails entity,
                        final HttpContext context) throws HttpException, IOException {

        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final ProtocolVersion ver = HttpCoreContext.cast(context).getProtocolVersion();
        if (ver == null || ver.compareToVersion(HttpVersion.HTTP_2) < 0) {
            return; // only for HTTP/2+
        }

        final Header existing = request.getFirstHeader(HttpHeaders.PRIORITY);
        if (existing != null && !overwrite) {
            return; // respect caller-set header
        }

        final PriorityValue pv = HttpCoreContext.cast(context)
                .getAttribute(ATTR_HTTP2_PRIORITY_VALUE, PriorityValue.class);
        if (pv == null) {
            return;
        }

        final String value = PriorityFormatter.format(pv);
        if (value == null) {
            // defaults (u=3, i=false) -> omit header
            if (overwrite && existing != null) {
                request.removeHeaders(HttpHeaders.PRIORITY);
            }
            return;
        }

        if (overwrite && existing != null) {
            request.removeHeaders(HttpHeaders.PRIORITY);
        }
        request.addHeader(HttpHeaders.PRIORITY, value);
    }
}
