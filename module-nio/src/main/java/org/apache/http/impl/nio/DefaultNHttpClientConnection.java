/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.impl.nio.codecs.HttpRequestWriter;
import org.apache.http.impl.nio.codecs.HttpResponseParser;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpMessageParser;
import org.apache.http.nio.NHttpMessageWriter;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpParams;

public class DefaultNHttpClientConnection 
    extends NHttpConnectionBase implements NHttpClientIOTarget {

    private final NHttpMessageParser responseParser;
    private final NHttpMessageWriter requestWriter;
    
    public DefaultNHttpClientConnection(
            final IOSession session,
            final HttpResponseFactory responseFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(session, allocator, params);
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        this.responseParser = createResponseParser(this.inbuf, responseFactory, params);
        this.requestWriter = createRequestWriter(this.outbuf, params);
        this.hasBufferedInput = false;
        this.hasBufferedOutput = false;
        this.session.setBufferStatus(this);
    }
    
    protected NHttpMessageParser createResponseParser(
            final SessionInputBuffer buffer,
            final HttpResponseFactory responseFactory,
            final HttpParams params) {
        // override in derived class to specify a line parser
        return new HttpResponseParser(buffer, null, responseFactory, params);
    }

    protected NHttpMessageWriter createRequestWriter(
            final SessionOutputBuffer buffer,
            final HttpParams params) {
        // override in derived class to specify a line formatter
        return new HttpRequestWriter(buffer, null, params);
    }
    
    public void resetInput() {
        this.response = null;
        this.contentDecoder = null;
        this.responseParser.reset();
    }
    
    public void resetOutput() {
        this.request = null;
        this.contentEncoder = null;
        this.requestWriter.reset();
    }
    
    public void consumeInput(final NHttpClientHandler handler) {
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
                    this.response = (HttpResponse) this.responseParser.parse();
                } while (bytesRead > 0 && this.response == null);
                if (this.response != null) {
                    if (this.response.getStatusLine().getStatusCode() >= 200) {
                        HttpEntity entity = prepareDecoder(this.response);
                        this.response.setEntity(entity);
                        this.connMetrics.incrementRequestCount();
                    }
                    handler.responseReceived(this);
                    if (this.contentDecoder == null) {
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
                    // Response entity received
                    // Ready to receive a new response
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

    public void produceOutput(final NHttpClientHandler handler) {

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
                        return;
                    }
                    
                    this.session.clearEvent(EventMask.WRITE);
                    
                    handler.requestReady(this);
                }
            }
        } catch (IOException ex) {
            handler.exception(this, ex);
        } finally {
            // Finally set buffered output flag
            this.hasBufferedOutput = this.outbuf.hasData();
        }
    }
    
    public void submitRequest(final HttpRequest request) throws IOException, HttpException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertNotClosed();
        if (this.request != null) {
            throw new HttpException("Request already submitted");
        }
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

    public boolean isRequestSubmitted() {
        return this.request != null;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[");
        if (isOpen()) {
            buffer.append(this.session.getRemoteAddress());
        } else {
            buffer.append("closed");
        }
        buffer.append("]");
        return buffer.toString();
    }
    
}
