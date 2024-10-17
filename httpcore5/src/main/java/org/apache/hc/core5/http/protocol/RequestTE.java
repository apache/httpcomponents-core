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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Tokenizer;

/**
 * HTTP request interceptor responsible for validating and processing the {@code TE} header field in HTTP/1.1 requests.
 * <p>
 * The {@code TE} header is used to indicate transfer codings the client is willing to accept and, in some cases, whether
 * the client is willing to accept trailer fields. This interceptor ensures that the {@code TE} header does not include
 * the {@code chunked} transfer coding and validates the presence of the {@code Connection: TE} header.
 * <p>
 * For HTTP/1.1 requests, the {@code TE} header can contain multiple values separated by commas and may include quality
 * values (denoted by {@code q=}) separated by semicolons.
 * <p>
 * In case of HTTP/2, this validation is skipped, and another layer of logic handles the specifics of HTTP/2 compliance.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class RequestTE implements HttpRequestInterceptor {

    /**
     * Singleton instance of the {@code RequestTE} interceptor.
     */
    public static final HttpRequestInterceptor INSTANCE = new RequestTE();

    /**
     * Delimiter used to parse the {@code TE} header, recognizing both commas (',') and semicolons (';') as delimiters.
     */
    public static final Tokenizer.Delimiter DELIMITER = Tokenizer.delimiters(',', ';');

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

        // Fetch the TE header
        final Header teHeader = request.getFirstHeader(HttpHeaders.TE);

        if (teHeader == null) {
            return;  // No further validation needed
        }

        final String teValue = teHeader.getValue();
        validateTEField(teValue);

        validateConnectionHeader(request);
    }

    /**
     * Validates the {@code TE} header values for compliance with HTTP/1.1.
     * <p>
     * Specifically, this method ensures that:
     * <ul>
     * <li>The {@code TE} header does not contain the {@code chunked} transfer coding.</li>
     * <li>The {@code trailers} directive is allowed and treated as valid.</li>
     * </ul>
     *
     * @param teValue the value of the {@code TE} header
     * @throws HttpException if the {@code TE} header contains invalid values
     */
    private void validateTEField(final String teValue) throws HttpException {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, teValue.length());

        while (!cursor.atEnd()) {
            Tokenizer.INSTANCE.skipWhiteSpace(teValue, cursor);

            final String member = Tokenizer.INSTANCE.parseToken(teValue, cursor, DELIMITER);

            if (member.isEmpty()) {
                if (!cursor.atEnd()) {
                    Tokenizer.INSTANCE.skipWhiteSpace(teValue, cursor);
                    cursor.updatePos(cursor.getPos() + 1);
                }
                continue;
            }

            if ("trailers".equalsIgnoreCase(member)) {
                continue;
            }

            if (HeaderElements.CHUNKED_ENCODING.equalsIgnoreCase(member)) {
                throw new ProtocolException("'chunked' transfer coding must not be listed in the TE header for HTTP/1.1.");
            }

            if (!cursor.atEnd()) {
                Tokenizer.INSTANCE.skipWhiteSpace(teValue, cursor);
                cursor.updatePos(cursor.getPos() + 1);
            }
        }
    }

    /**
     * Validates the presence of the {@code Connection: TE} header when the {@code TE} header is present.
     * <p>
     * If the {@code TE} header is used, the HTTP/1.1 protocol requires that the {@code Connection} header includes the {@code TE} directive to prevent forwarding by intermediaries.
     *
     * @param request the HTTP request to validate
     * @throws HttpException if the {@code Connection: TE} header is missing
     */
    private void validateConnectionHeader(final HttpRequest request) throws HttpException {
        final Header connectionHeader = request.getFirstHeader(HttpHeaders.CONNECTION);
        if (connectionHeader == null) {
            throw new ProtocolException("The 'TE' header is present, but the 'Connection' header is missing.");
        }
        final String connectionValue = connectionHeader.getValue();
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, connectionValue.length());

        boolean hasTE = false;

        while (!cursor.atEnd()) {
            final String directive = Tokenizer.INSTANCE.parseToken(connectionValue, cursor, DELIMITER).trim();

            if ("TE".equalsIgnoreCase(directive)) {
                hasTE = true;
                break;
            }

            if (!cursor.atEnd()) {
                cursor.updatePos(cursor.getPos() + 1);
            }
        }

        if (!hasTE) {
            throw new ProtocolException("The 'Connection' header must include the 'TE' directive when the 'TE' header is present.");
        }
    }
}
