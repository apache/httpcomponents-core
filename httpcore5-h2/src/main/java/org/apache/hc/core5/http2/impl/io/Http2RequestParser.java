/*
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

package org.apache.hc.core5.http2.impl.io;

import java.nio.charset.Charset;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestFactory;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.DefaultHttpRequestFactory;
import org.apache.hc.core5.http2.H2PseudoRequestHeaders;

/**
 * HTTP/2 request parser.
 *
 * @since 5.0
 */
public class Http2RequestParser extends AbstractHttp2MessageParser<HttpRequest> {

    private final HttpRequestFactory requestFactory;

    public Http2RequestParser(final Charset charset, final HttpRequestFactory requestFactory) {
        super(charset);
        this.requestFactory = requestFactory != null ? requestFactory : DefaultHttpRequestFactory.INSTANCE;
    }

    public Http2RequestParser(final Charset charset) {
        this(charset, null);
    }

    @Override
    protected HttpRequest createMessage(final List<Header> pseudoHeaders) throws HttpException {
        String method = null;
        String scheme = null;
        String authority = null;
        String path = null;

        for (int i = 0; i < pseudoHeaders.size(); i++) {
            final Header header = pseudoHeaders.get(i);
            final String name = header.getName();
            final String value = header.getValue();
            if (name.equals(H2PseudoRequestHeaders.METHOD)) {
                if (method != null) {
                    throw new ProtocolException("Multiple '" + name + "' request headers are illegal");
                }
                method = value;
            } else if (name.equals(H2PseudoRequestHeaders.SCHEME)) {
                if (scheme != null) {
                    throw new ProtocolException("Multiple '" + name + "' request headers are illegal");
                }
                scheme = value;
            } else if (name.equals(H2PseudoRequestHeaders.PATH)) {
                if (path != null) {
                    throw new ProtocolException("Multiple '" + name + "' request headers are illegal");
                }
                path = value;
            } else if (name.equals(H2PseudoRequestHeaders.AUTHORITY)) {
                authority = value;
            } else {
                throw new ProtocolException("Unsupported request header '" + name + "'");
            }
        }
        if (method == null) {
            throw new ProtocolException("Mandatory request header ':method' not found");
        }
        if (method.equalsIgnoreCase("CONNECT")) {
            if (authority == null) {
                throw new ProtocolException("Header ':authority' is mandatory for CONNECT request");
            }
            if (scheme != null) {
                throw new ProtocolException("Header ':scheme' must not be set for CONNECT request");
            }
            if (path != null) {
                throw new ProtocolException("Header ':path' must not be set for CONNECT request");
            }
        } else {
            if (scheme == null) {
                throw new ProtocolException("Mandatory request header ':scheme' not found");
            }
            if (path == null) {
                throw new ProtocolException("Mandatory request header ':path' not found");
            }
        }

        final HttpRequest httpRequest = this.requestFactory.newHttpRequest(HttpVersion.HTTP_2, method, path);
        httpRequest.setScheme(scheme);
        httpRequest.setAuthority(authority);
        httpRequest.setPath(path);

        return httpRequest;
    }

}
