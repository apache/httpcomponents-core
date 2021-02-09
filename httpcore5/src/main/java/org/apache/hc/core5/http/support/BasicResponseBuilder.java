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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.util.Args;

import java.util.Arrays;

/**
 * Builder for {@link BasicHttpResponse} instances.
 *
 * @since 5.1
 */
public class BasicResponseBuilder extends AbstractResponseBuilder<BasicHttpResponse> {

    protected BasicResponseBuilder(final int status) {
        super(status);
    }

    public static BasicResponseBuilder create(final int status) {
        Args.checkRange(status, 100, 599, "HTTP status code");
        return new BasicResponseBuilder(status);
    }

    public static BasicResponseBuilder copy(final HttpResponse response) {
        Args.notNull(response, "HTTP response");
        final BasicResponseBuilder builder = new BasicResponseBuilder(response.getCode());
        builder.digest(response);
        return builder;
    }

    @Override
    public BasicResponseBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public BasicResponseBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public BasicResponseBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public BasicResponseBuilder addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public BasicResponseBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public BasicResponseBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public BasicResponseBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public BasicResponseBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    @Override
    public BasicHttpResponse build() {
        final BasicHttpResponse result = new BasicHttpResponse(getStatus());
        result.setVersion(getVersion());
        result.setHeaders(getHeaders());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("BasicResponseBuilder [status=");
        builder.append(getStatus());
        builder.append(", headerGroup=");
        builder.append(Arrays.toString(getHeaders()));
        builder.append("]");
        return builder.toString();
    }

}
