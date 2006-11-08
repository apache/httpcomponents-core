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

package org.apache.http.impl;

import java.io.IOException;
import java.util.Iterator;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.RequestLine;
import org.apache.http.impl.entity.EntityDeserializer;
import org.apache.http.impl.entity.EntitySerializer;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.io.CharArrayBuffer;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.message.BufferedHeader;
import org.apache.http.params.HttpParams;
import org.apache.http.util.HeaderUtils;

/**
 * Abstract server-side HTTP connection capable of transmitting and receiving data
 * using arbitrary {@link HttpDataReceiver} and {@link HttpDataTransmitter}
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public abstract class AbstractHttpServerConnection implements HttpServerConnection {

    private int maxHeaderCount = -1;
    
    private final CharArrayBuffer buffer; 
    private final EntitySerializer entityserializer;
    private final EntityDeserializer entitydeserializer;
    
    /*
     * Dependent interfaces
     */
    private HttpRequestFactory requestfactory = null; 
    private HttpDataReceiver datareceiver = null;
    private HttpDataTransmitter datatransmitter = null;

    public AbstractHttpServerConnection() {
        super();
        this.buffer = new CharArrayBuffer(128);
        this.entityserializer = new EntitySerializer(
                new StrictContentLengthStrategy());
        this.entitydeserializer = new EntityDeserializer(
                new LaxContentLengthStrategy());
    }
    
    protected abstract void assertOpen() throws IllegalStateException;

    protected void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }
    
    protected void setRequestFactory(final HttpRequestFactory requestfactory) {
        if (requestfactory == null) {
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.requestfactory = requestfactory;
    }

    protected void setHttpDataReceiver(final HttpDataReceiver datareceiver) {
        if (datareceiver == null) {
            throw new IllegalArgumentException("HTTP data receiver may not be null");
        }
        this.datareceiver = datareceiver;
    }

    protected void setHttpDataTransmitter(final HttpDataTransmitter datatransmitter) {
        if (datatransmitter == null) {
            throw new IllegalArgumentException("HTTP data transmitter may not be null");
        }
        this.datatransmitter = datatransmitter;
    }

    public HttpRequest receiveRequestHeader(final HttpParams params) 
            throws HttpException, IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertOpen();
        HttpRequest request = receiveRequestLine(params);
        receiveRequestHeaders(request);
        return request;
    }
    
    public void receiveRequestEntity(final HttpEntityEnclosingRequest request) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertOpen();
        HttpEntity entity = this.entitydeserializer.deserialize(this.datareceiver, request);
        request.setEntity(entity);
    }

    protected HttpRequest receiveRequestLine(final HttpParams params)
            throws HttpException, IOException {
        this.buffer.clear();
        int i = this.datareceiver.readLine(this.buffer);
        if (i == -1) {
            throw new ConnectionClosedException("Client closed connection"); 
        }
        RequestLine requestline = BasicRequestLine.parse(this.buffer, 0, this.buffer.length());
        HttpRequest request = this.requestfactory.newHttpRequest(requestline);
        request.getParams().setDefaults(params);
        return request;
    }
    
    protected void receiveRequestHeaders(final HttpRequest request) 
            throws HttpException, IOException {
        Header[] headers = HeaderUtils.parseHeaders(this.datareceiver, this.maxHeaderCount);
        request.setHeaders(headers);
    }

    protected void doFlush() throws IOException  {
        this.datatransmitter.flush();
    }
    
    public void flush() throws IOException {
        assertOpen();
        doFlush();
    }
    
	public void sendResponseHeader(final HttpResponse response) 
            throws HttpException, IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        assertOpen();
        sendResponseStatusLine(response);
        sendResponseHeaders(response);
    }

    public void sendResponseEntity(final HttpResponse response) 
            throws HttpException, IOException {
        if (response.getEntity() == null) {
            return;
        }
        this.entityserializer.serialize(
                this.datatransmitter,
                response,
                response.getEntity());
    }
    
    protected void sendResponseStatusLine(final HttpResponse response) 
            throws HttpException, IOException {
        this.buffer.clear();
        BasicStatusLine.format(this.buffer, response.getStatusLine());
        this.datatransmitter.writeLine(this.buffer);
    }

    protected void sendResponseHeaders(final HttpResponse response) 
            throws HttpException, IOException {
        for (Iterator it = response.headerIterator(); it.hasNext(); ) {
            Header header = (Header) it.next();
            if (header instanceof BufferedHeader) {
                // If the header is backed by a buffer, re-use the buffer
                this.datatransmitter.writeLine(((BufferedHeader)header).getBuffer());
            } else {
                this.buffer.clear();
                BasicHeader.format(this.buffer, header);
                this.datatransmitter.writeLine(this.buffer);
            }
        }
        this.buffer.clear();
        this.datatransmitter.writeLine(this.buffer);
    }
        
    public boolean isStale() {
        assertOpen();
        try {
            this.datareceiver.isDataAvailable(1);
            return false;
        } catch (IOException ex) {
            return true;
        }
    }
    
}
