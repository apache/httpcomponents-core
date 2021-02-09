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

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Basic implementation of {@link HttpRequest}.
 *
 * @since 4.0
 */
public class BasicHttpRequest extends HeaderGroup implements HttpRequest {

    private static final long serialVersionUID = 1L;

    private final String method;
    private String path;
    private String scheme;
    private URIAuthority authority;
    private ProtocolVersion version;
    private URI requestUri;
    private boolean absoluteRequestUri;

    /**
     * Creates request message with the given method, host and request path.
     *
     * @param method request method.
     * @param scheme request scheme.
     * @param authority request authority.
     * @param path request path.
     *
     * @since 5.1
     */
    public BasicHttpRequest(final String method, final String scheme, final URIAuthority authority, final String path) {
        super();
        this.method = Args.notNull(method, "Method name");
        this.scheme = scheme;
        this.authority = authority;
        this.path = path;
    }

    /**
     * Creates request message with the given method and request path.
     *
     * @param method request method.
     * @param path request path.
     */
    public BasicHttpRequest(final String method, final String path) {
        super();
        this.method = method;
        if (path != null) {
            try {
                setUri(new URI(path));
            } catch (final URISyntaxException ex) {
                this.path = path;
            }
        }
    }

    /**
     * Creates request message with the given method, host and request path.
     *
     * @param method request method.
     * @param host request host.
     * @param path request path.
     *
     * @since 5.0
     */
    public BasicHttpRequest(final String method, final HttpHost host, final String path) {
        super();
        this.method = Args.notNull(method, "Method name");
        this.scheme = host != null ? host.getSchemeName() : null;
        this.authority = host != null ? new URIAuthority(host) : null;
        this.path = path;
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
        setUri(Args.notNull(requestUri, "Request URI"));
    }

    /**
     * Creates request message with the given method and request path.
     *
     * @param method request method.
     * @param path request path.
     *
     * @since 5.0
     */
    public BasicHttpRequest(final Method method, final String path) {
        super();
        this.method = Args.notNull(method, "Method").name();
        if (path != null) {
            try {
                setUri(new URI(path));
            } catch (final URISyntaxException ex) {
                this.path = path;
            }
        }
    }

    /**
     * Creates request message with the given method, host and request path.
     *
     * @param method request method.
     * @param host request host.
     * @param path request path.
     *
     * @since 5.0
     */
    public BasicHttpRequest(final Method method, final HttpHost host, final String path) {
        super();
        this.method = Args.notNull(method, "Method").name();
        this.scheme = host != null ? host.getSchemeName() : null;
        this.authority = host != null ? new URIAuthority(host) : null;
        this.path = path;
    }

    /**
     * Creates request message with the given method, request URI.
     *
     * @param method request method.
     * @param requestUri request URI.
     *
     * @since 5.0
     */
    public BasicHttpRequest(final Method method, final URI requestUri) {
        super();
        this.method = Args.notNull(method, "Method").name();
        setUri(Args.notNull(requestUri, "Request URI"));
    }

    @Override
    public void addHeader(final String name, final Object value) {
        Args.notNull(name, "Header name");
        addHeader(new BasicHeader(name, value));
    }

    @Override
    public void setHeader(final String name, final Object value) {
        Args.notNull(name, "Header name");
        setHeader(new BasicHeader(name, value));
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

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public void setPath(final String path) {
        if (path != null) {
            Args.check(!path.startsWith("//"), "URI path begins with multiple slashes");
        }
        this.path = path;
        this.requestUri = null;
    }

    @Override
    public String getScheme() {
        return this.scheme;
    }

    @Override
    public void setScheme(final String scheme) {
        this.scheme = scheme;
        this.requestUri = null;
    }

    @Override
    public URIAuthority getAuthority() {
        return this.authority;
    }

    @Override
    public void setAuthority(final URIAuthority authority) {
        this.authority = authority;
        this.requestUri = null;
    }

    /**
     * Sets a flag that the {@link #getRequestUri()} method should return the request URI
     * in an absolute form.
     * <p>
     * This flag can used when the request is going to be transmitted via an HTTP/1.1 proxy.
     *
     * @since 5.1
     */
    public void setAbsoluteRequestUri(final boolean absoluteRequestUri) {
        this.absoluteRequestUri = absoluteRequestUri;
    }

    @Override
    public String getRequestUri() {
        if (absoluteRequestUri) {
            final StringBuilder buf = new StringBuilder();
            assembleRequestUri(buf);
            return buf.toString();
        } else {
            return getPath();
        }
    }

    @Override
    public void setUri(final URI requestUri) {
        this.scheme = requestUri.getScheme();
        if (requestUri.getHost() != null) {
            this.authority = new URIAuthority(
                    requestUri.getRawUserInfo(), requestUri.getHost(), requestUri.getPort());
        } else if (requestUri.getRawAuthority() != null) {
            try {
                this.authority = URIAuthority.create(requestUri.getRawAuthority());
            } catch (final URISyntaxException ignore) {
                this.authority = null;
            }
        } else {
            this.authority = null;
        }
        final StringBuilder buf = new StringBuilder();
        final String rawPath = requestUri.getRawPath();
        if (!TextUtils.isBlank(rawPath)) {
            Args.check(!rawPath.startsWith("//"), "URI path begins with multiple slashes");
            buf.append(rawPath);
        } else {
            buf.append("/");
        }
        final String query = requestUri.getRawQuery();
        if (query != null) {
            buf.append('?').append(query);
        }
        this.path = buf.toString();
    }

    private void assembleRequestUri(final StringBuilder buf) {
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
    }

    @Override
    public URI getUri() throws URISyntaxException {
        if (this.requestUri == null) {
            final StringBuilder buf = new StringBuilder();
            assembleRequestUri(buf);
            this.requestUri = new URI(buf.toString());
        }
        return this.requestUri;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(method).append(" ");
        assembleRequestUri(buf);
        return buf.toString();
    }

}
