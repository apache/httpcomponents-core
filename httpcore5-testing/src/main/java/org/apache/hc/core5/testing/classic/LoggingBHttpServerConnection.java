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
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.SocketHolder;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Identifiable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
public class LoggingBHttpServerConnection extends DefaultBHttpServerConnection implements Identifiable {

    private static final AtomicLong COUNT = new AtomicLong();

    private final String id;
    private final Logger log;
    private final Logger headerlog;
    private final Wire wire;

    public LoggingBHttpServerConnection(
            final H1Config h1Config,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageParserFactory<ClassicHttpRequest> requestParserFactory,
            final HttpMessageWriterFactory<ClassicHttpResponse> responseWriterFactory) {
        super(h1Config, chardecoder, charencoder,
                incomingContentStrategy, outgoingContentStrategy,
                requestParserFactory, responseWriterFactory);
        this.id = "http-incoming-" + COUNT.incrementAndGet();
        this.log = LogManager.getLogger(getClass());
        this.headerlog = LogManager.getLogger("org.apache.hc.core5.http.headers");
        this.wire = new Wire(LogManager.getLogger("org.apache.hc.core5.http.wire"), this.id);
    }

    @Override
    public String getId() {
        return id;
    }

    public LoggingBHttpServerConnection(final H1Config h1Config) {
        this(h1Config, null, null, null, null, null, null);
    }

    @Override
    public void close() throws IOException {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + ": Close connection");
        }
        super.close();
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + ": Shutdown connection");
        }
        super.shutdown(shutdownType);
    }

    @Override
    public void bind(final Socket socket) throws IOException {
        super.bind(this.wire.isEnabled() ? new LoggingSocketHolder(socket, wire) : new SocketHolder(socket));
    }

    @Override
    protected void onRequestReceived(final ClassicHttpRequest request) {
        if (request != null && this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(id + " >> " + new RequestLine(request));
            final Header[] headers = request.getAllHeaders();
            for (final Header header : headers) {
                this.headerlog.debug(this.id + " >> " + header.toString());
            }
        }
    }

    @Override
    protected void onResponseSubmitted(final ClassicHttpResponse response) {
        if (response != null && this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(this.id + " << " + new StatusLine(response));
            final Header[] headers = response.getAllHeaders();
            for (final Header header : headers) {
                this.headerlog.debug(this.id + " << " + header.toString());
            }
        }
    }

}
