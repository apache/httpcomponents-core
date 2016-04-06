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

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.DefaultHttpResponseFactory;
import org.apache.hc.core5.http2.H2PseudoResponseHeaders;

/**
 * HTTP/2 response parser.
 *
 * @since 5.0
 */
@NotThreadSafe
public class Http2ResponseParser extends AbstractHttp2MessageParser<HttpResponse> {

    private HttpResponseFactory responseFactory;

    public Http2ResponseParser(final Charset charset, final HttpResponseFactory responseFactory) {
        super(charset);
        this.responseFactory = responseFactory != null ? responseFactory : DefaultHttpResponseFactory.INSTANCE;
    }

    public Http2ResponseParser(final Charset charset) {
        this(charset, null);
    }

    @Override
    protected HttpResponse createMessage(final List<Header> pseudoHeaders) throws HttpException {
        String statusText = null;

        for (int i = 0; i < pseudoHeaders.size(); i++) {
            final Header header = pseudoHeaders.get(i);
            final String name = header.getName();
            final String value = header.getValue();
            if (name.equals(H2PseudoResponseHeaders.STATUS)) {
                if (statusText != null) {
                    throw new ProtocolException("Multiple '" + name + "' response headers are illegal");
                }
                statusText = value;
            } else {
                throw new ProtocolException("Unsupported response header '" + name + "'");
            }
        }
        if (statusText == null) {
            throw new ProtocolException("Mandatory response header ':status' not found");
        }
        try {
            return this.responseFactory.newHttpResponse(HttpVersion.HTTP_2, Integer.parseInt(statusText), null);
        } catch (NumberFormatException ex) {
            throw new ProtocolException("Invalid response status: " + statusText);
        }
    }

}
