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
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;

/**
 * Builder for {@link BasicHttpRequest} instances.
 *
 * @since 5.1
 */
public abstract class AbstractResponseBuilder<T> extends AbstractMessageBuilder<T> {

    private int status;

    protected AbstractResponseBuilder(final int status) {
        super();
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(final int status) {
        this.status = status;
    }

    @Override
    public AbstractResponseBuilder<T> setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public AbstractResponseBuilder<T> setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public AbstractResponseBuilder<T> addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public AbstractResponseBuilder<T> addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public AbstractResponseBuilder<T> removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public AbstractResponseBuilder<T> removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public AbstractResponseBuilder<T> setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public AbstractResponseBuilder<T> setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    protected abstract T build();

}
