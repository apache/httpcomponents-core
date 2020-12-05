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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Args;

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
public class AsyncRequestBuilder {

    private String method;
    private URI uri;
    private Charset charset;
    private ProtocolVersion version;
    private HeaderGroup headerGroup;
    private AsyncEntityProducer entityProducer;
    private List<NameValuePair> parameters;

    AsyncRequestBuilder() {
    }

    AsyncRequestBuilder(final String method) {
        super();
        this.method = method;
    }

    AsyncRequestBuilder(final Method method) {
        this(method.name());
    }

    AsyncRequestBuilder(final String method, final URI uri) {
        super();
        this.method = method;
        this.uri = uri;
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

    public AsyncRequestBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public AsyncRequestBuilder setUri(final URI uri) {
        this.uri = uri;
        return this;
    }

    public AsyncRequestBuilder setUri(final String uri) {
        this.uri = uri != null ? URI.create(uri) : null;
        return this;
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public AsyncRequestBuilder setVersion(final ProtocolVersion version) {
        this.version = version;
        return this;
    }

    public Header[] getHeaders(final String name) {
        return headerGroup != null ? headerGroup.getHeaders(name) : null;
    }

    public AsyncRequestBuilder setHeaders(final Header... headers) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.setHeaders(headers);
        return this;
    }

    public Header getFirstHeader(final String name) {
        return headerGroup != null ? headerGroup.getFirstHeader(name) : null;
    }

    public Header getLastHeader(final String name) {
        return headerGroup != null ? headerGroup.getLastHeader(name) : null;
    }

    public AsyncRequestBuilder addHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.addHeader(header);
        return this;
    }

    public AsyncRequestBuilder addHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public AsyncRequestBuilder removeHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.removeHeader(header);
        return this;
    }

    public AsyncRequestBuilder removeHeaders(final String name) {
        if (name == null || headerGroup == null) {
            return this;
        }
        for (final Iterator<Header> i = headerGroup.headerIterator(); i.hasNext(); ) {
            final Header header = i.next();
            if (name.equalsIgnoreCase(header.getName())) {
                i.remove();
            }
        }
        return this;
    }

    public AsyncRequestBuilder setHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(header);
        return this;
    }

    public AsyncRequestBuilder setHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(new BasicHeader(name, value));
        return this;
    }

    public List<NameValuePair> getParameters() {
        return parameters != null ? new ArrayList<>(parameters) : new ArrayList<>();
    }

    public AsyncRequestBuilder addParameter(final NameValuePair nvp) {
        Args.notNull(nvp, "Name value pair");
        if (parameters == null) {
            parameters = new LinkedList<>();
        }
        parameters.add(nvp);
        return this;
    }

    public AsyncRequestBuilder addParameter(final String name, final String value) {
        return addParameter(new BasicNameValuePair(name, value));
    }

    public AsyncRequestBuilder addParameters(final NameValuePair... nvps) {
        for (final NameValuePair nvp: nvps) {
            addParameter(nvp);
        }
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
        URI uriCopy = uri != null ? uri : URI.create("/");
        AsyncEntityProducer entityProducerCopy = entityProducer;
        if (parameters != null && !parameters.isEmpty()) {
            if (entityProducerCopy == null && (Method.POST.isSame(method) || Method.PUT.isSame(method))) {
                final String content = WWWFormCodec.format(
                        parameters,
                        charset != null ? charset : ContentType.APPLICATION_FORM_URLENCODED.getCharset());
                entityProducerCopy = new StringAsyncEntityProducer(
                        content,
                        ContentType.APPLICATION_FORM_URLENCODED);
            } else {
                try {
                    uriCopy = new URIBuilder(uriCopy)
                      .setCharset(this.charset)
                      .addParameters(parameters)
                      .build();
                } catch (final URISyntaxException ex) {
                    // should never happen
                }
            }
        }

        if (entityProducerCopy != null && Method.TRACE.isSame(method)) {
            throw new IllegalStateException(Method.TRACE + " requests may not include an entity.");
        }

        final HttpRequest request = new BasicHttpRequest(method, uriCopy);
        if (this.headerGroup != null) {
            request.setHeaders(this.headerGroup.getHeaders());
        }
        if (version != null) {
            request.setVersion(version);
        }
        return new BasicRequestProducer(request, entityProducerCopy);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AsyncRequestBuilder [method=");
        builder.append(method);
        builder.append(", charset=");
        builder.append(charset);
        builder.append(", version=");
        builder.append(version);
        builder.append(", uri=");
        builder.append(uri);
        builder.append(", headerGroup=");
        builder.append(headerGroup);
        builder.append(", entity=");
        builder.append(entityProducer != null ? entityProducer.getClass() : null);
        builder.append(", parameters=");
        builder.append(parameters);
        builder.append("]");
        return builder.toString();
    }

}
