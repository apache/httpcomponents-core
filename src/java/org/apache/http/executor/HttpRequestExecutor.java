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

package org.apache.http.executor;

import java.io.IOException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpContext;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.interceptor.AbstractHttpProcessor;
import org.apache.http.params.HttpConnectionParams;
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
public class HttpRequestExecutor extends AbstractHttpProcessor {

    private HttpParams params = null;
    private HttpRequestRetryHandler retryhandler = null;
    
    public HttpRequestExecutor(final HttpContext parentContext) {
        super(new HttpExecutionContext(parentContext));
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
    
    public HttpResponse execute(final HttpRequest request, final HttpClientConnection conn) 
            throws IOException, HttpException {
        
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("Client connection may not be null");
        }
        HttpContext localContext = getContext();
        localContext.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
        localContext.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        localContext.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, 
        		conn.getTargetHost());

        // Link own parameters as defaults 
        request.getParams().setDefaults(this.params);
        
        if (request instanceof HttpMutableRequest) {
            preprocessRequest((HttpMutableRequest)request);
        }
        
        HttpResponse response = null;
        
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
                localContext.setAttribute(HttpExecutionContext.HTTP_REQ_SENT, 
                        new Boolean(false)); 
                response = conn.sendRequest(request);
                localContext.setAttribute(HttpExecutionContext.HTTP_REQ_SENT, 
                        new Boolean(true)); 
                // Request may be terminated prematurely, if the expect-continue 
                // protocol is used
                if (response == null) {
                    // No error response so far. 
                    response = conn.receiveResponse(request);
                }
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
        
        // Link own parameters as defaults 
        response.getParams().setDefaults(this.params);
        
        if (response instanceof HttpMutableResponse) {
            postprocessResponse((HttpMutableResponse)response);
        }
        return response;
    }
}
