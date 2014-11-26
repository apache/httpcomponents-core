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
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpMessage;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.Immutable;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.util.Args;

/**
 * The lax implementation of the content length strategy. This class will ignore
 * unrecognized transfer encodings and malformed {@code Content-Length}
 * header values.
 * <p>
 * This class recognizes "chunked" transfer-coding only.
 *
 * @since 4.0
 */
@Immutable
public class LaxContentLengthStrategy implements ContentLengthStrategy {

    public static final LaxContentLengthStrategy INSTANCE = new LaxContentLengthStrategy();

    /**
     * Creates {@code LaxContentLengthStrategy} instance. {@link ContentLengthStrategy#UNDEFINED}
     * is used per default when content length is not explicitly specified in the message.
     */
    public LaxContentLengthStrategy() {
    }

    @Override
    public long determineLength(final HttpMessage message) throws HttpException {
        Args.notNull(message, "HTTP message");

        final Header transferEncodingHeader = message.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        // Although Transfer-Encoding is specified as a list, in practice
        // it is either missing or has the single value "chunked". So we
        // treat it as a single-valued header here.
        if (transferEncodingHeader != null) {
            final String s = transferEncodingHeader.getValue();
            if (HeaderElements.CHUNKED_ENCODING.equalsIgnoreCase(s)) {
                return CHUNKED;
            } else if (HeaderElements.IDENTITY_ENCODING.equalsIgnoreCase(s)) {
                return IDENTITY;
            } else {
                throw new ProtocolException("Unsupported transfer encoding: " + s);
            }
        }
        if (message.containsHeader(HttpHeaders.CONTENT_LENGTH)) {
            long contentlen = -1;
            final Header[] headers = message.getHeaders(HttpHeaders.CONTENT_LENGTH);
            for (int i = headers.length - 1; i >= 0; i--) {
                final Header header = headers[i];
                try {
                    contentlen = Long.parseLong(header.getValue());
                    break;
                } catch (final NumberFormatException ignore) {
                }
                // See if we can have better luck with another header, if present
            }
            if (contentlen >= 0) {
                return contentlen;
            } else {
                throw new ProtocolException("Invalid content length");
            }
        }
        return UNDEFINED;
    }

}
