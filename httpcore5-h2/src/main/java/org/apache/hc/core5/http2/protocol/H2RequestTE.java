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
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.RequestTE;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Tokenizer;

/**
 * HTTP request interceptor responsible for validating the {@code TE} header in HTTP/2 requests.
 * <p>
 * The {@code TE} header in HTTP/2 is restricted to containing only the {@code trailers} directive.
 * This interceptor ensures compliance by validating that the {@code TE} header does not include
 * any other directives or transfer codings. If any value other than {@code trailers} is present,
 * a {@link ProtocolException} is thrown.
 * <p>
 * For HTTP/1.x requests, this interceptor falls back to the behavior of {@link RequestTE},
 * where other transfer codings may be allowed.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class H2RequestTE extends RequestTE {

    /**
     * Singleton instance of the {@code H2RequestTE} interceptor.
     */
    public static final HttpRequestInterceptor INSTANCE = new H2RequestTE();

    /**
     * Processes the {@code TE} header for HTTP/2 compliance.
     * <p>
     * If the protocol version is HTTP/2, this method checks if the {@code TE} header contains
     * only the {@code trailers} directive. If any other value is found, it throws a {@link ProtocolException}.
     * For HTTP/1.x requests, it delegates processing to the parent {@link RequestTE} class.
     *
     * @param request the HTTP request to validate
     * @param entity  the entity associated with the request (may be {@code null})
     * @param context the execution context for the request
     * @throws HttpException if the {@code TE} header contains invalid values for HTTP/2
     * @throws IOException   in case of an I/O error
     */
    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {

        Args.notNull(context, "HTTP context");
        final ProtocolVersion ver = context.getProtocolVersion();

        // If the protocol version is HTTP/2
        if (ver.getMajor() >= 2) {
            // Check if TE header is present
            final Header teHeader = request.getFirstHeader(HttpHeaders.TE);
            if (teHeader != null) {
                final String teValue = teHeader.getValue();
                validateTEHeaderForHttp2(teValue);
            }
        } else {
            // For HTTP/1.x, fall back to the parent TE logic
            super.process(request, entity, context);
        }
    }

    /**
     * Validates that the {@code TE} header for HTTP/2 contains only the {@code trailers} directive.
     * <p>
     * This method parses the {@code TE} header and ensures that only the {@code trailers} directive is present.
     * If any other value is found, a {@link ProtocolException} is thrown.
     *
     * @param teValue the value of the {@code TE} header to validate
     * @throws HttpException if the {@code TE} header contains invalid values for HTTP/2
     */
    private void validateTEHeaderForHttp2(final String teValue) throws HttpException {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, teValue.length());

        while (!cursor.atEnd()) {
            final String member = Tokenizer.INSTANCE.parseToken(teValue, cursor, DELIMITER).trim();

            // Only 'trailers' is allowed in HTTP/2
            if (!"trailers".equalsIgnoreCase(member)) {
                throw new ProtocolException("In HTTP/2, the TE header must only contain 'trailers'. Found: " + member);
            }

            // Skip any whitespace and delimiter before moving to the next value
            if (!cursor.atEnd()) {
                Tokenizer.INSTANCE.skipWhiteSpace(teValue, cursor);
                cursor.updatePos(cursor.getPos() + 1);
            }
        }
    }
}

