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
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;

/**
 * ResponseContent is the most important interceptor for outgoing responses.
 * It is responsible for delimiting content length by adding
 * {@code Content-Length} or {@code Transfer-Content} headers based
 * on the properties of the enclosed entity and the protocol version.
 * This interceptor is required for correct functioning of server side protocol
 * processors.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class ResponseContent implements HttpResponseInterceptor {

    private final boolean overwrite;

    /**
     * Default constructor. The {@code Content-Length} or {@code Transfer-Encoding}
     * will cause the interceptor to throw {@link ProtocolException} if already present in the
     * response message.
     */
    public ResponseContent() {
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
     public ResponseContent(final boolean overwrite) {
         super();
         this.overwrite = overwrite;
    }

    /**
     * Processes the response (possibly updating or inserting) Content-Length and Transfer-Encoding headers.
     * @param response The HttpResponse to modify.
     * @param context Unused.
     * @throws ProtocolException If either the Content-Length or Transfer-Encoding headers are found.
     * @throws IllegalArgumentException If the response is null.
     */
    @Override
    public void process(final HttpResponse response, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(response, "HTTP response");
        if (this.overwrite) {
            response.removeHeaders(HttpHeaders.TRANSFER_ENCODING);
            response.removeHeaders(HttpHeaders.CONTENT_LENGTH);
        } else {
            if (response.containsHeader(HttpHeaders.TRANSFER_ENCODING)) {
                throw new ProtocolException("Transfer-encoding header already present");
            }
            if (response.containsHeader(HttpHeaders.CONTENT_LENGTH)) {
                throw new ProtocolException("Content-Length header already present");
            }
        }
        final ProtocolVersion ver = context.getProtocolVersion();
        if (entity != null) {
            final long len = entity.getContentLength();
            if (len >= 0 && !entity.isChunked()) {
                response.addHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(entity.getContentLength()));
            } else if (ver.greaterEquals(HttpVersion.HTTP_1_1)) {
                response.addHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);
                MessageSupport.addTrailerHeader(response, entity);
            }
            MessageSupport.addContentTypeHeader(response, entity);
            MessageSupport.addContentEncodingHeader(response, entity);
        } else {
            final int status = response.getCode();
            if (status != HttpStatus.SC_NO_CONTENT && status != HttpStatus.SC_NOT_MODIFIED) {
                response.addHeader(HttpHeaders.CONTENT_LENGTH, "0");
            }
        }
    }

}
