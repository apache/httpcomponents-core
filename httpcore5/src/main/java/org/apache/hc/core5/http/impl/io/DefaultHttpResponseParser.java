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

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * HTTP response parser that obtain its input from an instance
 * of {@link org.apache.hc.core5.http.io.SessionInputBuffer}.
 *
 * @since 4.2
 */
public class DefaultHttpResponseParser extends AbstractMessageParser<ClassicHttpResponse> {

    private final HttpResponseFactory<ClassicHttpResponse> responseFactory;

    /**
     * @since 5.3
     */
    public DefaultHttpResponseParser(
            final Http1Config http1Config,
            final LineParser lineParser,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        super(http1Config, lineParser);
        this.responseFactory = responseFactory != null ? responseFactory : DefaultClassicHttpResponseFactory.INSTANCE;
    }

    /**
     * @deprecated Use {@link #DefaultHttpResponseParser(Http1Config, LineParser, HttpResponseFactory)}
     */
    @Deprecated
    public DefaultHttpResponseParser(
            final LineParser lineParser,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory,
            final Http1Config http1Config) {
        this(http1Config, lineParser, responseFactory);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpResponseParser(final Http1Config http1Config) {
        this(http1Config, null, null);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpResponseParser() {
        this(Http1Config.DEFAULT);
    }

    @Override
    protected ClassicHttpResponse createMessage(final CharArrayBuffer buffer) throws IOException, HttpException {
        final StatusLine statusline = getLineParser().parseStatusLine(buffer);
        final ClassicHttpResponse response = this.responseFactory.newHttpResponse(statusline.getStatusCode(), statusline.getReasonPhrase());
        response.setVersion(statusline.getProtocolVersion());
        return response;
    }

}
