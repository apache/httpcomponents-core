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

package org.apache.hc.core5.http.message;

import java.net.URI;

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * Basic implementation of {@link HttpRequest}.
 *
 * @since 4.0
 */
@NotThreadSafe
public class BasicHttpRequest extends AbstractHttpMessage implements HttpRequest {

    private static final long serialVersionUID = 1L;

    private final String method;
    private HttpHost host;
    private String requestUri;
    private ProtocolVersion version;

    /**
     * Creates request message with the given method and request URI.
     *
     * @param method request method.
     * @param requestUri request URI.
     */
    public BasicHttpRequest(final String method, final String requestUri) {
        this(method, null, requestUri);
    }

    /**
     * Creates request message with the given method, host and request URI.
     *
     * @param method request method.
     * @param host request host.
     * @param requestUri request URI.
     *
     * @since 5.0
     */
    public BasicHttpRequest(final String method, final HttpHost host, final String requestUri) {
        super();
        this.method = Args.notNull(method, "Method name");
        this.host = host;
        this.requestUri = Args.notNull(requestUri, "Request URI");
    }

    /**
     * Creates request message with the given method, request URI.
     *
     * @param method request method.
     * @param requestUri request URI.
     *
     * @since 5.0
     */
    public BasicHttpRequest(final String method, final URI requestUri) {
        super();
        this.method = Args.notNull(method, "Method name");
        Args.notNull(requestUri, "Request URI");
        if (requestUri.isAbsolute()) {
            this.host = new HttpHost(requestUri.getHost(), requestUri.getPort(), requestUri.getScheme());
        } else {
            this.host = null;
        }
        final StringBuilder buf = new StringBuilder();
        final String path = requestUri.getRawPath();
        if (!TextUtils.isBlank(path)) {
            buf.append(path);
        } else {
            buf.append("/");
        }
        final String query = requestUri.getRawQuery();
        if (query != null) {
            buf.append('?').append(query);
        }
        this.requestUri = buf.toString();
    }

    @Override
    public void setVersion(final ProtocolVersion version) {
        this.version = version;
    }

    @Override
    public ProtocolVersion getVersion() {
        return this.version;
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    /**
     * @since 5.0
     */
    @Override
    public HttpHost getHost() {
        return this.host;
    }

    /**
     * @since 5.0
     */
    @Override
    public void setHost(final HttpHost host) {
        this.host = host;
    }

    @Override
    public String getUri() {
        return this.requestUri;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.method).append(" ").append(this.host).append(" ").append(this.requestUri).append(" ")
                .append(super.toString());
        return sb.toString();
    }

}
