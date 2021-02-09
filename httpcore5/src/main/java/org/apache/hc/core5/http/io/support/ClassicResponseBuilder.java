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

import java.util.Arrays;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.support.AbstractResponseBuilder;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link ClassicHttpResponse} instances.
 *
 * @since 5.0
 */
public class ClassicResponseBuilder extends AbstractResponseBuilder<ClassicHttpResponse> {

    private HttpEntity entity;

    ClassicResponseBuilder(final int status) {
        super(status);
    }

    public static ClassicResponseBuilder create(final int status) {
        Args.checkRange(status, 100, 599, "HTTP status code");
        return new ClassicResponseBuilder(status);
    }

    /**
     * @since 5.1
     */
    public static ClassicResponseBuilder copy(final ClassicHttpResponse response) {
        Args.notNull(response, "HTTP response");
        final ClassicResponseBuilder builder = new ClassicResponseBuilder(response.getCode());
        builder.digest(response);
        return builder;
    }

    protected void digest(final ClassicHttpResponse response) {
        super.digest(response);
        setEntity(response.getEntity());
    }

    @Override
    public ClassicResponseBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public ClassicResponseBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public ClassicResponseBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public ClassicResponseBuilder addHeader(final String name, final    String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public ClassicResponseBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public ClassicResponseBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public ClassicResponseBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public ClassicResponseBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
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
        final BasicClassicHttpResponse result = new BasicClassicHttpResponse(getStatus());
        result.setVersion(getVersion());
        result.setHeaders(getHeaders());
        result.setEntity(entity);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ClassicResponseBuilder [status=");
        builder.append(getStatus());
        builder.append(", headerGroup=");
        builder.append(Arrays.toString(getHeaders()));
        builder.append(", entity=");
        builder.append(entity != null ? entity.getClass() : null);
        builder.append("]");
        return builder.toString();
    }

}
