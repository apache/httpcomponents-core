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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.StatusLine;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.config.MessageConstraints;
import org.apache.hc.core5.http.impl.DefaultHttpResponseFactory;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * HTTP response parser that obtain its input from an instance
 * of {@link org.apache.hc.core5.http.io.SessionInputBuffer}.
 *
 * @since 4.2
 */
@NotThreadSafe
public class DefaultHttpResponseParser extends AbstractMessageParser<HttpResponse> {

    private final HttpResponseFactory responseFactory;

    /**
     * Creates new instance of DefaultHttpResponseParser.
     *
     * @param lineParser the line parser. If {@code null}
     *   {@link org.apache.hc.core5.http.message.LazyLineParser#INSTANCE} will be used
     * @param responseFactory the response factory. If {@code null}
     *   {@link DefaultHttpResponseFactory#INSTANCE} will be used.
     * @param constraints the message constraints. If {@code null}
     *   {@link MessageConstraints#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public DefaultHttpResponseParser(
            final LineParser lineParser,
            final HttpResponseFactory responseFactory,
            final MessageConstraints constraints) {
        super(lineParser, constraints);
        this.responseFactory = responseFactory != null ? responseFactory : DefaultHttpResponseFactory.INSTANCE;
    }

    /**
     * @since 4.3
     */
    public DefaultHttpResponseParser(final MessageConstraints constraints) {
        this(null, null, constraints);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpResponseParser() {
        this(MessageConstraints.DEFAULT);
    }

    @Override
    protected IOException createConnectionClosedException() {
        return new NoHttpResponseException("The target server failed to respond");
    }

    @Override
    protected HttpResponse createMessage(final CharArrayBuffer buffer) throws IOException, HttpException {
        final StatusLine statusline = getLineParser().parseStatusLine(buffer);
        final ProtocolVersion version = statusline.getProtocolVersion();
        if (version.greaterEquals(HttpVersion.HTTP_2)) {
            throw new UnsupportedHttpVersionException("Unsupported version: " + version);
        }
        return this.responseFactory.newHttpResponse(statusline, null);
    }

}
