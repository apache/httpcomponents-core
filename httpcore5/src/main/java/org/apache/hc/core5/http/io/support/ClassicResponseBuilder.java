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

import java.util.Iterator;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link ClassicHttpResponse} instances.
 *
 * @since 5.0
 */
public class ClassicResponseBuilder {

    private int status;
    private ProtocolVersion version;
    private HeaderGroup headerGroup;
    private HttpEntity entity;

    ClassicResponseBuilder() {
    }

    ClassicResponseBuilder(final int status) {
        super();
        this.status = status;
    }

    public static ClassicResponseBuilder create(final int status) {
        Args.checkRange(status, 100, 599, "HTTP status code");
        return new ClassicResponseBuilder(status);
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public ClassicResponseBuilder setVersion(final ProtocolVersion version) {
        this.version = version;
        return this;
    }

    public Header[] getHeaders(final String name) {
        return headerGroup != null ? headerGroup.getHeaders(name) : null;
    }

    public ClassicResponseBuilder setHeaders(final Header... headers) {
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

    public ClassicResponseBuilder addHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.addHeader(header);
        return this;
    }

    public ClassicResponseBuilder addHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public ClassicResponseBuilder removeHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.removeHeader(header);
        return this;
    }

    public ClassicResponseBuilder removeHeaders(final String name) {
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

    public ClassicResponseBuilder setHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(header);
        return this;
    }

    public ClassicResponseBuilder setHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(new BasicHeader(name, value));
        return this;
    }

    public HttpEntity getEntity() {
        return entity;
    }

    public ClassicResponseBuilder setEntity(final HttpEntity entity) {
        this.entity = entity;
        return this;
    }

    public ClassicResponseBuilder setEntity(final String content, final ContentType contentType) {
        this.entity = new StringEntity(content, contentType);
        return this;
    }

    public ClassicResponseBuilder setEntity(final String content) {
        this.entity = new StringEntity(content);
        return this;
    }

    public ClassicResponseBuilder setEntity(final byte[] content, final ContentType contentType) {
        this.entity = new ByteArrayEntity(content, contentType);
        return this;
    }

    public ClassicHttpResponse build() {
        final ClassicHttpResponse result = new BasicClassicHttpResponse(status);
        result.setVersion(this.version != null ? this.version : HttpVersion.HTTP_1_1);
        if (this.headerGroup != null) {
            result.setHeaders(this.headerGroup.getHeaders());
        }
        result.setEntity(entity);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ClassicResponseBuilder [method=");
        builder.append(status);
        builder.append(", status=");
        builder.append(status);
        builder.append(", version=");
        builder.append(version);
        builder.append(", headerGroup=");
        builder.append(headerGroup);
        builder.append(", entity=");
        builder.append(entity != null ? entity.getClass() : null);
        builder.append("]");
        return builder.toString();
    }

}
