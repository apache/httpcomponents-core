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

package org.apache.http.impl.nio.codecs;

import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequestFactory;
import org.apache.http.RequestLine;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.util.CharArrayBuffer;

public class HttpRequestParser extends HttpMessageParser {
    
    private final HttpRequestFactory requestFactory;
    
    public HttpRequestParser(
            final SessionInputBuffer buffer, 
            final HttpRequestFactory requestFactory,
            final HttpParams params) {
        super(buffer, params);
        if (requestFactory == null) {
            throw new IllegalArgumentException("Request factory may not be null");
        }
        this.requestFactory = requestFactory;
    }

    protected HttpMessage createMessage(final CharArrayBuffer buffer) 
            throws HttpException {
        RequestLine requestLine = BasicRequestLine.parse(buffer, 0, buffer.length());
        return this.requestFactory.newHttpRequest(requestLine);
    }

}
