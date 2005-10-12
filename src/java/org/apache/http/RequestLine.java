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

package org.apache.http;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.apache.http.util.CharArrayBuffer;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class RequestLine {

    private final HttpVersion httpversion;
    private final String method;
    private final String uri;

    public RequestLine(final String method, final String uri, final HttpVersion httpversion) {
    	super();
    	if (method == null) {
    		throw new IllegalArgumentException("Method may not be null");
    	}
    	if (uri == null) {
    		throw new IllegalArgumentException("URI may not be null");
    	}
    	if (httpversion == null) {
    		throw new IllegalArgumentException("HTTP version may not be null");
    	}
    	this.method = method;
        this.uri = uri;
        this.httpversion = httpversion;
    }

    public String getMethod() {
        return this.method;
    }

    public HttpVersion getHttpVersion() {
        return this.httpversion;
    }

    public String getUri() {
        return this.uri;
    }

    public String toString() {
        CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(this.method);
        buffer.append(' ');
        buffer.append(this.uri);
        buffer.append(' ');
        buffer.append(this.httpversion);
        return buffer.toString();
    }
    
    public static RequestLine parse(final String requestLine) 
            throws HttpException {
        if (requestLine == null) {
            throw new IllegalArgumentException("Request line string may not be null");
        }
        String method = null;
        String uri = null;
        String protocol = null;
        try {
            StringTokenizer st = new StringTokenizer(requestLine, " ");
            method = st.nextToken().trim();
            uri = st.nextToken().trim();
            protocol = st.nextToken();
        } catch (NoSuchElementException e) {
            throw new ProtocolException("Invalid request line: " + requestLine);
        }
        return new RequestLine(method, uri, HttpVersion.parse(protocol));
    }

}
