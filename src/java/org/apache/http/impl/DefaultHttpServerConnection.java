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
import org.apache.http.HttpException;
import org.apache.http.HttpMutableEntityEnclosingRequest;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.entity.EntityGenerator;
import org.apache.http.entity.EntityWriter;
import org.apache.http.impl.entity.DefaultEntityGenerator;
import org.apache.http.impl.entity.DefaultServerEntityWriter;
import org.apache.http.io.CharArrayBuffer;
import org.apache.http.params.HttpParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultHttpServerConnection 
        extends AbstractHttpConnection implements HttpServerConnection {

    private final CharArrayBuffer buffer; 
    
    /*
     * Dependent interfaces
     */
    private HttpRequestFactory requestfactory = null; 
    private EntityGenerator entitygen = null;
    private EntityWriter entitywriter = null;

    public DefaultHttpServerConnection() {
        super();
        this.requestfactory = new DefaultHttpRequestFactory();
        this.buffer = new CharArrayBuffer(128);
        this.entitygen = new DefaultEntityGenerator();
        this.entitywriter = new DefaultServerEntityWriter();
    }
    
    public void setRequestFactory(final HttpRequestFactory requestfactory) {
        if (requestfactory == null) {
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.requestfactory = requestfactory;
    }

    public void setEntityGenerator(final EntityGenerator entitygen) {
        if (entitygen == null) {
            throw new IllegalArgumentException("Entity generator may not be null");
        }
        this.entitygen = entitygen;
    }

    public void setEntityWriter(final EntityWriter entitywriter) {
        if (entitywriter == null) {
            throw new IllegalArgumentException("Entity writer may not be null");
        }
        this.entitywriter = entitywriter;
    }

    public void bind(final Socket socket, final HttpParams params) throws IOException {
        super.bind(socket, params);
    }

    public HttpMutableRequest receiveRequestHeader(final HttpParams params) 
            throws HttpException, IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertOpen();
        HttpMutableRequest request = receiveRequestLine(params);
        receiveRequestHeaders(request);
        return request;
    }
    
    public void receiveRequestEntity(final HttpMutableEntityEnclosingRequest request) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertOpen();
        HttpEntity entity = this.entitygen.generate(this.datareceiver, request);
        request.setEntity(entity);
    }

    protected HttpMutableRequest receiveRequestLine(final HttpParams params)
            throws HttpException, IOException {
        this.buffer.clear();
        int i = this.datareceiver.readLine(this.buffer);
        if (i == -1) {
            throw new ConnectionClosedException("Client closed connection"); 
        }
        RequestLine requestline = RequestLine.parse(this.buffer, 0, this.buffer.length());
        HttpMutableRequest request = this.requestfactory.newHttpRequest(requestline);
        request.getParams().setDefaults(params);
        return request;
    }
    
    protected void receiveRequestHeaders(final HttpMutableRequest request) 
            throws HttpException, IOException {
        Header[] headers = Header.parseAll(this.datareceiver);
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
        this.entitywriter.write(
                response.getEntity(),
                response.getStatusLine().getHttpVersion(),
                this.datatransmitter);
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
