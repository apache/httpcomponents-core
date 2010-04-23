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

package org.apache.http.contrib.logging;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.NHttpMessageWriter;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpParams;

public class LoggingNHttpClientConnection extends DefaultNHttpClientConnection {

    private final Log log;
    private final Log headerlog;

    public LoggingNHttpClientConnection(
        final IOSession session,
        final HttpResponseFactory responseFactory,
        final ByteBufferAllocator allocator,
        final HttpParams params) {
        super(session, responseFactory, allocator, params);
        this.log = LogFactory.getLog(getClass());
        this.headerlog = LogFactory.getLog("org.apache.http.headers");
    }

    @Override
    public void close() throws IOException {
        this.log.debug("Close connection");
        super.close();
    }

    @Override
    public void shutdown() throws IOException {
        this.log.debug("Shutdown connection");
        super.shutdown();
    }

    @Override
    public void submitRequest(final HttpRequest request) throws IOException, HttpException {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + this + ": "  + request.getRequestLine().toString());
        }
        super.submitRequest(request);
    }

    @Override
    public void consumeInput(final NHttpClientHandler handler) {
        this.log.debug("Consume input");
        super.consumeInput(handler);
    }

    @Override
    public void produceOutput(final NHttpClientHandler handler) {
        this.log.debug("Produce output");
        super.produceOutput(handler);
    }

    @Override
    protected NHttpMessageWriter<HttpRequest> createRequestWriter(
            final SessionOutputBuffer buffer,
            final HttpParams params) {
        return new LoggingNHttpMessageWriter(
                super.createRequestWriter(buffer, params));
    }

    @Override
    protected NHttpMessageParser<HttpResponse> createResponseParser(
            final SessionInputBuffer buffer,
            final HttpResponseFactory responseFactory,
            final HttpParams params) {
        return new LoggingNHttpMessageParser(
                super.createResponseParser(buffer, responseFactory, params));
    }

    class LoggingNHttpMessageWriter implements NHttpMessageWriter<HttpRequest> {

        private final NHttpMessageWriter<HttpRequest> writer;

        public LoggingNHttpMessageWriter(final NHttpMessageWriter<HttpRequest> writer) {
            super();
            this.writer = writer;
        }

        public void reset() {
            this.writer.reset();
        }

        public void write(final HttpRequest message) throws IOException, HttpException {
            if (message != null && headerlog.isDebugEnabled()) {
                headerlog.debug(">> " + message.getRequestLine().toString());
                Header[] headers = message.getAllHeaders();
                for (int i = 0; i < headers.length; i++) {
                    headerlog.debug(">> " + headers[i].toString());
                }
            }
            this.writer.write(message);
        }

    }

    class LoggingNHttpMessageParser implements NHttpMessageParser<HttpResponse> {

        private final NHttpMessageParser<HttpResponse> parser;

        public LoggingNHttpMessageParser(final NHttpMessageParser<HttpResponse> parser) {
            super();
            this.parser = parser;
        }

        public void reset() {
            this.parser.reset();
        }

        public int fillBuffer(final ReadableByteChannel channel) throws IOException {
            return this.parser.fillBuffer(channel);
        }

        public HttpResponse parse() throws IOException, HttpException {
            HttpResponse message = this.parser.parse();
            if (message != null && headerlog.isDebugEnabled()) {
                headerlog.debug("<< " + message.getStatusLine().toString());
                Header[] headers = message.getAllHeaders();
                for (int i = 0; i < headers.length; i++) {
                    headerlog.debug("<< " + headers[i].toString());
                }
            }
            return message;
        }

    }

}