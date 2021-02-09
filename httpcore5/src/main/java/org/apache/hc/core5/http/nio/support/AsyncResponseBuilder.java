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

import java.util.Arrays;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.support.AbstractResponseBuilder;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link AsyncResponseProducer} instances.
 *
 * @since 5.0
 */
public class AsyncResponseBuilder extends AbstractResponseBuilder<AsyncResponseProducer>  {

    private AsyncEntityProducer entityProducer;

    AsyncResponseBuilder(final int status) {
        super(status);
    }

    public static AsyncResponseBuilder create(final int status) {
        Args.checkRange(status, 100, 599, "HTTP status code");
        return new AsyncResponseBuilder(status);
    }

    @Override
    public AsyncResponseBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public AsyncResponseBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public AsyncResponseBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public AsyncResponseBuilder addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public AsyncResponseBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public AsyncResponseBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public AsyncResponseBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public AsyncResponseBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    public AsyncEntityProducer getEntity() {
        return entityProducer;
    }

    public AsyncResponseBuilder setEntity(final AsyncEntityProducer entityProducer) {
        this.entityProducer = entityProducer;
        return this;
    }

    public AsyncResponseBuilder setEntity(final String content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncResponseBuilder setEntity(final String content) {
        this.entityProducer = new BasicAsyncEntityProducer(content);
        return this;
    }

    public AsyncResponseBuilder setEntity(final byte[] content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncResponseProducer build() {
        final BasicHttpResponse response = new BasicHttpResponse(getStatus());
        response.setVersion(getVersion());
        response.setHeaders(getHeaders());
        return new BasicResponseProducer(response, entityProducer);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AsyncResponseBuilder [status=");
        builder.append(getStatus());
        builder.append(", headerGroup=");
        builder.append(Arrays.toString(getHeaders()));
        builder.append(", entity=");
        builder.append(entityProducer != null ? entityProducer.getClass() : null);
        builder.append("]");
        return builder.toString();
    }

}
