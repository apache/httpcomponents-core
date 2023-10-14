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

package org.apache.hc.core5.http.impl;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

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
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DefaultContentLengthStrategy implements ContentLengthStrategy {

    public static final DefaultContentLengthStrategy INSTANCE = new DefaultContentLengthStrategy();

    /**
     * Creates {@code DefaultContentLengthStrategy} instance. {@link ContentLengthStrategy#UNDEFINED}
     * is used per default when content length is not explicitly specified in the message.
     */
    public DefaultContentLengthStrategy() {
    }

    enum Coding { UNKNOWN, CHUNK }

    @Override
    public long determineLength(final HttpMessage message) throws HttpException {
        Args.notNull(message, "HTTP message");
        final Header teh = message.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        if (teh != null) {
            final AtomicReference<Coding> codingRef = new AtomicReference<>();
            MessageSupport.parseTokens(message, HttpHeaders.TRANSFER_ENCODING, e -> {
                if (!TextUtils.isBlank(e)) {
                    if (e.equalsIgnoreCase(HeaderElements.CHUNKED_ENCODING)) {
                        if (!codingRef.compareAndSet(null, Coding.CHUNK)) {
                            codingRef.set(Coding.UNKNOWN);
                        }
                    } else {
                        codingRef.set(Coding.UNKNOWN);
                    }
                }
            });
            if (codingRef.get() == Coding.CHUNK) {
                return CHUNKED;
            }
            throw new NotImplementedException("Unsupported transfer encoding: " + teh.getValue());
        }
        if (message.countHeaders(HttpHeaders.CONTENT_LENGTH) > 1) {
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

}
