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
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;

/**
 * This request interceptor is responsible for delimiting the message content
 * by adding {@code Content-Length} or {@code Transfer-Content} headers based
 * on the properties of the enclosed entity and the protocol version.
 * <p>
 * This interceptor is essential for the HTTP protocol conformance and
 * the correct operation of the client-side message processing pipeline.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class RequestContent implements HttpRequestInterceptor {

    /**
     * Singleton instance.
     * @since 5.2
     */
    public static final HttpRequestInterceptor INSTANCE = new RequestContent();

    private final boolean overwrite;

    /**
     * Default constructor. The {@code Content-Length} or {@code Transfer-Encoding}
     * will cause the interceptor to throw {@link ProtocolException} if already present in the
     * response message.
     */
    public RequestContent() {
        this(false);
    }

    /**
     * Constructor that can be used to fine-tune behavior of this interceptor.
     *
     * @param overwrite If set to {@code true} the {@code Content-Length} and
     * {@code Transfer-Encoding} headers will be created or updated if already present.
     * If set to {@code false} the {@code Content-Length} and
     * {@code Transfer-Encoding} headers will cause the interceptor to throw
     * {@link ProtocolException} if already present in the response message.
     *
     * @since 4.2
     */
     public RequestContent(final boolean overwrite) {
         super();
         this.overwrite = overwrite;
    }

    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        final String method = request.getMethod();
        if (Method.TRACE.isSame(method) && entity != null) {
            throw new ProtocolException("TRACE request may not enclose an entity");
        }
        if (this.overwrite) {
            request.removeHeaders(HttpHeaders.TRANSFER_ENCODING);
            request.removeHeaders(HttpHeaders.CONTENT_LENGTH);
        } else {
            if (request.containsHeader(HttpHeaders.TRANSFER_ENCODING)) {
                throw new ProtocolException("Transfer-encoding header already present");
            }
            if (request.containsHeader(HttpHeaders.CONTENT_LENGTH)) {
                throw new ProtocolException("Content-Length header already present");
            }
        }
        if (entity == null && isContentEnclosingMethod(method)) {
            request.addHeader(HttpHeaders.CONTENT_LENGTH, "0");
            return;
        }
        if (entity != null) {

            // Check for OPTIONS request with content but no Content-Type header
            validateOptionsContentType(request);

            final ProtocolVersion ver = context.getProtocolVersion();
            // Must specify a transfer encoding or a content length
            if (entity.isChunked() || entity.getContentLength() < 0) {
                if (ver.lessEquals(HttpVersion.HTTP_1_0)) {
                    throw new ProtocolException(
                            "Chunked transfer encoding not allowed for " + ver);
                }
                request.addHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);
                MessageSupport.addTrailerHeader(request, entity);
            } else {
                request.addHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(entity.getContentLength()));
            }
            MessageSupport.addContentTypeHeader(request, entity);
            MessageSupport.addContentEncodingHeader(request, entity);
        }
    }
    private boolean isContentEnclosingMethod(final String method) {
        return (Method.POST.isSame(method)||Method.PUT.isSame(method)||Method.PATCH.isSame(method));
    }
    /**
     * Validates the presence of the Content-Type header for an OPTIONS request.
     *
     * <p>
     * According to the RFC specifications, an HTTP {@link Method#OPTIONS} request that contains content
     * must have a Content-Type header. This method checks for the presence of the Content-Type header
     * in such requests. It does not validate the actual value of the Content-Type header, as determining
     * its validity would require knowledge of all valid media types, which is beyond the scope of this method.
     * If the header is absent, a {@link ProtocolException} is thrown.
     * </p>
     *
     * <p>
     * Note: This method does not check the validity of the Content-Type header value, only its presence.
     * </p>
     *
     * @param request The {@link HttpRequest} to be validated for the presence of the Content-Type header. Must not be null.
     * @throws ProtocolException If the Content-Type header is missing in an OPTIONS request with content.
     */
    public void validateOptionsContentType(final HttpRequest request) throws ProtocolException {
        if (Method.OPTIONS.isSame(request.getMethod())) {
            final Header header = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
            if (header == null) {
                throw new ProtocolException("OPTIONS request must have Content-Type header");
            }
        }
    }
}
