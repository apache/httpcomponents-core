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

package org.apache.hc.core5.http.nio.support;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.support.AbstractRequestBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * Builder for {@link AsyncRequestProducer} instances.
 * <p>
 * Please note that this class treats parameters differently depending on composition
 * of the request: if the request has a content entity explicitly set with
 * {@link #setEntity(AsyncEntityProducer)} or it is not an entity enclosing method
 * (such as POST or PUT), parameters will be added to the query component of the request URI.
 * Otherwise, parameters will be added as a URL encoded entity.
 * </p>
 *
 * @since 5.0
 */
public class AsyncRequestBuilder extends AbstractRequestBuilder<AsyncRequestProducer> {

    private AsyncEntityProducer entityProducer;

    AsyncRequestBuilder(final String method) {
        super(method);
    }

    AsyncRequestBuilder(final Method method) {
        super(method);
    }

    AsyncRequestBuilder(final String method, final URI uri) {
        super(method, uri);
    }

    AsyncRequestBuilder(final Method method, final URI uri) {
        this(method.name(), uri);
    }

    AsyncRequestBuilder(final Method method, final String uri) {
        this(method.name(), uri != null ? URI.create(uri) : null);
    }

    AsyncRequestBuilder(final String method, final String uri) {
        this(method, uri != null ? URI.create(uri) : null);
    }

    public static AsyncRequestBuilder create(final String method) {
        Args.notBlank(method, "HTTP method");
        return new AsyncRequestBuilder(method);
    }

    public static AsyncRequestBuilder get() {
        return new AsyncRequestBuilder(Method.GET);
    }

    public static AsyncRequestBuilder get(final URI uri) {
        return new AsyncRequestBuilder(Method.GET, uri);
    }

    public static AsyncRequestBuilder get(final String uri) {
        return new AsyncRequestBuilder(Method.GET, uri);
    }

    public static AsyncRequestBuilder head() {
        return new AsyncRequestBuilder(Method.HEAD);
    }

    public static AsyncRequestBuilder head(final URI uri) {
        return new AsyncRequestBuilder(Method.HEAD, uri);
    }

    public static AsyncRequestBuilder head(final String uri) {
        return new AsyncRequestBuilder(Method.HEAD, uri);
    }

    public static AsyncRequestBuilder patch() {
        return new AsyncRequestBuilder(Method.PATCH);
    }

    public static AsyncRequestBuilder patch(final URI uri) {
        return new AsyncRequestBuilder(Method.PATCH, uri);
    }

    public static AsyncRequestBuilder patch(final String uri) {
        return new AsyncRequestBuilder(Method.PATCH, uri);
    }

    public static AsyncRequestBuilder post() {
        return new AsyncRequestBuilder(Method.POST);
    }

    public static AsyncRequestBuilder post(final URI uri) {
        return new AsyncRequestBuilder(Method.POST, uri);
    }

    public static AsyncRequestBuilder post(final String uri) {
        return new AsyncRequestBuilder(Method.POST, uri);
    }

    public static AsyncRequestBuilder put() {
        return new AsyncRequestBuilder(Method.PUT);
    }

    public static AsyncRequestBuilder put(final URI uri) {
        return new AsyncRequestBuilder(Method.PUT, uri);
    }

    public static AsyncRequestBuilder put(final String uri) {
        return new AsyncRequestBuilder(Method.PUT, uri);
    }

    public static AsyncRequestBuilder delete() {
        return new AsyncRequestBuilder(Method.DELETE);
    }

    public static AsyncRequestBuilder delete(final URI uri) {
        return new AsyncRequestBuilder(Method.DELETE, uri);
    }

    public static AsyncRequestBuilder delete(final String uri) {
        return new AsyncRequestBuilder(Method.DELETE, uri);
    }

    public static AsyncRequestBuilder trace() {
        return new AsyncRequestBuilder(Method.TRACE);
    }

    public static AsyncRequestBuilder trace(final URI uri) {
        return new AsyncRequestBuilder(Method.TRACE, uri);
    }

    public static AsyncRequestBuilder trace(final String uri) {
        return new AsyncRequestBuilder(Method.TRACE, uri);
    }

    public static AsyncRequestBuilder options() {
        return new AsyncRequestBuilder(Method.OPTIONS);
    }

    public static AsyncRequestBuilder options(final URI uri) {
        return new AsyncRequestBuilder(Method.OPTIONS, uri);
    }

    public static AsyncRequestBuilder options(final String uri) {
        return new AsyncRequestBuilder(Method.OPTIONS, uri);
    }

    @Override
    public AsyncRequestBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public AsyncRequestBuilder setUri(final URI uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public AsyncRequestBuilder setUri(final String uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public AsyncRequestBuilder setScheme(final String scheme) {
        super.setScheme(scheme);
        return this;
    }

    @Override
    public AsyncRequestBuilder setAuthority(final URIAuthority authority) {
        super.setAuthority(authority);
        return this;
    }

    /**
     * @since 5.1
     */
    @Override
    public AsyncRequestBuilder setHttpHost(final HttpHost httpHost) {
        super.setHttpHost(httpHost);
        return this;
    }

    @Override
    public AsyncRequestBuilder setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public AsyncRequestBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public AsyncRequestBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public AsyncRequestBuilder addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public AsyncRequestBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public AsyncRequestBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public AsyncRequestBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public AsyncRequestBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    @Override
    public AsyncRequestBuilder setCharset(final Charset charset) {
        super.setCharset(charset);
        return this;
    }

    @Override
    public AsyncRequestBuilder addParameter(final NameValuePair nvp) {
        super.addParameter(nvp);
        return this;
    }

    @Override
    public AsyncRequestBuilder addParameter(final String name, final String value) {
        super.addParameter(name, value);
        return this;
    }

    @Override
    public AsyncRequestBuilder addParameters(final NameValuePair... nvps) {
        super.addParameters(nvps);
        return this;
    }

    @Override
    public AsyncRequestBuilder setAbsoluteRequestUri(final boolean absoluteRequestUri) {
        super.setAbsoluteRequestUri(absoluteRequestUri);
        return this;
    }

    public AsyncEntityProducer getEntity() {
        return entityProducer;
    }

    public AsyncRequestBuilder setEntity(final AsyncEntityProducer entityProducer) {
        this.entityProducer = entityProducer;
        return this;
    }

    public AsyncRequestBuilder setEntity(final String content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncRequestBuilder setEntity(final String content) {
        this.entityProducer = new BasicAsyncEntityProducer(content);
        return this;
    }

    public AsyncRequestBuilder setEntity(final byte[] content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncRequestProducer build() {
        String path = getPath();
        if (TextUtils.isEmpty(path)) {
            path = "/";
        }
        AsyncEntityProducer entityProducerCopy = entityProducer;
        final String method = getMethod();
        final List<NameValuePair> parameters = getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            final Charset charset = getCharset();
            if (entityProducerCopy == null && (Method.POST.isSame(method) || Method.PUT.isSame(method))) {
                final String content = WWWFormCodec.format(
                        parameters,
                        charset != null ? charset : ContentType.APPLICATION_FORM_URLENCODED.getCharset());
                entityProducerCopy = new StringAsyncEntityProducer(
                        content,
                        ContentType.APPLICATION_FORM_URLENCODED);
            } else {
                try {
                    final URI uri = new URIBuilder(path)
                            .setCharset(charset)
                            .addParameters(parameters)
                            .build();
                    path = uri.toASCIIString();
                } catch (final URISyntaxException ex) {
                    // should never happen
                }
            }
        }

        if (entityProducerCopy != null && Method.TRACE.isSame(method)) {
            throw new IllegalStateException(Method.TRACE + " requests may not include an entity");
        }

        final BasicHttpRequest request = new BasicHttpRequest(method, getScheme(), getAuthority(), path);
        request.setVersion(getVersion());
        request.setHeaders(getHeaders());
        request.setAbsoluteRequestUri(isAbsoluteRequestUri());
        return new BasicRequestProducer(request, entityProducerCopy);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AsyncRequestBuilder [method=");
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
        builder.append(entityProducer != null ? entityProducer.getClass() : null);
        builder.append("]");
        return builder.toString();
    }

}
