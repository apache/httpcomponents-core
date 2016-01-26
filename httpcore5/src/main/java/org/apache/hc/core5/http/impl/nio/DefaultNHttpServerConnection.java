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

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.MessageConstraints;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.http.nio.NHttpServerConnection;
import org.apache.hc.core5.http.nio.NHttpServerEventHandler;
import org.apache.hc.core5.reactor.EventMask;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteBufferAllocator;

/**
 * Default implementation of the {@link org.apache.hc.core5.http.nio.NHttpServerConnection}
 * interface.
 *
 * @since 4.0
 */
@NotThreadSafe
public class DefaultNHttpServerConnection
    extends NHttpConnectionBase implements NHttpServerConnection {

    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final NHttpMessageParser<HttpRequest> requestParser;
    private final NHttpMessageWriter<HttpResponse> responseWriter;

    /**
     * Creates new instance DefaultNHttpServerConnection given the underlying I/O session.
     *
     * @param session the underlying I/O session.
     * @param buffersize buffer size. Must be a positive number.
     * @param fragmentSizeHint fragment size hint.
     * @param allocator memory allocator.
     *   If {@code null} {@link org.apache.hc.core5.util.HeapByteBufferAllocator#INSTANCE}
     *   will be used.
     * @param chardecoder decoder to be used for decoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for byte to char conversion.
     * @param charencoder encoder to be used for encoding HTTP protocol elements.
     *   If {@code null} simple type cast will be used for char to byte conversion.
     * @param constraints Message constraints. If {@code null}
     *   {@link MessageConstraints#DEFAULT} will be used.
     * @param incomingContentStrategy incoming content length strategy. If {@code null}
     *   {@link DefaultContentLengthStrategy#INSTANCE} will be used.
     * @param outgoingContentStrategy outgoing content length strategy. If {@code null}
     *   {@link DefaultContentLengthStrategy#INSTANCE} will be used.
     * @param requestParserFactory request parser factory. If {@code null}
     *   {@link DefaultHttpRequestParserFactory#INSTANCE} will be used.
     * @param responseWriterFactory response writer factory. If {@code null}
     *   {@link DefaultHttpResponseWriterFactory#INSTANCE} will be used.
     *
     * @since 4.3
     */
    public DefaultNHttpServerConnection(
            final IOSession session,
            final int buffersize,
            final int fragmentSizeHint,
            final ByteBufferAllocator allocator,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final MessageConstraints constraints,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final NHttpMessageParserFactory<HttpRequest> requestParserFactory,
            final NHttpMessageWriterFactory<HttpResponse> responseWriterFactory) {
        super(session, buffersize, fragmentSizeHint, allocator, chardecoder, charencoder, constraints);
        this.requestParser = (requestParserFactory != null ? requestParserFactory :
            DefaultHttpRequestParserFactory.INSTANCE).create(constraints);
        this.responseWriter = (responseWriterFactory != null ? responseWriterFactory :
            DefaultHttpResponseWriterFactory.INSTANCE).create();
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
    }

    /**
     * @since 4.3
     */
    public DefaultNHttpServerConnection(
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
    public DefaultNHttpServerConnection(final IOSession session, final int buffersize) {
        this(session, buffersize, buffersize, null, null, null, null, null, null, null, null);
    }

    /**
     * @since 4.2
     */
    protected void onRequestReceived(final HttpRequest request) {
    }

    /**
     * @since 4.2
     */
    protected void onResponseSubmitted(final HttpResponse response) {
    }

    @Override
    public void resetInput() {
        this.request = null;
        this.contentDecoder = null;
        this.requestParser.reset();
    }

    @Override
    public void resetOutput() {
        this.response = null;
        this.contentEncoder = null;
        this.responseWriter.reset();
    }

    public void consumeInput(final NHttpServerEventHandler handler) {
        if (this.status != ACTIVE) {
            this.session.clearEvent(EventMask.READ);
            return;
        }
        try {
            if (this.request == null) {
                int bytesRead;
                do {
                    bytesRead = this.inbuf.fill(this.session.channel());
                    if (bytesRead > 0) {
                        this.inTransportMetrics.incrementBytesTransferred(bytesRead);
                    }
                    this.request = this.requestParser.parse(this.inbuf, bytesRead == -1);
                } while (bytesRead > 0 && this.request == null);
                if (this.request != null) {
                    final long len = this.incomingContentStrategy.determineLength(this.request);
                    if (len != ContentLengthStrategy.UNDEFINED) {
                        this.contentDecoder = createContentDecoder(
                                len,
                                this.session.channel(),
                                this.inbuf,
                                this.inTransportMetrics);
                        final HttpEntity entity = createIncomingEntity(this.request, len);
                        this.request.setEntity(entity);
                    }
                    this.connMetrics.incrementRequestCount();
                    this.hasBufferedInput = this.inbuf.hasData();
                    onRequestReceived(this.request);
                    handler.requestReceived(this);
                    if (this.contentDecoder == null) {
                        // No request entity is expected
                        // Ready to receive a new request
                        resetInput();
                    }
                }
                if (bytesRead == -1 && !this.inbuf.hasData()) {
                    handler.endOfInput(this);
                }
            }
            if (this.contentDecoder != null && (this.session.getEventMask() & SelectionKey.OP_READ) > 0) {
                handler.inputReady(this, this.contentDecoder);
                if (this.contentDecoder.isCompleted()) {
                    // Request entity received
                    // Ready to receive a new request
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

    public void produceOutput(final NHttpServerEventHandler handler) {
        try {
            if (this.status == ACTIVE) {
                if (this.contentEncoder == null && !this.outbuf.hasData()) {
                    handler.responseReady(this);
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
            }
        } catch (final Exception ex) {
            handler.exception(this, ex);
        } finally {
            // Finally set the buffered output flag
            this.hasBufferedOutput = this.outbuf.hasData();
        }
    }

    @Override
    public void submitResponse(final HttpResponse response) throws IOException, HttpException {
        Args.notNull(response, "HTTP response");
        assertNotClosed();
        if (this.response != null) {
            throw new HttpException("Response already submitted");
        }
        onResponseSubmitted(response);
        this.responseWriter.write(response, this.outbuf);
        this.hasBufferedOutput = this.outbuf.hasData();

        if (response.getCode() >= HttpStatus.SC_SUCCESS) {
            this.connMetrics.incrementResponseCount();
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                this.response = response;
                final long len = this.outgoingContentStrategy.determineLength(response);
                this.contentEncoder = createContentEncoder(
                        len,
                        this.session.channel(),
                        this.outbuf,
                        this.outTransportMetrics,
                        entity.getTrailers());
            }
        }

        this.session.setEvent(EventMask.WRITE);
    }

    @Override
    public boolean isResponseSubmitted() {
        return this.response != null;
    }

}
