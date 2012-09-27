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

package org.apache.http.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.impl.io.DefaultHttpRequestWriter;
import org.apache.http.impl.io.DefaultHttpResponseParser;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.HttpMessageWriter;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.BasicLineFormatter;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineFormatter;
import org.apache.http.message.LineParser;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;

/**
 * Default implementation of {@link HttpClientConnection}.
 * <p/>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_MALFORMED_INPUT_ACTION}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_UNMAPPABLE_INPUT_ACTION}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MIN_CHUNK_LIMIT}</li>
 * </ul>
 *
 * @since 4.3
 */
@NotThreadSafe
public class DefaultBHttpClientConnection extends BHttpConnectionBase
                                                   implements HttpClientConnection {

    private final HttpMessageParser<HttpResponse> responseParser;
    private final HttpMessageWriter<HttpRequest> requestWriter;

    public DefaultBHttpClientConnection(
            final LineParser lineParser,
            final LineFormatter lineFormatter,
            final HttpResponseFactory responseFactory,
            final HttpParams params) {
        super(params);
        this.responseParser = createResponseParser(
                getSessionInputBuffer(), lineParser, responseFactory, params);
        this.requestWriter = createRequestWriter(
                getSessionOutputBuffer(), lineFormatter, params);
    }

    public DefaultBHttpClientConnection(final HttpParams params) {
        this(null, null, null, params);
    }

    /**
     * Creates an instance of {@link HttpMessageParser} to be used for parsing
     * HTTP responses received over this connection.
     *
     * @param buffer the session input buffer.
     * @param lineParser the line parser. If <code>null</code> {@link BasicLineParser#INSTANCE}
     *   will be used
     * @param responseFactory the response factory. If <code>null</code>
     *   {@link DefaultHttpResponseFactory#INSTANCE} will be used.
     * @param params HTTP parameters.
     * @return HTTP response parser.
     */
    protected HttpMessageParser<HttpResponse> createResponseParser(
            final SessionInputBuffer buffer,
            final LineParser lineParser,
            final HttpResponseFactory responseFactory,
            final HttpParams params) {
        int maxHeaderCount = params.getIntParameter(CoreConnectionPNames.MAX_HEADER_COUNT, -1);
        int maxLineLen = params.getIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, -1);
        return new DefaultHttpResponseParser(
                buffer, maxHeaderCount, maxLineLen, lineParser, responseFactory);
    }

    /**
     * Creates an instance of {@link HttpMessageWriter} to be used for
     * writing out HTTP requests sent over this connection.
     *
     * @param buffer the session output buffer
     * @param lineFormatter the line formatter. If <code>null</code>
     *   {@link BasicLineFormatter#INSTANCE} will be used.
     * @param params HTTP parameters
     * @return HTTP request writer
     */
    protected HttpMessageWriter<HttpRequest> createRequestWriter(
            final SessionOutputBuffer buffer,
            final LineFormatter lineFormatter,
            final HttpParams params) {
        return new DefaultHttpRequestWriter(buffer, lineFormatter);
    }

    protected void onResponseReceived(final HttpResponse response) {
    }

    protected void onRequestSubmitted(final HttpRequest request) {
    }

    @Override
    public void bind(final Socket socket) throws IOException {
        super.bind(socket);
    }

    public boolean isResponseAvailable(int timeout) throws IOException {
        assertOpen();
        try {
            return awaitInput(timeout);
        } catch (SocketTimeoutException ex) {
            return false;
        }
    }

    public void sendRequestHeader(final HttpRequest request)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        assertOpen();
        this.requestWriter.write(request);
        onRequestSubmitted(request);
        incrementRequestCount();
    }

    public void sendRequestEntity(final HttpEntityEnclosingRequest request)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        assertOpen();
        HttpEntity entity = request.getEntity();
        if (entity == null) {
            return;
        }
        OutputStream outstream = prepareOutput(request);
        entity.writeTo(outstream);
        outstream.close();
    }

    public HttpResponse receiveResponseHeader() throws HttpException, IOException {
        assertOpen();
        HttpResponse response = this.responseParser.parse();
        onResponseReceived(response);
        if (response.getStatusLine().getStatusCode() >= 200) {
            incrementResponseCount();
        }
        return response;
    }

    public void receiveResponseEntity(
            final HttpResponse response) throws HttpException, IOException {
        Args.notNull(response, "HTTP response");
        assertOpen();
        HttpEntity entity = prepareInput(response);
        response.setEntity(entity);
    }

    public void flush() throws IOException {
        assertOpen();
        doFlush();
    }

}
