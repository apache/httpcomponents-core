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
import java.nio.channels.SelectionKey;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.config.MessageConstraints;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestWriterFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParserFactory;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.NHttpMessageParserFactory;
import org.apache.http.nio.NHttpMessageWriter;
import org.apache.http.nio.NHttpMessageWriterFactory;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.util.Args;

/**
 * Default implementation of the {@link org.apache.http.nio.NHttpClientConnection}
 * interface.
 *
 * @since 4.0
 */
@NotThreadSafe
public class DefaultNHttpClientConnection
    extends NHttpConnectionBase implements NHttpClientConnection {

    protected final NHttpMessageParser<HttpResponse> responseParser;
    protected final NHttpMessageWriter<HttpRequest> requestWriter;

    /**
     * Creates new instance DefaultNHttpClientConnection given the underlying I/O session.
     *
     * @param session the underlying I/O session.
     * @param buffersize buffer size. Must be a positive number.
     * @param fragmentSizeHint fragment size hint.
     * @param allocator memory allocator.
     *   If {@code null} {@link org.apache.http.nio.util.HeapByteBufferAllocator#INSTANCE}
     *   will be used.
     * @param chardecoder decoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     * @param charencoder encoder to be used for encoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for char to byte conversion.
     * @param constraints Message constraints. If {@code null}
     *   {@link MessageConstraints#DEFAULT} will be used.
     * @param incomingContentStrategy incoming content length strategy. If {@code null}
     *   {@link org.apache.http.impl.entity.LaxContentLengthStrategy#INSTANCE} will be used.
     * @param outgoingContentStrategy outgoing content length strategy. If {@code null}
     *   {@link org.apache.http.impl.entity.StrictContentLengthStrategy#INSTANCE} will be used.
     *
     * @since 4.3
     */
    public DefaultNHttpClientConnection(
            final IOSession session,
            final int buffersize,
            final int fragmentSizeHint,
            final ByteBufferAllocator allocator,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final MessageConstraints constraints,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final NHttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final NHttpMessageParserFactory<HttpResponse> responseParserFactory) {
        super(session, buffersize, fragmentSizeHint, allocator, chardecoder, charencoder,
                constraints, incomingContentStrategy, outgoingContentStrategy);
        this.requestWriter = (requestWriterFactory != null ? requestWriterFactory :
            DefaultHttpRequestWriterFactory.INSTANCE).create(this.outbuf);
        this.responseParser = (responseParserFactory != null ? responseParserFactory :
            DefaultHttpResponseParserFactory.INSTANCE).create(this.inbuf, constraints);
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpClientConnection(
            final IOSession session,
            final int buffersize,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final MessageConstraints constraints) {
        this(session, buffersize, buffersize, null, chardecoder, charencoder, constraints,
                null, null, null, null);
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpClientConnection(final IOSession session, final int buffersize) {
        this(session, buffersize, buffersize, null, null, null, null, null, null, null, null);
    }

    /**
     * @since 4.2
     */
    protected void onResponseReceived(final HttpResponse response) {
    }

    /**
     * @since 4.2
     */
    protected void onRequestSubmitted(final HttpRequest request) {
    }

    @Override
    public void resetInput() {
        this.response = null;
        this.contentDecoder = null;
        this.responseParser.reset();
    }

    @Override
    public void resetOutput() {
        this.request = null;
        this.contentEncoder = null;
        this.requestWriter.reset();
    }

    public void consumeInput(final NHttpClientEventHandler handler) {
        if (this.status != ACTIVE) {
            this.session.clearEvent(EventMask.READ);
            return;
        }
        try {
            if (this.response == null) {
                int bytesRead;
                do {
                    bytesRead = this.responseParser.fillBuffer(this.session.channel());
                    if (bytesRead > 0) {
                        this.inTransportMetrics.incrementBytesTransferred(bytesRead);
                    }
                    this.response = this.responseParser.parse();
                } while (bytesRead > 0 && this.response == null);
                if (this.response != null) {
                    if (this.response.getStatusLine().getStatusCode() >= 200) {
                        final HttpEntity entity = prepareDecoder(this.response);
                        this.response.setEntity(entity);
                        this.connMetrics.incrementResponseCount();
                    }
                    this.hasBufferedInput = this.inbuf.hasData();
                    onResponseReceived(this.response);
                    handler.responseReceived(this);
                    if (this.contentDecoder == null) {
                        resetInput();
                    }
                }
                if (bytesRead == -1) {
                    handler.endOfInput(this);
                }
            }
            if (this.contentDecoder != null && (this.session.getEventMask() & SelectionKey.OP_READ) > 0) {
                handler.inputReady(this, this.contentDecoder);
                if (this.contentDecoder.isCompleted()) {
                    // Response entity received
                    // Ready to receive a new response
                    resetInput();
                }
            }
        } catch (final HttpException ex) {
            resetInput();
            handler.exception(this, ex);
        } catch (final Exception ex) {
            handler.exception(this, ex);
        } finally {
            // Finally set buffered input flag
            this.hasBufferedInput = this.inbuf.hasData();
        }
    }

    public void produceOutput(final NHttpClientEventHandler handler) {
        try {
            if (this.status == ACTIVE) {
                if (this.contentEncoder == null) {
                    handler.requestReady(this);
                }
                if (this.contentEncoder != null) {
                    handler.outputReady(this, this.contentEncoder);
                    if (this.contentEncoder.isCompleted()) {
                        resetOutput();
                    }
                }
            }
            if (this.outbuf.hasData()) {
                final int bytesWritten = this.outbuf.flush(this.session.channel());
                if (bytesWritten > 0) {
                    this.outTransportMetrics.incrementBytesTransferred(bytesWritten);
                }
            }
            if (!this.outbuf.hasData()) {
                if (this.status == CLOSING) {
                    this.session.close();
                    this.status = CLOSED;
                    resetOutput();
                }
                if (this.contentEncoder == null && this.status != CLOSED) {
                    this.session.clearEvent(EventMask.WRITE);
                }
            }
        } catch (final Exception ex) {
            handler.exception(this, ex);
        } finally {
            // Finally set the buffered output flag
            this.hasBufferedOutput = this.outbuf.hasData();
        }
    }

    @Override
    public void submitRequest(final HttpRequest request) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        assertNotClosed();
        if (this.request != null) {
            throw new HttpException("Request already submitted");
        }
        onRequestSubmitted(request);
        this.requestWriter.write(request);
        this.hasBufferedOutput = this.outbuf.hasData();

        if (request instanceof HttpEntityEnclosingRequest
                && ((HttpEntityEnclosingRequest) request).getEntity() != null) {
            prepareEncoder(request);
            this.request = request;
        }
        this.connMetrics.incrementRequestCount();
        this.session.setEvent(EventMask.WRITE);
    }

    @Override
    public boolean isRequestSubmitted() {
        return this.request != null;
    }

}
