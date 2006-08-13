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

package org.apache.http.protocol;

import java.io.IOException;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.params.HttpParams;

/**
 * Minimalistic server-side implementation of an HTTP processor.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class HttpService extends AbstractHttpProcessor {

    private final HttpServerConnection conn;
    private final HttpContext context;

    private volatile boolean destroyed = false;
    
    private HttpParams params = null;
    private ConnectionReuseStrategy connStrategy = null;
    private HttpResponseFactory responseFactory = null;

    public HttpService(final HttpServerConnection conn) {
        this(conn, null);
    }

    public HttpService(final HttpServerConnection conn, final HttpContext parentContext) {
        super();
        if (conn == null) {
            throw new IllegalArgumentException("HTTP server connection may not be null");
        }
        this.conn = conn;
        this.context = new HttpExecutionContext(parentContext);
        this.connStrategy = new DefaultConnectionReuseStrategy();
        this.responseFactory = new DefaultHttpResponseFactory();
    }
    
    protected void setConnReuseStrategy(final ConnectionReuseStrategy connStrategy) {
        if (connStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        this.connStrategy = connStrategy;
    }

    protected void setResponseFactory(final HttpResponseFactory responseFactory) {
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        this.responseFactory = responseFactory;
    }
    
    public HttpContext getContext() {
        return this.context;
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
    
    protected void closeConnection() {
        try {
            this.conn.close();
        } catch (IOException ex) {
            logIOException(ex);
        }
    }
            
    public void handleRequest() { 
        this.context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, this.conn);
        HttpResponse response;
        try {
            HttpRequest request = this.conn.receiveRequestHeader(this.params);
            HttpVersion ver = request.getRequestLine().getHttpVersion();
            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                // Downgrade protocol version if greater than HTTP/1.1 
                ver = HttpVersion.HTTP_1_1;
            }

            response = this.responseFactory.newHttpResponse(ver, HttpStatus.SC_OK);
            response.getParams().setDefaults(this.params);
            
            if (request instanceof HttpEntityEnclosingRequest) {
                if (((HttpEntityEnclosingRequest) request).expectContinue()) {

                    logMessage("Expected 100 (Continue)");
                    
                    HttpResponse ack = this.responseFactory.newHttpResponse(ver, HttpStatus.SC_CONTINUE);
                    ack.getParams().setDefaults(this.params);
                    this.conn.sendResponseHeader(ack);
                    this.conn.flush();
                }
                this.conn.receiveRequestEntity((HttpEntityEnclosingRequest) request);
            }
            preprocessRequest(request, this.context);
            logMessage("Request received");

            this.context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
            this.context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
            doService(request, response);
            
            if (request instanceof HttpEntityEnclosingRequest) {
                // Make sure the request content is fully consumed
                HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
                if (entity != null) {
                    entity.consumeContent();
                }
            }
        } catch (ConnectionClosedException ex) {
            logMessage("Client closed connection");
            closeConnection();
            return;
        } catch (HttpException ex) {
            response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0, 
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.getParams().setDefaults(this.params);
            handleException(ex, response);
        } catch (IOException ex) {
            logIOException(ex);
            closeConnection();
            return;
        }
        try {
            postprocessResponse(response, this.context);
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
        if (!this.connStrategy.keepAlive(conn, response)) {
            closeConnection();
        } else {
            logMessage("Connection kept alive");
        }
    }
    
    protected void handleException(final HttpException ex, final HttpResponse response) {
        if (ex instanceof MethodNotSupportedException) {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else if (ex instanceof UnsupportedHttpVersionException) {
            response.setStatusCode(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED);
        } else if (ex instanceof ProtocolException) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        } else {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    protected void doService(final HttpRequest request, final HttpResponse response) 
            throws HttpException, IOException {
        response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
    }
    
    protected void logMessage(final String s) {
    }
    
    protected void logIOException(final IOException ex) {
    }
    
    protected void logProtocolException(final HttpException ex) {
    }
    
    public void destroy() {
        this.destroyed = true;
        try {
            this.conn.shutdown();
        } catch (IOException ex) {
            logIOException(ex);
        }
    }
    
    public boolean isDestroyed() {
        return this.destroyed;
    }
               
}
