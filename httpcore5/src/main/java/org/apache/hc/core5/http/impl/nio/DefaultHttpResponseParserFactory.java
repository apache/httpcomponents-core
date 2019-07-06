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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.LazyLaxLineParser;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;

/**
 * Default factory for response message parsers.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpResponseParserFactory implements NHttpMessageParserFactory<HttpResponse> {

    public static final DefaultHttpResponseParserFactory INSTANCE = new DefaultHttpResponseParserFactory();

    private final Http1Config http1Config;
    private final HttpResponseFactory<HttpResponse> responseFactory;
    private final LineParser lineParser;

    public DefaultHttpResponseParserFactory(
            final Http1Config http1Config,
            final HttpResponseFactory<HttpResponse> responseFactory,
            final LineParser lineParser) {
        super();
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.responseFactory = responseFactory != null ? responseFactory : DefaultHttpResponseFactory.INSTANCE;
        this.lineParser = lineParser != null ? lineParser : LazyLaxLineParser.INSTANCE;
    }

    public DefaultHttpResponseParserFactory(final Http1Config http1Config) {
        this(http1Config, null, null);
    }

    public DefaultHttpResponseParserFactory() {
        this(null);
    }

    @Override
    public NHttpMessageParser<HttpResponse> create() {
        return new DefaultHttpResponseParser<>(responseFactory, lineParser, http1Config);
    }

}
