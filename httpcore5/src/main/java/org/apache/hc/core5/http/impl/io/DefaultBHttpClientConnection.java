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
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.LengthRequiredException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpMessageParser;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriter;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.util.Args;

/**
 * Default implementation of {@link HttpClientConnection}.
 *
 * @since 4.3
 */
public class DefaultBHttpClientConnection extends BHttpConnectionBase
                                                   implements HttpClientConnection {

    private final HttpMessageParser<ClassicHttpResponse> responseParser;
    private final HttpMessageWriter<ClassicHttpRequest> requestWriter;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private volatile boolean consistent;

    /**
     * Creates new instance of DefaultBHttpClientConnection.
     *
     * @param h1Config Message h1Config. If {@code null}
     *   {@link H1Config#DEFAULT} will be used.
     * @param chardecoder decoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     * @param charencoder encoder to be used for encoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for char to byte conversion.
     * @param incomingContentStrategy incoming content length strategy. If {@code null}
     *   {@link DefaultContentLengthStrategy#INSTANCE} will be used.
     * @param outgoingContentStrategy outgoing content length strategy. If {@code null}
     *   {@link DefaultContentLengthStrategy#INSTANCE} will be used.
     * @param requestWriterFactory request writer factory. If {@code null}
     *   {@link DefaultHttpRequestWriterFactory#INSTANCE} will be used.
     * @param responseParserFactory response parser factory. If {@code null}
     *   {@link DefaultHttpResponseParserFactory#INSTANCE} will be used.
     */
    public DefaultBHttpClientConnection(
            final H1Config h1Config,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        super(h1Config, chardecoder, charencoder);
        this.requestWriter = (requestWriterFactory != null ? requestWriterFactory :
            DefaultHttpRequestWriterFactory.INSTANCE).create();
        this.responseParser = (responseParserFactory != null ? responseParserFactory :
            DefaultHttpResponseParserFactory.INSTANCE).create(h1Config);
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.consistent = true;
    }

    public DefaultBHttpClientConnection(
            final H1Config h1Config,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder) {
        this(h1Config, chardecoder, charencoder, null, null, null, null);
    }

    public DefaultBHttpClientConnection(final H1Config h1Config) {
        this(h1Config, null, null);
    }

    protected void onResponseReceived(final ClassicHttpResponse response) {
    }

    protected void onRequestSubmitted(final ClassicHttpRequest request) {
    }

    @Override
    public void bind(final Socket socket) throws IOException {
        super.bind(socket);
    }

    @Override
    public void sendRequestHeader(final ClassicHttpRequest request)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        final SocketHolder socketHolder = ensureOpen();
        this.requestWriter.write(request, this.outbuffer, socketHolder.getOutputStream());
        onRequestSubmitted(request);
        incrementRequestCount();
    }

    @Override
    public void sendRequestEntity(final ClassicHttpRequest request) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        final SocketHolder socketHolder = ensureOpen();
        final HttpEntity entity = request.getEntity();
        if (entity == null) {
            return;
        }
        final long len = this.outgoingContentStrategy.determineLength(request);
        if (len == ContentLengthStrategy.UNDEFINED) {
            throw new LengthRequiredException("Length required");
        }
        final OutputStream outstream = createContentOutputStream(len, this.outbuffer, socketHolder.getOutputStream(), entity.getTrailers());
        entity.writeTo(outstream);
        outstream.close();
    }

    @Override
    public boolean isConsistent() {
        return this.consistent;
    }

    @Override
    public void terminateRequest(final ClassicHttpRequest request) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        final SocketHolder socketHolder = ensureOpen();
        final HttpEntity entity = request.getEntity();
        if (entity == null) {
            return;
        }
        final long len = this.outgoingContentStrategy.determineLength(request);
        if (len == ContentLengthStrategy.CHUNKED) {
            final OutputStream outstream = createContentOutputStream(len, this.outbuffer, socketHolder.getOutputStream(), entity.getTrailers());
            outstream.close();
        } else if (len >= 0 && len <= 1024) {
            final OutputStream outstream = createContentOutputStream(len, this.outbuffer, socketHolder.getOutputStream(), null);
            entity.writeTo(outstream);
            outstream.close();
        } else {
            this.consistent = false;
        }
    }

    @Override
    public ClassicHttpResponse receiveResponseHeader() throws HttpException, IOException {
        final SocketHolder socketHolder = ensureOpen();
        final ClassicHttpResponse response = this.responseParser.parse(this.inbuffer, socketHolder.getInputStream());
        final ProtocolVersion transportVersion = response.getVersion();
        if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
            throw new UnsupportedHttpVersionException("Unsupported version: " + transportVersion);
        }
        this.version = transportVersion;
        onResponseReceived(response);
        if (response.getCode() >= HttpStatus.SC_SUCCESS) {
            incrementResponseCount();
        }
        return response;
    }

    @Override
    public void receiveResponseEntity( final ClassicHttpResponse response) throws HttpException, IOException {
        Args.notNull(response, "HTTP response");
        final SocketHolder socketHolder = ensureOpen();
        final long len = this.incomingContentStrategy.determineLength(response);
        if (len == ContentLengthStrategy.UNDEFINED) {
            return;
        }
        response.setEntity(createIncomingEntity(response, this.inbuffer, socketHolder.getInputStream(), len));
    }
}
