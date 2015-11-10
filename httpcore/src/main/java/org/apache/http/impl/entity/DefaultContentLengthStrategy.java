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

package org.apache.http.impl.entity;

import org.apache.http.Header;
import org.apache.http.HeaderElements;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpMessage;
import org.apache.http.NotImplementedException;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.Immutable;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.util.Args;

/**
 * The default implementation of the content length strategy. This class
 * will throw {@link ProtocolException} if it encounters an unsupported
 * transfer encoding, multiple {@code Content-Length} header
 * values or a malformed {@code Content-Length} header value.
 * <p>
 * This class recognizes "chunked" transfer-coding only.
 *
 * @since 5.0
 */
@Immutable
public class DefaultContentLengthStrategy implements ContentLengthStrategy {

    public static final DefaultContentLengthStrategy INSTANCE = new DefaultContentLengthStrategy();

    /**
     * Creates {@code DefaultContentLengthStrategy} instance. {@link ContentLengthStrategy#UNDEFINED}
     * is used per default when content length is not explicitly specified in the message.
     */
    public DefaultContentLengthStrategy() {
    }

    @Override
    public long determineLength(final HttpMessage message) throws HttpException {
        Args.notNull(message, "HTTP message");
        // Although Transfer-Encoding is specified as a list, in practice
        // it is either missing or has the single value "chunked". So we
        // treat it as a single-valued header here.
        final Header transferEncodingHeader = message.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        if (transferEncodingHeader != null) {
            final String s = transferEncodingHeader.getValue();
            if (HeaderElements.CHUNKED_ENCODING.equalsIgnoreCase(s)) {
                return CHUNKED;
            }
            throw new NotImplementedException("Unsupported transfer encoding: " + s);
        }
        if (message.containsHeaders(HttpHeaders.CONTENT_LENGTH) > 1) {
            throw new ProtocolException("Multiple Content-Length headers");
        }
        final Header contentLengthHeader = message.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            final String s = contentLengthHeader.getValue();
            try {
                final long len = Long.parseLong(s);
                if (len < 0) {
                    throw new ProtocolException("Negative content length: " + s);
                }
                return len;
            } catch (final NumberFormatException e) {
                throw new ProtocolException("Invalid content length: " + s);
            }
        }
        return UNDEFINED;
    }

    public static long determineLength(final ContentLengthStrategy strategy,
                                       final HttpMessage message,
                                       final HttpEntity entity)
            throws HttpException {
        final boolean hasTrailers = !entity.getExpectedTrailerNames().isEmpty();
        if (hasTrailers && !entity.isChunked()) {
            throw new ProtocolException("Request with trailers should have chunked encoding");
        }
        final long len = strategy.determineLength(message);
        if (hasTrailers && len != ContentLengthStrategy.CHUNKED) {
            throw new ProtocolException("Request with trailers should have chunked encoding");
        }
        return len;
    }
}
