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
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.LazyLineParser;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;

/**
 * Default factory for request message parsers.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpRequestParserFactory implements NHttpMessageParserFactory<HttpRequest> {

    public static final DefaultHttpRequestParserFactory INSTANCE = new DefaultHttpRequestParserFactory();

    private final Http1Config http1Config;
    private final LineParser lineParser;
    private final HttpRequestFactory<HttpRequest> requestFactory;

    public DefaultHttpRequestParserFactory(
            final Http1Config http1Config,
            final HttpRequestFactory<HttpRequest> requestFactory,
            final LineParser lineParser) {
        super();
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.requestFactory = requestFactory != null ? requestFactory : DefaultHttpRequestFactory.INSTANCE;
        this.lineParser = lineParser != null ? lineParser : LazyLineParser.INSTANCE;
    }

    public DefaultHttpRequestParserFactory(final Http1Config http1Config) {
        this(http1Config, null, null);
    }

    public DefaultHttpRequestParserFactory() {
        this(null);
    }

    @Override
    public NHttpMessageParser<HttpRequest> create() {
        return new DefaultHttpRequestParser<>(requestFactory, lineParser, http1Config);
    }

}
