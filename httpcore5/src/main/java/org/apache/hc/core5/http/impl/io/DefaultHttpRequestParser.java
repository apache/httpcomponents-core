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

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequestFactory;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * HTTP request parser that obtain its input from an instance
 * of {@link org.apache.hc.core5.http.io.SessionInputBuffer}.
 *
 * @since 4.2
 */
public class DefaultHttpRequestParser extends AbstractMessageParser<ClassicHttpRequest> {

    private final HttpRequestFactory<ClassicHttpRequest> requestFactory;

    /**
     * Creates new instance of DefaultHttpRequestParser.
     *
     * @param lineParser the line parser. If {@code null}
     *   {@link org.apache.hc.core5.http.message.LazyLineParser#INSTANCE} will be used.
     * @param requestFactory the response factory. If {@code null}
     *   {@link DefaultClassicHttpRequestFactory#INSTANCE} will be used.
     * @param h1Config the message h1Config. If {@code null}
     *   {@link H1Config#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public DefaultHttpRequestParser(
            final LineParser lineParser,
            final HttpRequestFactory<ClassicHttpRequest> requestFactory,
            final H1Config h1Config) {
        super(lineParser, h1Config);
        this.requestFactory = requestFactory != null ? requestFactory : DefaultClassicHttpRequestFactory.INSTANCE;
    }

    /**
     * @since 4.3
     */
    public DefaultHttpRequestParser(final H1Config h1Config) {
        this(null, null, h1Config);
    }

    /**
     * @since 4.3
     */
    public DefaultHttpRequestParser() {
        this(H1Config.DEFAULT);
    }

    @Override
    protected IOException createConnectionClosedException() {
        return new ConnectionClosedException("Client closed connection");
    }

    @Override
    protected ClassicHttpRequest createMessage(final CharArrayBuffer buffer) throws IOException, HttpException {
        final RequestLine requestLine = getLineParser().parseRequestLine(buffer);
        final ClassicHttpRequest request = this.requestFactory.newHttpRequest(requestLine.getMethod(), requestLine.getUri());
        request.setVersion(requestLine.getProtocolVersion());
        return request;
    }

}
