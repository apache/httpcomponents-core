/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.message;

import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.params.HttpProtocolParams;

/**
 * Basic implementation of an HTTP request that can be modified.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BasicHttpRequest extends AbstractHttpMessage implements HttpRequest {
    
    private RequestLine requestline;
    private String method;
    private String uri;
    private ProtocolVersion ver;
        
    public BasicHttpRequest(final String method, final String uri, final ProtocolVersion ver) {
        super();
        if (method == null) {
            throw new IllegalArgumentException("Method name may not be null");
        }
        if (uri == null) {
            throw new IllegalArgumentException("Request URI may not be null");
        }
        this.method = method;
        this.uri = uri;
        this.ver = ver;
        this.requestline = null;
    }

    public BasicHttpRequest(final RequestLine requestline) {
        super();
        if (requestline == null) {
            throw new IllegalArgumentException("Request line may not be null");
        }
        this.requestline = requestline;
        this.method = requestline.getMethod();
        this.uri = requestline.getUri();
        this.ver = requestline.getProtocolVersion();
    }

    public BasicHttpRequest(final String method, final String uri) {
        this(method, uri, null);
    }

    public ProtocolVersion getProtocolVersion() {
        if (this.ver == null) {
            this.ver = HttpProtocolParams.getVersion(getParams());
        }
        return this.ver;
    }
    
    public RequestLine getRequestLine() {
        if (this.requestline == null) {
            this.requestline = new BasicRequestLine(this.method, this.uri, 
                    getProtocolVersion());
        }
        return this.requestline;
    }
    
    public void setUri(final String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Request URI may not be null");
        }
        this.uri = uri;
        this.requestline = null;
    }
    
    public void setProtocolVersion(final ProtocolVersion ver) {
        this.ver = ver;
        this.requestline = null;
    }
    
}
