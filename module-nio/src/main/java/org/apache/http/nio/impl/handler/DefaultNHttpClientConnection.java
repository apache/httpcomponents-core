/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.impl.handler;

import java.io.IOException;
import java.util.Iterator;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BufferedHeader;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.impl.codecs.HttpResponseParser;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.params.HttpParams;

public class DefaultNHttpClientConnection 
    extends NHttpConnectionBase implements NHttpClientConnection {

    private HttpResponseParser responseParser;
    
    public DefaultNHttpClientConnection(
            final IOSession session,
            final HttpResponseFactory responseFactory,
            final HttpParams params) {
        super(session, params);
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        this.responseParser = new HttpResponseParser(this.inbuf, responseFactory);
    }

    private void resetInput() {
        this.response = null;
        this.contentDecoder = null;
        this.responseParser.reset();
    }
    
    private void resetOutput() {
        this.request = null;
        this.contentEncoder = null;
    }
    
    public void consumeInput(final NHttpClientHandler handler) {
        if (this.closed) {
            this.session.clearEvent(EventMask.READ);
            return;
        }
        try {
            if (this.response == null) {
                int bytesRead = this.responseParser.fillBuffer(this.session.channel());
                this.response = (HttpResponse) this.responseParser.parse(); 
                if (this.response != null) {
                    handler.responseReceived(this);
                    
                    if (this.response.getStatusLine().getStatusCode() >= 200) {
                        HttpEntity entity = prepareDecoder(this.response);
                        this.response.setEntity(entity);
                    } else {
                        // Discard the intermediate response
                        this.response = null;
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
            handler.exception(this, ex);
        }
    }

    public void produceOutput(final NHttpClientHandler handler) {
        try {
            if (this.outbuf.hasData()) {
                this.outbuf.flush(this.session.channel());
            }
            if (!this.outbuf.hasData()) {
                if (this.closed) {
                    this.session.close();
                } else {
                    if (this.contentEncoder != null) {
                        handler.outputReady(this, this.contentEncoder);
                        if (this.contentEncoder.isCompleted()) {
                            resetOutput();
                        }
                    }
                }
                if (this.contentEncoder == null) {
                    this.session.clearEvent(EventMask.WRITE);
                }
            }
        } catch (IOException ex) {
            handler.exception(this, ex);
        }
    }
    
    public void submitRequest(final HttpRequest request) throws HttpException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (this.request != null) {
            throw new HttpException("Request already submitted");
        }
        this.lineBuffer.clear();
        BasicRequestLine.format(this.lineBuffer, request.getRequestLine());
        this.outbuf.writeLine(this.lineBuffer);
        for (Iterator it = request.headerIterator(); it.hasNext(); ) {
            Header header = (Header) it.next();
            if (header instanceof BufferedHeader) {
                // If the header is backed by a buffer, re-use the buffer
                this.outbuf.writeLine(((BufferedHeader)header).getBuffer());
            } else {
                this.lineBuffer.clear();
                BasicHeader.format(this.lineBuffer, header);
                this.outbuf.writeLine(this.lineBuffer);
            }
        }
        this.lineBuffer.clear();
        this.outbuf.writeLine(this.lineBuffer);

        if (request instanceof HttpEntityEnclosingRequest) {
            prepareEncoder(request);
        }
        this.request = request;
        this.session.setEvent(EventMask.WRITE);
    }

    public boolean isRequestSubmitted() {
        return this.request != null;
    }

}
