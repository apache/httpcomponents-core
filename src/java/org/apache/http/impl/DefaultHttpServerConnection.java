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
import java.net.Socket;

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
import org.apache.http.StatusLine;
import org.apache.http.entity.EntityDeserializer;
import org.apache.http.entity.EntitySerializer;
import org.apache.http.impl.entity.DefaultEntityDeserializer;
import org.apache.http.impl.entity.DefaultEntitySerializer;
import org.apache.http.io.CharArrayBuffer;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.HeaderUtils;

/**
 * Default implementation of a server-side HTTP connection.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultHttpServerConnection 
        extends AbstractHttpConnection implements HttpServerConnection {

    private int maxHeaderCount = -1;
    
    private final CharArrayBuffer buffer; 
    
    /*
     * Dependent interfaces
     */
    private HttpRequestFactory requestfactory = null; 
    private EntitySerializer entityserializer = null;
    private EntityDeserializer entitydeserializer = null;

    public DefaultHttpServerConnection() {
        super();
        this.requestfactory = new DefaultHttpRequestFactory();
        this.buffer = new CharArrayBuffer(128);
        this.entityserializer = new DefaultEntitySerializer();
        this.entitydeserializer = new DefaultEntityDeserializer();
    }
    
    public void setRequestFactory(final HttpRequestFactory requestfactory) {
        if (requestfactory == null) {
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.requestfactory = requestfactory;
    }

    public void setEntityDeserializer(final EntityDeserializer entitydeserializer) {
        if (entitydeserializer == null) {
            throw new IllegalArgumentException("Entity deserializer may not be null");
        }
        this.entitydeserializer = entitydeserializer;
    }

    public void setEntitySerializer(final EntitySerializer entityserializer) {
        if (entityserializer == null) {
            throw new IllegalArgumentException("Entity serializer may not be null");
        }
        this.entityserializer = entityserializer;
    }

    public void bind(final Socket socket, final HttpParams params) throws IOException {
        super.bind(socket, params);
        this.maxHeaderCount = params.getIntParameter(HttpConnectionParams.MAX_HEADER_COUNT, -1);
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
        RequestLine requestline = RequestLine.parse(this.buffer, 0, this.buffer.length());
        HttpRequest request = this.requestfactory.newHttpRequest(requestline);
        request.getParams().setDefaults(params);
        return request;
    }
    
    protected void receiveRequestHeaders(final HttpRequest request) 
            throws HttpException, IOException {
        Header[] headers = HeaderUtils.parseHeaders(this.datareceiver, this.maxHeaderCount);
        for (int i = 0; i < headers.length; i++) {
            request.addHeader(headers[i]);
        }
    }

    public void flush() throws IOException {
        assertOpen();
        this.datatransmitter.flush();
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
        StatusLine.format(this.buffer, response.getStatusLine());
        this.datatransmitter.writeLine(this.buffer);
    }

    protected void sendResponseHeaders(final HttpResponse response) 
            throws HttpException, IOException {
        Header[] headers = response.getAllHeaders();
        for (int i = 0; i < headers.length; i++) {
            this.buffer.clear();
            Header.format(this.buffer, headers[i]);
            this.datatransmitter.writeLine(this.buffer);
        }
        this.buffer.clear();
        this.datatransmitter.writeLine(this.buffer);
    }
        
}
