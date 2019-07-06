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

package org.apache.hc.core5.http.impl.nio;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Default {@link org.apache.hc.core5.http.nio.NHttpMessageParser} implementation for {@link HttpResponse}s.
 *
 * @since 4.1
 */
public class DefaultHttpResponseParser<T extends HttpResponse> extends AbstractMessageParser<T> {

    private final HttpResponseFactory<T> responseFactory;

    /**
     * Creates an instance of DefaultHttpResponseParser.
     *
     * @param responseFactory the response factory.
     * @param parser the line parser. If {@code null}
     *   {@link org.apache.hc.core5.http.message.LazyLineParser#INSTANCE} will be used.
     * @param http1Config Message http1Config. If {@code null}
     *   {@link Http1Config#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public DefaultHttpResponseParser(
            final HttpResponseFactory<T> responseFactory,
            final LineParser parser,
            final Http1Config http1Config) {
        super(parser, http1Config);
        this.responseFactory = Args.notNull(responseFactory, "Response factory");
    }

    /**
     * @since 4.3
     */
    public DefaultHttpResponseParser(final HttpResponseFactory<T> responseFactory, final Http1Config http1Config) {
        this(responseFactory, null, http1Config);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpResponseParser(final HttpResponseFactory<T> responseFactory) {
        this(responseFactory, null);
    }

    @Override
    protected T createMessage(final CharArrayBuffer buffer) throws HttpException {
        final StatusLine statusLine = getLineParser().parseStatusLine(buffer);
        final T response = this.responseFactory.newHttpResponse(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        response.setVersion(statusLine.getProtocolVersion());
        return response;
    }

}
