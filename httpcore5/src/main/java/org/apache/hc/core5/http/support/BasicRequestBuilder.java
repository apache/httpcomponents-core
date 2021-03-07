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
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link BasicHttpRequest} instances.
 *
 * @since 5.1
 */
public class BasicRequestBuilder extends AbstractRequestBuilder<BasicHttpRequest> {

    BasicRequestBuilder(final String method) {
        super(method);
    }

    BasicRequestBuilder(final Method method) {
        super(method);
    }

    BasicRequestBuilder(final String method, final URI uri) {
        super(method, uri);
    }

    BasicRequestBuilder(final Method method, final URI uri) {
        super(method, uri);
    }

    BasicRequestBuilder(final Method method, final String uri) {
        super(method, uri);
    }

    BasicRequestBuilder(final String method, final String uri) {
        super(method, uri);
    }

    public static BasicRequestBuilder create(final String method) {
        Args.notBlank(method, "HTTP method");
        return new BasicRequestBuilder(method);
    }

    public static BasicRequestBuilder get() {
        return new BasicRequestBuilder(Method.GET);
    }

    public static BasicRequestBuilder get(final URI uri) {
        return new BasicRequestBuilder(Method.GET, uri);
    }

    public static BasicRequestBuilder get(final String uri) {
        return new BasicRequestBuilder(Method.GET, uri);
    }

    public static BasicRequestBuilder head() {
        return new BasicRequestBuilder(Method.HEAD);
    }

    public static BasicRequestBuilder head(final URI uri) {
        return new BasicRequestBuilder(Method.HEAD, uri);
    }

    public static BasicRequestBuilder head(final String uri) {
        return new BasicRequestBuilder(Method.HEAD, uri);
    }

    public static BasicRequestBuilder patch() {
        return new BasicRequestBuilder(Method.PATCH);
    }

    public static BasicRequestBuilder patch(final URI uri) {
        return new BasicRequestBuilder(Method.PATCH, uri);
    }

    public static BasicRequestBuilder patch(final String uri) {
        return new BasicRequestBuilder(Method.PATCH, uri);
    }

    public static BasicRequestBuilder post() {
        return new BasicRequestBuilder(Method.POST);
    }

    public static BasicRequestBuilder post(final URI uri) {
        return new BasicRequestBuilder(Method.POST, uri);
    }

    public static BasicRequestBuilder post(final String uri) {
        return new BasicRequestBuilder(Method.POST, uri);
    }

    public static BasicRequestBuilder put() {
        return new BasicRequestBuilder(Method.PUT);
    }

    public static BasicRequestBuilder put(final URI uri) {
        return new BasicRequestBuilder(Method.PUT, uri);
    }

    public static BasicRequestBuilder put(final String uri) {
        return new BasicRequestBuilder(Method.PUT, uri);
    }

    public static BasicRequestBuilder delete() {
        return new BasicRequestBuilder(Method.DELETE);
    }

    public static BasicRequestBuilder delete(final URI uri) {
        return new BasicRequestBuilder(Method.DELETE, uri);
    }

    public static BasicRequestBuilder delete(final String uri) {
        return new BasicRequestBuilder(Method.DELETE, uri);
    }

    public static BasicRequestBuilder trace() {
        return new BasicRequestBuilder(Method.TRACE);
    }

    public static BasicRequestBuilder trace(final URI uri) {
        return new BasicRequestBuilder(Method.TRACE, uri);
    }

    public static BasicRequestBuilder trace(final String uri) {
        return new BasicRequestBuilder(Method.TRACE, uri);
    }

    public static BasicRequestBuilder options() {
        return new BasicRequestBuilder(Method.OPTIONS);
    }

    public static BasicRequestBuilder options(final URI uri) {
        return new BasicRequestBuilder(Method.OPTIONS, uri);
    }

    public static BasicRequestBuilder options(final String uri) {
        return new BasicRequestBuilder(Method.OPTIONS, uri);
    }

    public static BasicRequestBuilder copy(final HttpRequest request) {
        Args.notNull(request, "HTTP request");
        final BasicRequestBuilder builder = new BasicRequestBuilder(request.getMethod());
        builder.digest(request);
        return builder;
    }

    @Override
    public BasicRequestBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public BasicRequestBuilder setUri(final URI uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public BasicRequestBuilder setUri(final String uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public BasicRequestBuilder setScheme(final String scheme) {
        super.setScheme(scheme);
        return this;
    }

    @Override
    public BasicRequestBuilder setAuthority(final URIAuthority authority) {
        super.setAuthority(authority);
        return this;
    }

    /**
     * @since 5.1
     */
    @Override
    public BasicRequestBuilder setHttpHost(final HttpHost httpHost) {
        super.setHttpHost(httpHost);
        return this;
    }

    @Override
    public BasicRequestBuilder setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public BasicRequestBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public BasicRequestBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public BasicRequestBuilder addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public BasicRequestBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public BasicRequestBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public BasicRequestBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public BasicRequestBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    @Override
    public BasicRequestBuilder setCharset(final Charset charset) {
        super.setCharset(charset);
        return this;
    }

    @Override
    public BasicRequestBuilder addParameter(final NameValuePair nvp) {
        super.addParameter(nvp);
        return this;
    }

    @Override
    public BasicRequestBuilder addParameter(final String name, final String value) {
        super.addParameter(name, value);
        return this;
    }

    @Override
    public BasicRequestBuilder addParameters(final NameValuePair... nvps) {
        super.addParameters(nvps);
        return this;
    }

    @Override
    public BasicRequestBuilder setAbsoluteRequestUri(final boolean absoluteRequestUri) {
        super.setAbsoluteRequestUri(absoluteRequestUri);
        return this;
    }

    @Override
    public BasicHttpRequest build() {
        String path = getPath();
        final List<NameValuePair> parameters = getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            try {
                final URI uri = new URIBuilder(path)
                        .setCharset(getCharset())
                        .addParameters(parameters)
                        .build();
                path = uri.toASCIIString();
            } catch (final URISyntaxException ex) {
                // should never happen
            }
        }
        final BasicHttpRequest result = new BasicHttpRequest(getMethod(), getScheme(), getAuthority(), path);
        result.setVersion(getVersion());
        result.setHeaders(getHeaders());
        result.setAbsoluteRequestUri(isAbsoluteRequestUri());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("BasicRequestBuilder [method=");
        builder.append(getMethod());
        builder.append(", scheme=");
        builder.append(getScheme());
        builder.append(", authority=");
        builder.append(getAuthority());
        builder.append(", path=");
        builder.append(getPath());
        builder.append(", parameters=");
        builder.append(getParameters());
        builder.append(", headerGroup=");
        builder.append(Arrays.toString(getHeaders()));
        builder.append("]");
        return builder.toString();
    }

}
