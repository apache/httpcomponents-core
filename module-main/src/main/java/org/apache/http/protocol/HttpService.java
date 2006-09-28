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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
import org.apache.http.params.HttpParams;

/**
 * Minimalistic server-side implementation of an HTTP processor.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class HttpService {

    private final Map handlerMap;
    
    private HttpParams params = null;
    private HttpProcessor processor = null;
    private ConnectionReuseStrategy connStrategy = null;
    private HttpResponseFactory responseFactory = null;
    
    /**
     * Create a new HTTP service.
     *
     * @param proc             the processor to use on requests and responses
     * @param connStrategy     the connection reuse strategy
     * @param responseFactory  the response factory
     */
    public HttpService(
            final HttpProcessor proc,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory) {
        super();
        this.handlerMap = new HashMap();
        setHttpProcessor(proc);
        setConnReuseStrategy(connStrategy);
        setResponseFactory(responseFactory);
    }
    
    protected void setHttpProcessor(final HttpProcessor processor) {
        if (processor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null.");
        }
        this.processor = processor;
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
    
    public HttpParams getParams() {
        return this.params;
    }
    
    public void setParams(final HttpParams params) {
        this.params = params;
    }
    
    public void registerRequestHandler(final String pattern, final HttpRequestHandler handler) {
        if (pattern == null) {
            throw new IllegalArgumentException("URI request pattern may not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("HTTP request handelr may not be null");
        }
        this.handlerMap.put(pattern, handler);
    }
    
    protected HttpRequestHandler lookupHandler(final String requestURI) {
        // direct match?
        Object handler = this.handlerMap.get(requestURI);
        if (handler == null) {
            // pattern match?
            String bestMatch = null;
            for (Iterator it = this.handlerMap.keySet().iterator(); it.hasNext();) {
                String pattern = (String) it.next();
                if (matchUriRequestPattern(pattern, requestURI)) {
                    // we have a match. is it any better?
                    if (bestMatch == null || bestMatch.length() <= pattern.length()) {
                        handler = this.handlerMap.get(pattern);
                        bestMatch = pattern;
                    }
                }
            }
        }
        return (HttpRequestHandler) handler;
    }

    protected boolean matchUriRequestPattern(final String pattern, final String requestUri) {
        if (pattern.equals("*")) {
            return true;
        } else {
            return 
            (pattern.endsWith("*") && requestUri.startsWith(pattern.substring(0, pattern.length() - 1))) ||
            (pattern.startsWith("*") && requestUri.endsWith(pattern.substring(1, pattern.length())));
        }
    }
    
    public void handleRequest(final HttpServerConnection conn, final HttpContext context) 
            throws IOException, HttpException { 
        context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        HttpResponse response;
        try {
            HttpRequest request = conn.receiveRequestHeader(this.params);
            HttpVersion ver = request.getRequestLine().getHttpVersion();
            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                // Downgrade protocol version if greater than HTTP/1.1 
                ver = HttpVersion.HTTP_1_1;
            }

            response = this.responseFactory.newHttpResponse(ver, HttpStatus.SC_OK);
            response.getParams().setDefaults(this.params);
            
            if (request instanceof HttpEntityEnclosingRequest) {
                if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                    HttpResponse ack = this.responseFactory.newHttpResponse(ver, HttpStatus.SC_CONTINUE);
                    ack.getParams().setDefaults(this.params);
                    conn.sendResponseHeader(ack);
                    conn.flush();
                }
                conn.receiveRequestEntity((HttpEntityEnclosingRequest) request);
            }
            processor.process(request, context);

            context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
            context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
            doService(request, response, context);
            
            if (request instanceof HttpEntityEnclosingRequest) {
                // Make sure the request content is fully consumed
                HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
                if (entity != null) {
                    entity.consumeContent();
                }
            }
        } catch (HttpException ex) {
            response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0, 
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.getParams().setDefaults(this.params);
            handleException(ex, response);
        }
        processor.process(response, context);
        conn.sendResponseHeader(response);
        conn.sendResponseEntity(response);
        conn.flush();
        if (!this.connStrategy.keepAlive(response, context)) {
            conn.close();
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
    
    protected void doService(
            final HttpRequest request, 
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {
        HttpRequestHandler handler = lookupHandler(request.getRequestLine().getUri());
        if (handler != null) {
            handler.handle(request, response, context);
        } else {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }
    
}
