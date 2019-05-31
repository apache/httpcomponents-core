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

import java.util.Iterator;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link AsyncPushProducer} instances.
 *
 * @since 5.0
 */
public class AsyncPushBuilder {

    private int status;
    private HeaderGroup headerGroup;
    private AsyncEntityProducer entityProducer;

    AsyncPushBuilder() {
    }

    AsyncPushBuilder(final int status) {
        super();
        this.status = status;
    }

    public static AsyncPushBuilder create(final int status) {
        Args.checkRange(status, 100, 599, "HTTP status code");
        return new AsyncPushBuilder(status);
    }

    public Header[] getHeaders(final String name) {
        return headerGroup != null ? headerGroup.getHeaders(name) : null;
    }

    public AsyncPushBuilder setHeaders(final Header... headers) {
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

    public AsyncPushBuilder addHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.addHeader(header);
        return this;
    }

    public AsyncPushBuilder addHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public AsyncPushBuilder removeHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.removeHeader(header);
        return this;
    }

    public AsyncPushBuilder removeHeaders(final String name) {
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

    public AsyncPushBuilder setHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(header);
        return this;
    }

    public AsyncPushBuilder setHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(new BasicHeader(name, value));
        return this;
    }

    public AsyncEntityProducer getEntity() {
        return entityProducer;
    }

    public AsyncPushBuilder setEntity(final AsyncEntityProducer entityProducer) {
        this.entityProducer = entityProducer;
        return this;
    }

    public AsyncPushBuilder setEntity(final String content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncPushBuilder setEntity(final String content) {
        this.entityProducer = new BasicAsyncEntityProducer(content);
        return this;
    }

    public AsyncPushBuilder setEntity(final byte[] content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncPushProducer build() {
        final HttpResponse response = new BasicHttpResponse(status);
        if (this.headerGroup != null) {
            response.setHeaders(this.headerGroup.getHeaders());
        }
        return new BasicPushProducer(response, entityProducer);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AsyncPushProducer [method=");
        builder.append(status);
        builder.append(", status=");
        builder.append(status);
        builder.append(", headerGroup=");
        builder.append(headerGroup);
        builder.append(", entity=");
        builder.append(entityProducer != null ? entityProducer.getClass() : null);
        builder.append("]");
        return builder.toString();
    }

}
