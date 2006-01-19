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

package org.apache.http.protocol;

import java.io.IOException;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableEntityEnclosingRequest;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class HttpService extends AbstractHttpProcessor {

    private final HttpServerConnection conn;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpContext context;
    
    private HttpParams params = null;

    public HttpService(final HttpServerConnection conn) {
        super();
        if (conn == null) {
            throw new IllegalArgumentException("HTTP server connection may not be null");
        }
        this.conn = conn;
        this.connStrategy = new DefaultConnectionReuseStrategy();
        this.context = new HttpExecutionContext(null);
    }

    public HttpParams getParams() {
        return this.params;
    }
    
    public void setParams(final HttpParams params) {
        this.params = params;
    }
    
    public boolean isActive() {
        return this.conn.isOpen();
    }
    
    private void closeConnection() {
        try {
            this.conn.close();
        } catch (IOException ex) {
            logIOException(ex);
        }
    }
            
    public void handleRequest() { 
        this.context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, this.conn);
        BasicHttpResponse response = new BasicHttpResponse();
        response.getParams().setDefaults(this.params);
        try {
            HttpMutableRequest request = this.conn.receiveRequestHeader(this.params);
            if (request instanceof HttpEntityEnclosingRequest) {
                if (((HttpMutableEntityEnclosingRequest) request).expectContinue()) {

                    logMessage("Expected 100 (Continue)");
                    
                    BasicHttpResponse ack = new BasicHttpResponse();
                    ack.getParams().setDefaults(this.params);
                    ack.setStatusCode(HttpStatus.SC_CONTINUE);
                    this.conn.sendResponseHeader(ack);
                    this.conn.flush();
                }
                this.conn.receiveRequestEntity((HttpMutableEntityEnclosingRequest) request);
            }
            preprocessRequest(request, this.context);
            logMessage("Request received");
            
            this.context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
            this.context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
            doService(request, response);
            
            if (request instanceof HttpEntityEnclosingRequest) {
                // Make sure the request content is fully consumed
                HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
                if (entity != null && entity.getContent() != null) {
                    entity.getContent().close();
                }
            }
            
            postprocessResponse(response, this.context);
        } catch (ConnectionClosedException ex) {
            logMessage("Client closed connection");
            closeConnection();
            return;
        } catch (HttpException ex) {
            handleException(ex, response);
        } catch (IOException ex) {
            logIOException(ex);
            closeConnection();
            return;
        }
        try {
            this.conn.sendResponseHeader(response);
            this.conn.sendResponseEntity(response);
            this.conn.flush();
            logMessage("Response sent");
        } catch (HttpException ex) {
            logProtocolException(ex);
            closeConnection();
            return;
        } catch (IOException ex) {
            logIOException(ex);
            closeConnection();
            return;
        }
        if (!this.connStrategy.keepAlive(response)) {
            closeConnection();
        } else {
            logMessage("Connection kept alive");
        }
    }
    
    protected void handleException(final HttpException ex, final HttpMutableResponse response) {
        if (ex instanceof MethodNotSupportedException) {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else if (ex instanceof ProtocolException) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        } else {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    protected void doService(final HttpRequest request, final HttpMutableResponse response) 
            throws HttpException, IOException {
        HttpVersion ver = request.getRequestLine().getHttpVersion();
        if (ver.lessEquals(HttpVersion.HTTP_1_1)) {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else {
            response.setStatusLine(new StatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_IMPLEMENTED));
        }
    }
    
    protected void logMessage(final String s) {
    }
    
    protected void logIOException(final IOException ex) {
    }
    
    protected void logProtocolException(final HttpException ex) {
    }
}
