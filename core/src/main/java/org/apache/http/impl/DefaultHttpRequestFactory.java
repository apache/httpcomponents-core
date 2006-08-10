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

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;

/**
 * Default implementation of a factory for creating request objects.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultHttpRequestFactory implements HttpRequestFactory {
    
    public DefaultHttpRequestFactory() {
        super();
    }

    public HttpRequest newHttpRequest(final RequestLine requestline)
            throws MethodNotSupportedException {
        if (requestline == null) {
            throw new IllegalArgumentException("Request line may not be null");
        }
        String method = requestline.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            return new BasicHttpRequest(requestline); 
        } else if ("HEAD".equalsIgnoreCase(method)) {
            return new BasicHttpRequest(requestline); 
        } else if ("POST".equalsIgnoreCase(method)) {
            return new BasicHttpEntityEnclosingRequest(requestline); 
        } else { 
            throw new MethodNotSupportedException(method +  " method not supported");
        }
    }

    public HttpRequest newHttpRequest(final String method, final String uri)
			throws MethodNotSupportedException {
		if ("GET".equalsIgnoreCase(method)) {
			return new BasicHttpRequest(method, uri);
		} else if ("POST".equalsIgnoreCase(method)) {
			return new BasicHttpEntityEnclosingRequest(method, uri);
		} else {
			throw new MethodNotSupportedException(method
					+ " method not supported");
		}
    }
    
}
