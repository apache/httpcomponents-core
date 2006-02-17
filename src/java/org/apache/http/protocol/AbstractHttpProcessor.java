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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;

/**
 * Keeps lists of interceptors for processing requests and responses.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public abstract class AbstractHttpProcessor {

    private List requestInterceptors = null; 
    private List responseInterceptors = null; 
    
    public void addInterceptor(final HttpRequestInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        if (this.requestInterceptors == null) {
            this.requestInterceptors = new ArrayList();
        }
        this.requestInterceptors.add(interceptor);
    }
    
    public void removeInterceptor(final HttpRequestInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        if (this.requestInterceptors == null) {
            return;
        }
        this.requestInterceptors.remove(interceptor);
        if (this.requestInterceptors.isEmpty()) {
            this.requestInterceptors = null;
        }
    }
    
    public void addInterceptor(final HttpResponseInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        if (this.responseInterceptors == null) {
            this.responseInterceptors = new ArrayList();
        }
        this.responseInterceptors.add(interceptor);
    }
    
    public void removeInterceptor(final HttpResponseInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        if (this.responseInterceptors == null) {
            return;
        }
        this.responseInterceptors.remove(interceptor);
        if (this.responseInterceptors.isEmpty()) {
            this.responseInterceptors = null;
        }
    }
    
    public void removeInterceptors(final Class clazz) {
        if (clazz == null) {
            return;
        }
        if (this.requestInterceptors != null) {
            for (Iterator i = this.requestInterceptors.iterator(); i.hasNext(); ) {
                if (clazz.isInstance(i.next())) {
                    i.remove();
                }
            }
        }
        if (this.responseInterceptors != null) {
            for (Iterator i = this.responseInterceptors.iterator(); i.hasNext(); ) {
                if (clazz.isInstance(i.next())) {
                    i.remove();
                }
            }
        }
    }

    public void setInterceptors(final List list) {
        if (list == null) {
            return;
        }
        if (this.requestInterceptors != null) {
            this.requestInterceptors.clear();
        }
        if (this.responseInterceptors != null) {
            this.responseInterceptors.clear();
        }
        for (int i = 0; i < list.size(); i++) {
            Object obj = list.get(i);
            if (obj instanceof HttpRequestInterceptor) {
                addInterceptor((HttpRequestInterceptor)obj);
            }
            if (obj instanceof HttpResponseInterceptor) {
                addInterceptor((HttpResponseInterceptor)obj);
            }
        }
    }
    
    public void clearInterceptors() {
        this.requestInterceptors = null;
        this.responseInterceptors = null;
    }
    
    protected void preprocessRequest(
            final HttpRequest request,
            final HttpContext context) 
                throws IOException, HttpException {
        if (this.requestInterceptors != null) {
            for (int i = 0; i < this.requestInterceptors.size(); i++) {
                HttpRequestInterceptor interceptor = (HttpRequestInterceptor) this.requestInterceptors.get(i);
                interceptor.process(request, context);
            }
        }
    }

    protected void postprocessResponse(
            final HttpResponse response,
            final HttpContext context) 
                throws IOException, HttpException {
        if (this.responseInterceptors != null) {
            for (int i = 0; i < this.responseInterceptors.size(); i++) {
                HttpResponseInterceptor interceptor = (HttpResponseInterceptor) this.responseInterceptors.get(i);
                interceptor.process(response, context);
            }
        }
    }
    
}
