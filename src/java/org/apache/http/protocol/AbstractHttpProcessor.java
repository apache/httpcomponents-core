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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.HttpException;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public abstract class AbstractHttpProcessor {

    private final HttpContext localContext;
    
    private Set interceptors = null; 
    
    public AbstractHttpProcessor(final HttpContext localContext) {
        super();
        this.localContext = localContext;
    }
    
    private void addInterceptor(final Object obj) {
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
    
    public void addRequestInterceptor(final HttpRequestInterceptor interceptor) {
        addInterceptor(interceptor);
    }
    
    public void addResponseInterceptor(final HttpResponseInterceptor interceptor) {
        addInterceptor(interceptor);
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
    
    public void setInterceptors(final Set interceptors) {
        if (interceptors == null) {
            return;
        }
        if (this.interceptors != null) {
            this.interceptors.clear();
            this.interceptors.addAll(interceptors);
        } else {
            this.interceptors = new HashSet(interceptors);
        }
    }
    
    protected void preprocessRequest(final HttpMutableRequest request) 
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

    protected void postprocessResponse(final HttpMutableResponse response) 
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
    
    protected HttpContext getContext() {
    	return this.localContext;
    }
    
}
