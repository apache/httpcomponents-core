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

package org.apache.http.message;

import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.io.CharArrayBuffer;
import org.apache.http.protocol.HTTP;

/**
 * The first line of an {@link HttpRequest HttpRequest}.
 * It contains the method, URI, and HTTP version of the request.
 * For details, see RFC 2616.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BasicRequestLine implements RequestLine {

    private final HttpVersion httpversion;
    private final String method;
    private final String uri;

    public BasicRequestLine(final String method, final String uri, final HttpVersion httpversion) {
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
    
    public static RequestLine parse(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo) 
            throws ProtocolException {
        if (buffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }
        try {
            int i = indexFrom;
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }
            int blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                throw new ProtocolException("Invalid request line: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            String method = buffer.substringTrimmed(i, blank);
            i = blank;
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }
            blank = buffer.indexOf(' ', i, indexTo);
            if (blank < 0) {
                throw new ProtocolException("Invalid request line: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            String uri = buffer.substringTrimmed(i, blank);
            HttpVersion ver = BasicHttpVersion.parse(buffer, blank, indexTo);
            return new BasicRequestLine(method, uri, ver);
        } catch (IndexOutOfBoundsException e) {
            throw new ProtocolException("Invalid request line: " + 
                    buffer.substring(indexFrom, indexTo)); 
        }
    }

    public static final RequestLine parse(final String s)
            throws ProtocolException {
        if (s == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        CharArrayBuffer buffer = new CharArrayBuffer(s.length()); 
        buffer.append(s);
        return parse(buffer, 0, buffer.length());
    }
    
    public static void format(final CharArrayBuffer buffer, final RequestLine requestline) {
        if (buffer == null) {
            throw new IllegalArgumentException("String buffer may not be null");
        }
        if (requestline == null) {
            throw new IllegalArgumentException("Request line may not be null");
        }
        buffer.append(requestline.getMethod());
        buffer.append(' ');
        buffer.append(requestline.getUri());
        buffer.append(' ');
        BasicHttpVersion.format(buffer, requestline.getHttpVersion());
    }
 
    public static String format(final RequestLine requestline) {
        CharArrayBuffer buffer = new CharArrayBuffer(64);
        format(buffer, requestline);
        return buffer.toString();
    }
    
}
