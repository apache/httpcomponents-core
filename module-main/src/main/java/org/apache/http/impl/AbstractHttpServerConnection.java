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

package org.apache.http.impl;

import java.io.IOException;
import java.util.Iterator;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpConnectionMetrics;
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
import org.apache.http.impl.io.AbstractMessageParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.message.BufferedHeader;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.CharArrayBuffer;

/**
 * Abstract server-side HTTP connection capable of transmitting and receiving data
 * using arbitrary {@link SessionInputBuffer} and {@link SessionOutputBuffer}
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public abstract class AbstractHttpServerConnection implements HttpServerConnection {

    private final CharArrayBuffer buffer; 
    private final EntitySerializer entityserializer;
    private final EntityDeserializer entitydeserializer;
    private final HttpRequestFactory requestfactory; 
    
    private SessionInputBuffer inbuffer = null;
    private SessionOutputBuffer outbuffer = null;

    private int maxHeaderCount = -1;
    private int maxLineLen = -1;
    
    private HttpConnectionMetricsImpl metrics;
    
    public AbstractHttpServerConnection() {
        super();
        this.buffer = new CharArrayBuffer(128);
        this.entityserializer = createEntitySerializer();
        this.entitydeserializer = createEntityDeserializer();
        this.requestfactory = createHttpRequestFactory();
    }
    
    protected abstract void assertOpen() throws IllegalStateException;

    protected EntityDeserializer createEntityDeserializer() {
        return new EntityDeserializer(new LaxContentLengthStrategy());
    }

    protected EntitySerializer createEntitySerializer() {
        return new EntitySerializer(new StrictContentLengthStrategy());
    }

    protected HttpRequestFactory createHttpRequestFactory() {
        return new DefaultHttpRequestFactory();
    }

    protected void init(
            final SessionInputBuffer inbuffer,
            final SessionOutputBuffer outbuffer,
            final HttpParams params) {
        if (inbuffer == null) {
            throw new IllegalArgumentException("Input session buffer may not be null");
        }
        if (outbuffer == null) {
            throw new IllegalArgumentException("Output session buffer may not be null");
        }
        this.inbuffer = inbuffer;
        this.outbuffer = outbuffer;
        this.maxHeaderCount = params.getIntParameter(
                HttpConnectionParams.MAX_HEADER_COUNT, -1);
        this.maxLineLen = params.getIntParameter(
                HttpConnectionParams.MAX_LINE_LENGTH, -1);
        this.metrics = new HttpConnectionMetricsImpl(
                inbuffer.getMetrics(),
                outbuffer.getMetrics());
    }
    
    public HttpRequest receiveRequestHeader() 
            throws HttpException, IOException {
        assertOpen();
        HttpRequest request = receiveRequestLine();
        receiveRequestHeaders(request);
        this.metrics.incrementRequestCount();
        return request;
    }
    
    public void receiveRequestEntity(final HttpEntityEnclosingRequest request) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertOpen();
        HttpEntity entity = this.entitydeserializer.deserialize(this.inbuffer, request);
        request.setEntity(entity);
    }

    protected HttpRequest receiveRequestLine()
            throws HttpException, IOException {
        this.buffer.clear();
        int i = this.inbuffer.readLine(this.buffer);
        if (i == -1) {
            throw new ConnectionClosedException("Client closed connection"); 
        }
        RequestLine requestline = BasicRequestLine.parse(this.buffer, 0, this.buffer.length());
        return this.requestfactory.newHttpRequest(requestline);
    }
    
    protected void receiveRequestHeaders(final HttpRequest request) 
            throws HttpException, IOException {
        Header[] headers = AbstractMessageParser.parseHeaders(
                this.inbuffer, 
                this.maxHeaderCount,
                this.maxLineLen);
        request.setHeaders(headers);
    }

    protected void doFlush() throws IOException  {
        this.outbuffer.flush();
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
        if (response.getStatusLine().getStatusCode() >= 200) {
            this.metrics.incrementResponseCount();
        }
    }

    public void sendResponseEntity(final HttpResponse response) 
            throws HttpException, IOException {
        if (response.getEntity() == null) {
            return;
        }
        this.entityserializer.serialize(
                this.outbuffer,
                response,
                response.getEntity());
    }
    
    protected void sendResponseStatusLine(final HttpResponse response) 
            throws HttpException, IOException {
        this.buffer.clear();
        BasicStatusLine.format(this.buffer, response.getStatusLine());
        this.outbuffer.writeLine(this.buffer);
    }

    protected void sendResponseHeaders(final HttpResponse response) 
            throws HttpException, IOException {
        for (Iterator it = response.headerIterator(); it.hasNext(); ) {
            Header header = (Header) it.next();
            if (header instanceof BufferedHeader) {
                // If the header is backed by a buffer, re-use the buffer
                this.outbuffer.writeLine(((BufferedHeader)header).getBuffer());
            } else {
                this.buffer.clear();
                BasicHeader.format(this.buffer, header);
                this.outbuffer.writeLine(this.buffer);
            }
        }
        this.buffer.clear();
        this.outbuffer.writeLine(this.buffer);
    }
        
    public boolean isStale() {
        assertOpen();
        try {
            this.inbuffer.isDataAvailable(1);
            return false;
        } catch (IOException ex) {
            return true;
        }
    }
    
    public HttpConnectionMetrics getMetrics() {
        return this.metrics;
    }

}
