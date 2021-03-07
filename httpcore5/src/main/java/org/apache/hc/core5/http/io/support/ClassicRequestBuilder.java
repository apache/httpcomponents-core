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

package org.apache.hc.core5.http.io.support;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.support.AbstractRequestBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * Builder for {@link ClassicHttpRequest} instances.
 * <p>
 * Please note that this class treats parameters differently depending on composition
 * of the request: if the request has a content entity explicitly set with
 * {@link #setEntity(HttpEntity)} or it is not an entity enclosing method
 * (such as POST or PUT), parameters will be added to the query component
 * of the request URI. Otherwise, parameters will be added as a URL encoded entity.
 * </p>
 *
 * @since 5.0
 */
public class ClassicRequestBuilder extends AbstractRequestBuilder<ClassicHttpRequest> {

    private HttpEntity entity;

    ClassicRequestBuilder(final String method) {
        super(method);
    }

    ClassicRequestBuilder(final Method method) {
        super(method);
    }

    ClassicRequestBuilder(final String method, final URI uri) {
        super(method, uri);
    }

    ClassicRequestBuilder(final Method method, final URI uri) {
        super(method, uri);
    }

    ClassicRequestBuilder(final Method method, final String uri) {
        super(method, uri);
    }

    ClassicRequestBuilder(final String method, final String uri) {
        super(method, uri);
    }

    public static ClassicRequestBuilder create(final String method) {
        Args.notBlank(method, "HTTP method");
        return new ClassicRequestBuilder(method);
    }

    public static ClassicRequestBuilder get() {
        return new ClassicRequestBuilder(Method.GET);
    }

    public static ClassicRequestBuilder get(final URI uri) {
        return new ClassicRequestBuilder(Method.GET, uri);
    }

    public static ClassicRequestBuilder get(final String uri) {
        return new ClassicRequestBuilder(Method.GET, uri);
    }

    public static ClassicRequestBuilder head() {
        return new ClassicRequestBuilder(Method.HEAD);
    }

    public static ClassicRequestBuilder head(final URI uri) {
        return new ClassicRequestBuilder(Method.HEAD, uri);
    }

    public static ClassicRequestBuilder head(final String uri) {
        return new ClassicRequestBuilder(Method.HEAD, uri);
    }

    public static ClassicRequestBuilder patch() {
        return new ClassicRequestBuilder(Method.PATCH);
    }

    public static ClassicRequestBuilder patch(final URI uri) {
        return new ClassicRequestBuilder(Method.PATCH, uri);
    }

    public static ClassicRequestBuilder patch(final String uri) {
        return new ClassicRequestBuilder(Method.PATCH, uri);
    }

    public static ClassicRequestBuilder post() {
        return new ClassicRequestBuilder(Method.POST);
    }

    public static ClassicRequestBuilder post(final URI uri) {
        return new ClassicRequestBuilder(Method.POST, uri);
    }

    public static ClassicRequestBuilder post(final String uri) {
        return new ClassicRequestBuilder(Method.POST, uri);
    }

    public static ClassicRequestBuilder put() {
        return new ClassicRequestBuilder(Method.PUT);
    }

    public static ClassicRequestBuilder put(final URI uri) {
        return new ClassicRequestBuilder(Method.PUT, uri);
    }

    public static ClassicRequestBuilder put(final String uri) {
        return new ClassicRequestBuilder(Method.PUT, uri);
    }

    public static ClassicRequestBuilder delete() {
        return new ClassicRequestBuilder(Method.DELETE);
    }

    public static ClassicRequestBuilder delete(final URI uri) {
        return new ClassicRequestBuilder(Method.DELETE, uri);
    }

    public static ClassicRequestBuilder delete(final String uri) {
        return new ClassicRequestBuilder(Method.DELETE, uri);
    }

    public static ClassicRequestBuilder trace() {
        return new ClassicRequestBuilder(Method.TRACE);
    }

    public static ClassicRequestBuilder trace(final URI uri) {
        return new ClassicRequestBuilder(Method.TRACE, uri);
    }

    public static ClassicRequestBuilder trace(final String uri) {
        return new ClassicRequestBuilder(Method.TRACE, uri);
    }

    public static ClassicRequestBuilder options() {
        return new ClassicRequestBuilder(Method.OPTIONS);
    }

    public static ClassicRequestBuilder options(final URI uri) {
        return new ClassicRequestBuilder(Method.OPTIONS, uri);
    }

    public static ClassicRequestBuilder options(final String uri) {
        return new ClassicRequestBuilder(Method.OPTIONS, uri);
    }

    /**
     * @since 5.1
     */
    public static ClassicRequestBuilder copy(final ClassicHttpRequest request) {
        Args.notNull(request, "HTTP request");
        final ClassicRequestBuilder builder = new ClassicRequestBuilder(request.getMethod());
        builder.digest(request);
        return builder;
    }

    protected void digest(final ClassicHttpRequest request) {
        super.digest(request);
        setEntity(request.getEntity());
    }

    @Override
    public ClassicRequestBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public ClassicRequestBuilder setUri(final URI uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public ClassicRequestBuilder setUri(final String uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public ClassicRequestBuilder setScheme(final String scheme) {
        super.setScheme(scheme);
        return this;
    }

    @Override
    public ClassicRequestBuilder setAuthority(final URIAuthority authority) {
        super.setAuthority(authority);
        return this;
    }

    /**
     * @since 5.1
     */
    @Override
    public ClassicRequestBuilder setHttpHost(final HttpHost httpHost) {
        super.setHttpHost(httpHost);
        return this;
    }

    @Override
    public ClassicRequestBuilder setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public ClassicRequestBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public ClassicRequestBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public ClassicRequestBuilder addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public ClassicRequestBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public ClassicRequestBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public ClassicRequestBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public ClassicRequestBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    @Override
    public ClassicRequestBuilder setCharset(final Charset charset) {
        super.setCharset(charset);
        return this;
    }

    @Override
    public ClassicRequestBuilder addParameter(final NameValuePair nvp) {
        super.addParameter(nvp);
        return this;
    }

    @Override
    public ClassicRequestBuilder addParameter(final String name, final String value) {
        super.addParameter(name, value);
        return this;
    }

    @Override
    public ClassicRequestBuilder addParameters(final NameValuePair... nvps) {
        super.addParameters(nvps);
        return this;
    }

    @Override
    public ClassicRequestBuilder setAbsoluteRequestUri(final boolean absoluteRequestUri) {
        super.setAbsoluteRequestUri(absoluteRequestUri);
        return this;
    }

    public HttpEntity getEntity() {
        return entity;
    }

    public ClassicRequestBuilder setEntity(final HttpEntity entity) {
        this.entity = entity;
        return this;
    }

    public ClassicRequestBuilder setEntity(final String content, final ContentType contentType) {
        this.entity = new StringEntity(content, contentType);
        return this;
    }

    public ClassicRequestBuilder setEntity(final String content) {
        this.entity = new StringEntity(content);
        return this;
    }

    public ClassicRequestBuilder setEntity(final byte[] content, final ContentType contentType) {
        this.entity = new ByteArrayEntity(content, contentType);
        return this;
    }

    public ClassicHttpRequest build() {
        String path = getPath();
        if (TextUtils.isEmpty(path)) {
            path = "/";
        }
        HttpEntity entityCopy = this.entity;
        final String method = getMethod();
        final List<NameValuePair> parameters = getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            if (entityCopy == null && (Method.POST.isSame(method) || Method.PUT.isSame(method))) {
                entityCopy = HttpEntities.createUrlEncoded(parameters, getCharset());
            } else {
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
        }

        if (entityCopy != null && Method.TRACE.isSame(method)) {
            throw new IllegalStateException(Method.TRACE + " requests may not include an entity");
        }

        final BasicClassicHttpRequest result = new BasicClassicHttpRequest(method, getScheme(), getAuthority(), path);
        result.setVersion(getVersion());
        result.setHeaders(getHeaders());
        result.setEntity(entityCopy);
        result.setAbsoluteRequestUri(isAbsoluteRequestUri());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ClassicRequestBuilder [method=");
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
        builder.append(", entity=");
        builder.append(entity != null ? entity.getClass() : null);
        builder.append("]");
        return builder.toString();
    }

}
