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

package org.apache.http.impl.nio;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestParser;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseWriter;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.NHttpMessageWriter;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServerIOTarget;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpParams;

/**
 * Default implementation of the {@link NHttpServerConnection} interface.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 * </ul>
 *
 * @since 4.0
 */
public class DefaultNHttpServerConnection
    extends NHttpConnectionBase implements NHttpServerIOTarget {

    protected final NHttpMessageParser<HttpRequest> requestParser;
    protected final NHttpMessageWriter<HttpResponse> responseWriter;

    /**
     * Creates a new instance of this class given the underlying I/O session.
     *
     * @param session the underlying I/O session.
     * @param requestFactory HTTP request factory.
     * @param allocator byte buffer allocator.
     * @param params HTTP parameters.
     */
    public DefaultNHttpServerConnection(
            final IOSession session,
            final HttpRequestFactory requestFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(session, allocator, params);
        if (requestFactory == null) {
            throw new IllegalArgumentException("Request factory may not be null");
        }
        this.requestParser = createRequestParser(this.inbuf, requestFactory, params);
        this.responseWriter = createResponseWriter(this.outbuf, params);
    }

    /**
     * Creates an instance of {@link NHttpMessageParser} to be used
     * by this connection for parsing incoming {@link HttpRequest} messages.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link NHttpMessageParser} interface.
     *
     * @return HTTP response parser.
     */
    protected NHttpMessageParser<HttpRequest> createRequestParser(
            final SessionInputBuffer buffer,
            final HttpRequestFactory requestFactory,
            final HttpParams params) {
        // override in derived class to specify a line parser
        return new DefaultHttpRequestParser(buffer, null, requestFactory, params);
    }

    /**
     * Creates an instance of {@link NHttpMessageWriter} to be used
     * by this connection for writing out outgoing {@link HttpResponse}
     * messages.
     * <p>
     * This method can be overridden by a super class in order to provide
     * a different implementation of the {@link NHttpMessageWriter} interface.
     *
     * @return HTTP response parser.
     */
    protected NHttpMessageWriter<HttpResponse> createResponseWriter(
            final SessionOutputBuffer buffer,
            final HttpParams params) {
        // override in derived class to specify a line formatter
        return new DefaultHttpResponseWriter(buffer, null, params);
    }

    public void resetInput() {
        this.request = null;
        this.contentDecoder = null;
        this.requestParser.reset();
    }

    public void resetOutput() {
        this.response = null;
        this.contentEncoder = null;
        this.responseWriter.reset();
    }

    public void consumeInput(final NHttpServiceHandler handler) {
        if (this.status != ACTIVE) {
            this.session.clearEvent(EventMask.READ);
            return;
        }
        try {
            if (this.request == null) {
                int bytesRead;
                do {
                    bytesRead = this.requestParser.fillBuffer(this.session.channel());
                    if (bytesRead > 0) {
                        this.inTransportMetrics.incrementBytesTransferred(bytesRead);
                    }
                    this.request = this.requestParser.parse();
                } while (bytesRead > 0 && this.request == null);
                if (this.request != null) {
                    if (this.request instanceof HttpEntityEnclosingRequest) {
                        // Receive incoming entity
                        HttpEntity entity = prepareDecoder(this.request);
                        ((HttpEntityEnclosingRequest)this.request).setEntity(entity);
                    }
                    this.connMetrics.incrementRequestCount();
                    handler.requestReceived(this);
                    if (this.contentDecoder == null) {
                        // No request entity is expected
                        // Ready to receive a new request
                        resetInput();
                    }
                }
                if (bytesRead == -1) {
                    close();
                }
            }
            if (this.contentDecoder != null) {
                handler.inputReady(this, this.contentDecoder);
                if (this.contentDecoder.isCompleted()) {
                    // Request entity received
                    // Ready to receive a new request
                    resetInput();
                }
            }
        } catch (IOException ex) {
            handler.exception(this, ex);
        } catch (HttpException ex) {
            resetInput();
            handler.exception(this, ex);
        } finally {
            // Finally set buffered input flag
            this.hasBufferedInput = this.inbuf.hasData();
        }
    }

    public void produceOutput(final NHttpServiceHandler handler) {
        try {
            if (this.outbuf.hasData()) {
                int bytesWritten = this.outbuf.flush(this.session.channel());
                if (bytesWritten > 0) {
                    this.outTransportMetrics.incrementBytesTransferred(bytesWritten);
                }
            }
            if (!this.outbuf.hasData()) {
                if (this.status == CLOSING) {
                    this.session.close();
                    this.status = CLOSED;
                    resetOutput();
                    return;
                } else {
                    if (this.contentEncoder != null) {
                        handler.outputReady(this, this.contentEncoder);
                        if (this.contentEncoder.isCompleted()) {
                            resetOutput();
                        }
                    }
                }

                if (this.contentEncoder == null && !this.outbuf.hasData()) {
                    if (this.status == CLOSING) {
                        this.session.close();
                        this.status = CLOSED;
                    }
                    if (this.status != CLOSED) {
                        this.session.clearEvent(EventMask.WRITE);
                        handler.responseReady(this);
                    }
                }
            }
        } catch (IOException ex) {
            handler.exception(this, ex);
        } finally {
            // Finally set the buffered output flag
            this.hasBufferedOutput = this.outbuf.hasData();
        }
    }

    public void submitResponse(final HttpResponse response) throws IOException, HttpException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        assertNotClosed();
        if (this.response != null) {
            throw new HttpException("Response already submitted");
        }
        this.responseWriter.write(response);
        this.hasBufferedOutput = this.outbuf.hasData();

        if (response.getStatusLine().getStatusCode() >= 200) {
            this.connMetrics.incrementResponseCount();
            if (response.getEntity() != null) {
                this.response = response;
                prepareEncoder(response);
            }
        }

        this.session.setEvent(EventMask.WRITE);
    }

    public boolean isResponseSubmitted() {
        return this.response != null;
    }

}
