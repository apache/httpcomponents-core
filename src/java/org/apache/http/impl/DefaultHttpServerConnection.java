/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableEntity;
import org.apache.http.HttpMutableEntityEnclosingRequest;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.RequestLine;
import org.apache.http.impl.entity.DefaultEntityGenerator;
import org.apache.http.impl.entity.DefaultEntityWriter;
import org.apache.http.impl.entity.EntityGenerator;
import org.apache.http.impl.entity.EntityWriter;
import org.apache.http.params.HttpParams;
import org.apache.http.util.HeadersParser;

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

    /*
     * Dependent interfaces
     */
    private HttpRequestFactory requestfactory = null; 

    public DefaultHttpServerConnection() {
        super();
        this.requestfactory = new DefaultHttpRequestFactory();
    }
    
    public void setRequestFactory(final HttpRequestFactory requestfactory) {
        if (requestfactory == null) {
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.requestfactory = requestfactory;
    }

    public void bind(final Socket socket, final HttpParams params) throws IOException {
        super.bind(socket, params);
    }

    public HttpRequest receiveRequest(final HttpParams params) 
            throws HttpException, IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertOpen();
        // reset the data transmitter
        this.datareceiver.reset(params);

        HttpMutableRequest request = receiveRequestLine(params);
        receiveRequestHeaders(request);

        if (request instanceof HttpMutableEntityEnclosingRequest) {
        	if (((HttpMutableEntityEnclosingRequest) request).expectContinue()) {
            	// return and let the caller validate the request
                return request;
        	}
            receiveRequestBody((HttpMutableEntityEnclosingRequest) request);
        }
        return request;
    }
    
    protected HttpMutableRequest receiveRequestLine(final HttpParams params)
            throws HttpException, IOException {
        String line = this.datareceiver.readLine();
        if (line == null) {
            throw new ConnectionClosedException("Client closed connection"); 
        }
        RequestLine requestline = RequestLine.parse(line);
        if (isWirelogEnabled()) {
            wirelog(">> " + line + "[\\r][\\n]");
        }
        HttpMutableRequest request = this.requestfactory.newHttpRequest(requestline);
        request.setParams((HttpParams)params.clone());
        return request;
    }
    
    protected void receiveRequestHeaders(final HttpMutableRequest request) 
            throws HttpException, IOException {
        Header[] headers = HeadersParser.processHeaders(this.datareceiver);
        for (int i = 0; i < headers.length; i++) {
            request.addHeader(headers[i]);
            if (isWirelogEnabled()) {
                wirelog(">> " + headers[i].toString() + "[\\r][\\n]");
            }
        }
        wirelog(">> [\\r][\\n]");
    }

    protected void receiveRequestBody(final HttpMutableEntityEnclosingRequest request)
            throws HttpException, IOException {
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpMutableEntity entity = entitygen.generate(this.datareceiver, request);
        request.setEntity(entity);
    }
    
    public void continueRequest(final HttpEntityEnclosingRequest request) 
    		throws HttpException, IOException {
    	if (request.expectContinue() && request.getEntity() == null) {
            receiveRequestBody((HttpMutableEntityEnclosingRequest) request);
    	}
	}

	public void sendResponse(final HttpResponse response) 
            throws HttpException, IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        assertOpen();

        // reset the data transmitter
        this.datatransmitter.reset(response.getParams());
        sendResponseStatusLine(response);
        sendResponseHeaders(response);
        sendResponseBody(response);
        this.datatransmitter.flush();
    }
    
    protected void sendResponseStatusLine(final HttpResponse response) 
            throws HttpException, IOException {
        String line = response.getStatusLine().toString();
        this.datatransmitter.writeLine(line);
        if (isWirelogEnabled()) {
            wirelog("<< " + line + "[\\r][\\n]");
        }
    }

    protected void sendResponseHeaders(final HttpResponse response) 
            throws HttpException, IOException {
        Header[] headers = response.getAllHeaders();
        for (int i = 0; i < headers.length; i++) {
            String line = headers[i].toString();
            this.datatransmitter.writeLine(line);
            if (isWirelogEnabled()) {
                wirelog("<< " + line + "[\\r][\\n]");
            }
        }
        this.datatransmitter.writeLine("");
        if (isWirelogEnabled()) {
            wirelog("<< [\\r][\\n]");
        }
    }

    protected void sendResponseBody(final HttpResponse response) 
            throws HttpException, IOException {
        if (response.getEntity() == null) {
            return;
        }
        EntityWriter entitywriter = new DefaultEntityWriter();
        entitywriter.write(
                response.getEntity(),
                response.getStatusLine().getHttpVersion(),
                this.datatransmitter);
    }
        
}
