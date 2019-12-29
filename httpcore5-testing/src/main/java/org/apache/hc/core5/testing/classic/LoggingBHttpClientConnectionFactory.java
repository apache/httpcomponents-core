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

package org.apache.hc.core5.testing.classic;

import java.io.IOException;
import java.net.Socket;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;

public class LoggingBHttpClientConnectionFactory implements HttpConnectionFactory<LoggingBHttpClientConnection> {

    public static final LoggingBHttpClientConnectionFactory INSTANCE = new LoggingBHttpClientConnectionFactory();

    private final Http1Config http1Config;
    private final CharCodingConfig charCodingConfig;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory;
    private final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory;

    public LoggingBHttpClientConnectionFactory(
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        super();
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.incomingContentStrategy = incomingContentStrategy;
        this.outgoingContentStrategy = outgoingContentStrategy;
        this.requestWriterFactory = requestWriterFactory;
        this.responseParserFactory = responseParserFactory;
    }

    public LoggingBHttpClientConnectionFactory(
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        this(http1Config, charCodingConfig, null, null, requestWriterFactory, responseParserFactory);
    }

    public LoggingBHttpClientConnectionFactory(
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig) {
        this(http1Config, charCodingConfig, null, null, null, null);
    }

    public LoggingBHttpClientConnectionFactory() {
        this(null, null, null, null, null, null);
    }

    @Override
    public LoggingBHttpClientConnection createConnection(final Socket socket) throws IOException {
        final LoggingBHttpClientConnection conn = new LoggingBHttpClientConnection(
                http1Config,
                CharCodingSupport.createDecoder(charCodingConfig),
                CharCodingSupport.createEncoder(charCodingConfig),
                incomingContentStrategy,
                outgoingContentStrategy,
                requestWriterFactory,
                responseParserFactory);
        conn.bind(socket);
        return conn;
    }

}
