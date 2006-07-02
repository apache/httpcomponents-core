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

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.params.HttpProtocolParams;

/**
 * A request interceptor that sets the Host header for HTTP/1.1 requests.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class RequestTargetHost implements HttpRequestInterceptor {

    public RequestTargetHost() {
        super();
    }
    
    public void process(final HttpRequest request, final HttpContext context) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        if (!request.containsHeader(HTTP.TARGET_HOST)) {
            HttpHost targethost = (HttpHost) context
                .getAttribute(HttpExecutionContext.HTTP_TARGET_HOST);
            if (targethost == null) {
                HttpVersion ver = request.getRequestLine().getHttpVersion();
                if (ver.lessEquals(HttpVersion.HTTP_1_0)) {
                    return;
                } else {
                    throw new ProtocolException("Target host missing");
                }
            }
            String virtualhost = HttpProtocolParams.getVirtualHost(request.getParams());
            if (virtualhost != null) {
                targethost = new HttpHost(virtualhost, 
                        targethost.getPort(), targethost.getScheme());
            }
            request.addHeader(HTTP.TARGET_HOST, targethost.toHostString());
        }
    }
    
}
