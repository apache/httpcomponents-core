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

package org.apache.http.impl.nio.codecs;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.ParseException;
import org.apache.http.RequestLine;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.message.LineParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * Default {@link org.apache.http.nio.NHttpMessageParser} implementation
 * for {@link HttpRequest}s.
 *
 * @since 4.1
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public class DefaultHttpRequestParser extends AbstractMessageParser<HttpRequest> {

    private final HttpRequestFactory requestFactory;

    /**
     * Creates an instance of this class.
     *
     * @param buffer the session input buffer.
     * @param parser the line parser.
     * @param params HTTP parameters.
     *
     * @deprecated (4.3) use
     *   {@link DefaultHttpRequestParser#DefaultHttpRequestParser(
     *   SessionInputBuffer, LineParser, HttpRequestFactory, MessageConstraints)}
     */
    @Deprecated
    public DefaultHttpRequestParser(
            final SessionInputBuffer buffer,
            final LineParser parser,
            final HttpRequestFactory requestFactory,
            final HttpParams params) {
        super(buffer, parser, params);
        Args.notNull(requestFactory, "Request factory");
        this.requestFactory = requestFactory;
    }

    /**
     * Creates an instance of DefaultHttpRequestParser.
     *
     * @param buffer the session input buffer.
     * @param parser the line parser. If {@code null}
     *   {@link org.apache.http.message.BasicLineParser#INSTANCE} will be used.
     * @param requestFactory the request factory. If {@code null}
     *   {@link DefaultHttpRequestFactory#INSTANCE} will be used.
     * @param constraints Message constraints. If {@code null}
     *   {@link MessageConstraints#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public DefaultHttpRequestParser(
            final SessionInputBuffer buffer,
            final LineParser parser,
            final HttpRequestFactory requestFactory,
            final MessageConstraints constraints) {
        super(buffer, parser, constraints);
        this.requestFactory = requestFactory != null ? requestFactory : DefaultHttpRequestFactory.INSTANCE;
    }

    /**
    * @since 4.3
    */
    public DefaultHttpRequestParser(final SessionInputBuffer buffer, final MessageConstraints constraints) {
        this(buffer, null, null, constraints);
    }

    /**
    * @since 4.3
    */
    public DefaultHttpRequestParser(final SessionInputBuffer buffer) {
        this(buffer, null);
    }

    @Override
    protected HttpRequest createMessage(final CharArrayBuffer buffer)
            throws HttpException, ParseException {
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final RequestLine requestLine = lineParser.parseRequestLine(buffer, cursor);
        return this.requestFactory.newHttpRequest(requestLine);
    }

}
