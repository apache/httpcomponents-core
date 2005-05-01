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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpContext;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.impl.BasicHttpContext;
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
public class HttpRequestExecutor {

    private final HttpParams params;
    private final HttpContext localContext;
    
    private HttpRequestRetryHandler retryhandler = null;
    private Set interceptors = null; 
    
    public HttpRequestExecutor(final HttpParams params, final HttpContext parentContext) {
        super();
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.params = params;
        this.localContext = new BasicHttpContext(parentContext); 
    }
    
    private void setInterceptor(final Object obj) {
        if (obj == null) {
            return;
        }
        if (this.interceptors == null) {
            this.interceptors = new HashSet();
        }
        this.interceptors.add(obj);
    }
    
    public void removeInterceptor(final Object obj) {
        if (obj == null) {
            return;
        }
        if (this.interceptors == null) {
            return;
        }
        this.interceptors.remove(obj);
        if (this.interceptors.isEmpty()) {
            this.interceptors = null;
        }
    }
    
    public void setRequestInterceptor(final HttpRequestInterceptor interceptor) {
        setInterceptor(interceptor);
    }
    
    public void setResponseInterceptor(final HttpResponseInterceptor interceptor) {
        setInterceptor(interceptor);
    }

    public void removeRequestInterceptor(final HttpRequestInterceptor interceptor) {
        removeInterceptor(interceptor);
    }
    
    public void removeResponseInterceptor(final HttpResponseInterceptor interceptor) {
        removeInterceptor(interceptor);
    }
    
    public void removeInterceptors(final Class clazz) {
        if (clazz == null) {
            return;
        }
        if (this.interceptors == null) {
            return;
        }
        for (Iterator i = this.interceptors.iterator(); i.hasNext(); ) {
            if (clazz.isInstance(i.next())) {
                i.remove();
            }
        }
    }
    
    public HttpRequestRetryHandler getRetryHandler() {
        return this.retryhandler;
    }
    
    public void setRetryHandler(final HttpRequestRetryHandler retryhandler) {
        this.retryhandler = retryhandler;
    }
    
    private void preprocessRequest(final HttpMutableRequest request) 
            throws IOException, HttpException {
        if (this.interceptors == null) {
            return;
        }
        for (Iterator i = this.interceptors.iterator(); i.hasNext(); ) {
            Object obj = i.next();
            if (obj instanceof HttpRequestInterceptor) {
                HttpRequestInterceptor interceptor = (HttpRequestInterceptor)obj;
                interceptor.process(request, this.localContext);
            }
        }
    }

    
    
    private void postprocessResponse(final HttpMutableResponse response) 
            throws IOException, HttpException {
        if (this.interceptors == null) {
            return;
        }
        for (Iterator i = this.interceptors.iterator(); i.hasNext(); ) {
            Object obj = i.next();
            if (obj instanceof HttpResponseInterceptor) {
                HttpResponseInterceptor interceptor = (HttpResponseInterceptor)obj;
                interceptor.process(response, this.localContext);
            }
        }
    }
    
    public HttpResponse execute(final HttpRequest request, final HttpClientConnection conn) 
            throws IOException, HttpException {
        
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("Client connection may not be null");
        }
        
        this.localContext.setAttribute(HttpContext.HTTP_REQUEST, request);
        this.localContext.setAttribute(HttpContext.HTTP_CONNECTION, conn);
        this.localContext.setAttribute(HttpContext.HTTP_TARGET_HOST, conn.getHost());
        
        if (request instanceof HttpMutableRequest) {
            preprocessRequest((HttpMutableRequest)request);
        }
        
        HttpResponse response = null;
        
        // loop until the method is successfully processed, the retryHandler 
        // returns false or a non-recoverable exception is thrown
        HttpConnectionParams connparams = new HttpConnectionParams(this.params); 
        for (int execCount = 0; ; execCount++) {
            try {
                if (connparams.isStaleCheckingEnabled()) {
                    if (conn.isStale()) {
                        conn.close();
                    }
                }
                if (!conn.isOpen()) {
                    conn.open(this.params);
                    // TODO: Implement secure tunnelling
                }
                response = conn.sendRequest(request);
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
        
        if (response instanceof HttpMutableResponse) {
            postprocessResponse((HttpMutableResponse)response);
        }
        return response;
    }
}
