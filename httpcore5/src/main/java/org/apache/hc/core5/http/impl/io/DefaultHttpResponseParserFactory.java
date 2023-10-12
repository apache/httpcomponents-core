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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.HttpMessageParser;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.message.LazyLaxLineParser;
import org.apache.hc.core5.http.message.LineParser;

/**
 * Default factory for response message parsers.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpResponseParserFactory implements HttpMessageParserFactory<ClassicHttpResponse> {

    public static final DefaultHttpResponseParserFactory INSTANCE = new DefaultHttpResponseParserFactory();

    private final Http1Config http1Config;
    private final LineParser lineParser;
    private final HttpResponseFactory<ClassicHttpResponse> responseFactory;

    /**
     * @since 5.3
     */
    public DefaultHttpResponseParserFactory(
            final Http1Config http1Config,
            final LineParser lineParser,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        super();
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.lineParser = lineParser != null ? lineParser : LazyLaxLineParser.INSTANCE;
        this.responseFactory = responseFactory != null ? responseFactory : DefaultClassicHttpResponseFactory.INSTANCE;
    }

    public DefaultHttpResponseParserFactory(final LineParser lineParser,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        this(null, lineParser, responseFactory);
    }

    /**
     * @since 5.3
     */
    public DefaultHttpResponseParserFactory(final Http1Config http1Config) {
        this(http1Config, null, null);
    }

    public DefaultHttpResponseParserFactory() {
        this(null, null);
    }

    /**
     * @deprecated Use {@link #create()}
     */
    @Deprecated
    @Override
    public HttpMessageParser<ClassicHttpResponse> create(final Http1Config http1Config) {
        return new DefaultHttpResponseParser(http1Config, this.lineParser, this.responseFactory);
    }

    @Override
    public HttpMessageParser<ClassicHttpResponse> create() {
        return new DefaultHttpResponseParser(this.http1Config, this.lineParser, this.responseFactory);
    }

}
