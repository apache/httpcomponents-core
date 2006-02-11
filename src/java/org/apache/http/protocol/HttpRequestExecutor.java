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
import java.net.ProtocolException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Sends HTTP requests and receives the responses.
 * Takes care of request preprocessing and response postprocessing
 * by the respective interceptors.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class HttpRequestExecutor extends AbstractHttpProcessor {

    private static final int WAIT_FOR_CONTINUE_MS = 10000;
    
    private final HttpContext context;
    
    private HttpParams params = null;
    private HttpRequestRetryHandler retryhandler = null;
    
    public HttpRequestExecutor(final HttpContext parentContext) {
        super();
        this.context = new HttpExecutionContext(parentContext);
    }
    
    public HttpRequestExecutor() {
        this(null);
    }
    
    public HttpParams getParams() {
        return this.params;
    }

    public void setParams(final HttpParams params) {
        this.params = params;
    }
    
    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {
        if ("HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        int status = response.getStatusLine().getStatusCode(); 
        return status >= HttpStatus.SC_OK 
            && status != HttpStatus.SC_NO_CONTENT 
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT; 
    }

    private HttpMutableResponse doExecute(
            final HttpRequest request, final HttpClientConnection conn)
                throws IOException, HttpException {
        HttpMutableResponse response = null;
        this.context.setAttribute(HttpExecutionContext.HTTP_REQ_SENT, 
                new Boolean(false));
        // Send request header
        conn.sendRequestHeader(request);
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpVersion ver = request.getRequestLine().getHttpVersion();
            if (ver.greaterEquals(HttpVersion.HTTP_1_1) 
                    && ((HttpEntityEnclosingRequest)request).expectContinue()) {
                // Flush headers
                conn.flush();
                if (conn.isResponseAvailable(WAIT_FOR_CONTINUE_MS)) {
                    response = conn.receiveResponseHeader(this.params);
                    if (canResponseHaveBody(request, response)) {
                        conn.receiveResponseEntity(response);
                    }
                    int status = response.getStatusLine().getStatusCode();
                    if (status < 200) {
                        if (status != HttpStatus.SC_CONTINUE) {
                            throw new ProtocolException("Unexpected response: " + 
                                    response.getStatusLine());
                        }
                    } else {
                        return response;
                    }                    
                }
            }
            conn.sendRequestEntity((HttpEntityEnclosingRequest) request);
        }
        conn.flush();
        
        this.context.setAttribute(HttpExecutionContext.HTTP_REQ_SENT, 
                new Boolean(true)); 
        for (;;) {
            // Loop until non 1xx resposne is received
            response = conn.receiveResponseHeader(this.params);
            if (canResponseHaveBody(request, response)) {
                conn.receiveResponseEntity(response);
            }
            int statuscode = response.getStatusLine().getStatusCode();
            if (statuscode >= HttpStatus.SC_OK) {
                break;
            }
        }
        return response;
    }
    
    public HttpResponse execute(final HttpRequest request, final HttpClientConnection conn) 
            throws IOException, HttpException {
        
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("Client connection may not be null");
        }
        this.context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
        this.context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        this.context.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, 
        		conn.getTargetHost());

        // Link own parameters as defaults 
        request.getParams().setDefaults(this.params);
        
        if (request instanceof HttpMutableRequest) {
            preprocessRequest((HttpMutableRequest)request, this.context);
        }
        
        HttpMutableResponse response = null;
        // loop until the method is successfully processed, the retryHandler 
        // returns false or a non-recoverable exception is thrown
        for (int execCount = 0; ; execCount++) {
            try {
                if (HttpConnectionParams.isStaleCheckingEnabled(this.params)) {
                    if (conn.isOpen() && conn.isStale()) {
                        conn.close();
                    }
                }
                if (!conn.isOpen()) {
                    conn.open(this.params);
                    // TODO: Implement secure tunnelling
                }
                response = doExecute(request, conn);
                // exit retry loop
                break;
            } catch (IOException ex) {
                conn.close();
                if (this.retryhandler == null) {
                    throw ex;
                }
                if (!this.retryhandler.retryRequest(ex, execCount, null)) {
                    throw ex;
                }
            } catch (HttpException ex) {
                conn.close();
                throw ex;
            } catch (RuntimeException ex) {
                conn.close();
                throw ex;
            }
        }
        postprocessResponse(response, this.context);
        return response;
    }

    public HttpRequestRetryHandler getRetryHandler() {
        return this.retryhandler;
    }

    public void setRetryHandler(final HttpRequestRetryHandler retryhandler) {
        this.retryhandler = retryhandler;
    }
    
}
