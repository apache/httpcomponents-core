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

package org.apache.hc.core5.http.protocol;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;


/**
 * HTTP protocol interceptor responsible for validating and processing the {@link HttpHeaders#TE} header field in HTTP/1.1 requests.
 * <p>
 * The {@link HttpHeaders#TE} header is used to indicate transfer codings the client is willing to accept and, in some cases, whether
 * the client is willing to accept trailer fields. This interceptor ensures that the {@link HttpHeaders#TE} header does not include
 * the {@code chunked} transfer coding and validates the presence of the {@code Connection: TE} header.
 * <p>
 * For HTTP/1.1 requests, the {@link HttpHeaders#TE} header can contain multiple values separated by commas and may include quality
 * values (denoted by {@code q=}) separated by semicolons.
 * <p>
 * In case of HTTP/2, this validation is skipped, and another layer of logic handles the specifics of HTTP/2 compliance.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class RequestTE implements HttpRequestInterceptor {

    /**
     * Singleton instance of the {@code RequestTE} interceptor.
     */
    public static final HttpRequestInterceptor INSTANCE = new RequestTE();

    /**
     * Default constructor.
     */
    public RequestTE() {
        super();
    }

    /**
     * Processes the {@code TE} header of the given HTTP request and ensures compliance with HTTP/1.1 requirements.
     * <p>
     * If the {@code TE} header is present, this method validates that:
     * <ul>
     * <li>The {@code TE} header does not include the {@code chunked} transfer coding, which is implicitly supported for HTTP/1.1.</li>
     * <li>The {@code Connection} header includes the {@code TE} directive, as required by the protocol.</li>
     * </ul>
     *
     * @param request the HTTP request containing the headers to validate
     * @param entity  the entity associated with the request (may be {@code null})
     * @param context the execution context for the request
     * @throws HttpException if the {@code TE} header contains invalid values or the {@code Connection} header is missing
     * @throws IOException   in case of an I/O error
     */
    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final AtomicBoolean hasTE = new AtomicBoolean(false);
        final AtomicBoolean hasChunk = new AtomicBoolean(false);
        MessageSupport.parseTokens(request, HttpHeaders.TE, token -> {
            hasTE.set(true);
            if (token.equalsIgnoreCase("chunked")) {
                hasChunk.set(true);
            }
        });
        if (hasChunk.get()) {
            throw new ProtocolException("'chunked' transfer coding must not be listed in the TE header for HTTP/1.1.");
        }
        if (hasTE.get()) {
            final AtomicBoolean hasConnection = new AtomicBoolean(false);
            final AtomicBoolean hasTEinConnection = new AtomicBoolean(false);
            MessageSupport.parseTokens(request, HttpHeaders.CONNECTION, token -> {
                hasConnection.set(true);
                if ("TE".equalsIgnoreCase(token)) {
                    hasTEinConnection.set(true);
                }
            });
            if (!hasTEinConnection.get()) {
                throw new ProtocolException("The 'Connection' header must include the 'TE' directive when the 'TE' header is present.");
            }
        }
    }

}