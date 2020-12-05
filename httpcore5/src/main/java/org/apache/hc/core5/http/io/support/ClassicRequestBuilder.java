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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;

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
public class ClassicRequestBuilder {

    private String method;
    private URI uri;
    private Charset charset;
    private ProtocolVersion version;
    private HeaderGroup headerGroup;
    private HttpEntity entity;
    private List<NameValuePair> parameters;

    ClassicRequestBuilder() {
    }

    ClassicRequestBuilder(final String method) {
        super();
        this.method = method;
    }

    ClassicRequestBuilder(final Method method) {
        this(method.name());
    }

    ClassicRequestBuilder(final String method, final URI uri) {
        super();
        this.method = method;
        this.uri = uri;
    }

    ClassicRequestBuilder(final Method method, final URI uri) {
        this(method.name(), uri);
    }

    ClassicRequestBuilder(final Method method, final String uri) {
        this(method.name(), uri != null ? URI.create(uri) : null);
    }

    ClassicRequestBuilder(final String method, final String uri) {
        this(method, uri != null ? URI.create(uri) : null);
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

    public ClassicRequestBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getMethod() {
        return method;
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public ClassicRequestBuilder setVersion(final ProtocolVersion version) {
        this.version = version;
        return this;
    }

    public URI getUri() {
        return uri;
    }

    public ClassicRequestBuilder setUri(final URI uri) {
        this.uri = uri;
        return this;
    }

    public ClassicRequestBuilder setUri(final String uri) {
        this.uri = uri != null ? URI.create(uri) : null;
        return this;
    }

    public Header[] getHeaders(final String name) {
        return headerGroup != null ? headerGroup.getHeaders(name) : null;
    }

    public ClassicRequestBuilder setHeaders(final Header... headers) {
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

    public ClassicRequestBuilder addHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.addHeader(header);
        return this;
    }

    public ClassicRequestBuilder addHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public ClassicRequestBuilder removeHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.removeHeader(header);
        return this;
    }

    public ClassicRequestBuilder removeHeaders(final String name) {
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

    public ClassicRequestBuilder setHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(header);
        return this;
    }

    public ClassicRequestBuilder setHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(new BasicHeader(name, value));
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

    public List<NameValuePair> getParameters() {
        return parameters != null ? new ArrayList<>(parameters) :
                new ArrayList<>();
    }

    public ClassicRequestBuilder addParameter(final NameValuePair nvp) {
        Args.notNull(nvp, "Name value pair");
        if (parameters == null) {
            parameters = new LinkedList<>();
        }
        parameters.add(nvp);
        return this;
    }

    public ClassicRequestBuilder addParameter(final String name, final String value) {
        return addParameter(new BasicNameValuePair(name, value));
    }

    public ClassicRequestBuilder addParameters(final NameValuePair... nvps) {
        for (final NameValuePair nvp : nvps) {
            addParameter(nvp);
        }
        return this;
    }

    public ClassicHttpRequest build() {
        URI uriCopy = this.uri != null ? this.uri : URI.create("/");
        HttpEntity entityCopy = this.entity;
        if (parameters != null && !parameters.isEmpty()) {
            if (entityCopy == null && (Method.POST.isSame(method) || Method.PUT.isSame(method))) {
                entityCopy = HttpEntities.createUrlEncoded(parameters, charset);
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

        if (entityCopy != null && Method.TRACE.isSame(method)) {
            throw new IllegalStateException(Method.TRACE + " requests may not include an entity");
        }

        final ClassicHttpRequest result = new BasicClassicHttpRequest(method, uriCopy);
        result.setVersion(this.version != null ? this.version : HttpVersion.HTTP_1_1);
        if (this.headerGroup != null) {
            result.setHeaders(this.headerGroup.getHeaders());
        }
        result.setEntity(entityCopy);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ClassicRequestBuilder [method=");
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
        builder.append(entity != null ? entity.getClass() : null);
        builder.append(", parameters=");
        builder.append(parameters);
        builder.append("]");
        return builder.toString();
    }

}
