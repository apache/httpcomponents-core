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

package org.apache.hc.core5.http.support;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.TextUtils;

/**
 * Builder for {@link BasicHttpRequest} instances.
 *
 * @since 5.1
 */
public abstract class AbstractRequestBuilder<T> extends AbstractMessageBuilder<T> {

    final private String method;
    private String scheme;
    private URIAuthority authority;
    private String path;
    private Charset charset;
    private List<NameValuePair> parameters;
    private boolean absoluteRequestUri;

    protected AbstractRequestBuilder(final String method) {
        super();
        this.method = method;
    }

    protected AbstractRequestBuilder(final Method method) {
        this(method.name());
    }

    protected AbstractRequestBuilder(final String method, final URI uri) {
        super();
        this.method = method;
        setUri(uri);
    }

    protected AbstractRequestBuilder(final Method method, final URI uri) {
        this(method.name(), uri);
    }

    protected AbstractRequestBuilder(final Method method, final String uri) {
        this(method.name(), uri != null ? URI.create(uri) : null);
    }

    protected AbstractRequestBuilder(final String method, final String uri) {
        this(method, uri != null ? URI.create(uri) : null);
    }

    protected void digest(final HttpRequest request) {
        if (request == null) {
            return;
        }
        setScheme(request.getScheme());
        setAuthority(request.getAuthority());
        setPath(request.getPath());
        this.parameters = null;
        super.digest(request);
    }

    public String getMethod() {
        return method;
    }

    @Override
    public AbstractRequestBuilder<T> setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    public String getScheme() {
        return scheme;
    }

    public AbstractRequestBuilder<T> setScheme(final String scheme) {
        this.scheme = scheme;
        return this;
    }

    public URIAuthority getAuthority() {
        return authority;
    }

    public AbstractRequestBuilder<T> setAuthority(final URIAuthority authority) {
        this.authority = authority;
        return this;
    }

    public AbstractRequestBuilder<T> setHttpHost(final HttpHost httpHost) {
        if (httpHost == null) {
            return this;
        }
        this.authority = new URIAuthority(httpHost);
        this.scheme = httpHost.getSchemeName();
        return this;
    }

    public String getPath() {
        return path;
    }

    public AbstractRequestBuilder<T> setPath(final String path) {
        this.path = path;
        return this;
    }

    public URI getUri() {
        final StringBuilder buf = new StringBuilder();
        if (this.authority != null) {
            buf.append(this.scheme != null ? this.scheme : URIScheme.HTTP.id).append("://");
            buf.append(this.authority.getHostName());
            if (this.authority.getPort() >= 0) {
                buf.append(":").append(this.authority.getPort());
            }
        }
        if (this.path == null) {
            buf.append("/");
        } else {
            if (buf.length() > 0 && !this.path.startsWith("/")) {
                buf.append("/");
            }
            buf.append(this.path);
        }
        return URI.create(buf.toString());
    }

    public AbstractRequestBuilder<T> setUri(final URI uri) {
        if (uri == null) {
            this.scheme = null;
            this.authority = null;
            this.path = null;
        } else {
            this.scheme = uri.getScheme();
            if (uri.getHost() != null) {
                this.authority = new URIAuthority(uri.getRawUserInfo(), uri.getHost(), uri.getPort());
            } else if (uri.getRawAuthority() != null) {
                try {
                    this.authority = URIAuthority.create(uri.getRawAuthority());
                } catch (final URISyntaxException ignore) {
                    this.authority = null;
                }
            } else {
                this.authority = null;
            }
            final StringBuilder buf = new StringBuilder();
            final String rawPath = uri.getRawPath();
            if (!TextUtils.isBlank(rawPath)) {
                buf.append(rawPath);
            } else {
                buf.append("/");
            }
            final String query = uri.getRawQuery();
            if (query != null) {
                buf.append('?').append(query);
            }
            this.path = buf.toString();
        }
        return this;
    }

    public AbstractRequestBuilder<T> setUri(final String uri) {
        setUri(uri != null ? URI.create(uri) : null);
        return this;
    }

    @Override
    public AbstractRequestBuilder<T> setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public AbstractRequestBuilder<T> addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public AbstractRequestBuilder<T> addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public AbstractRequestBuilder<T> removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public AbstractRequestBuilder<T> removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public AbstractRequestBuilder<T> setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public AbstractRequestBuilder<T> setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public AbstractRequestBuilder<T> setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    public List<NameValuePair> getParameters() {
        return parameters != null ? new ArrayList<>(parameters) : null;
    }

    public AbstractRequestBuilder<T> addParameter(final NameValuePair nvp) {
        if (nvp == null) {
            return this;
        }
        if (parameters == null) {
            parameters = new LinkedList<>();
        }
        parameters.add(nvp);
        return this;
    }

    public AbstractRequestBuilder<T> addParameter(final String name, final String value) {
        return addParameter(new BasicNameValuePair(name, value));
    }

    public AbstractRequestBuilder<T> addParameters(final NameValuePair... nvps) {
        for (final NameValuePair nvp : nvps) {
            addParameter(nvp);
        }
        return this;
    }

    public boolean isAbsoluteRequestUri() {
        return absoluteRequestUri;
    }

    public AbstractRequestBuilder<T> setAbsoluteRequestUri(final boolean absoluteRequestUri) {
        this.absoluteRequestUri = absoluteRequestUri;
        return this;
    }

}
