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

import java.util.Iterator;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;

/**
 * Abstract {@link HttpMessage} builder.
 *
 * @since 5.1
 */
public abstract class AbstractMessageBuilder<T> {

    private ProtocolVersion version;
    private HeaderGroup headerGroup;

    protected AbstractMessageBuilder() {
    }

    protected void digest(final HttpMessage message) {
        if (message == null) {
            return;
        }
        setVersion(message.getVersion());
        setHeaders(message.headerIterator());
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public AbstractMessageBuilder<T> setVersion(final ProtocolVersion version) {
        this.version = version;
        return this;
    }

    public Header[] getHeaders() {
        return headerGroup != null ? headerGroup.getHeaders() : null;
    }

    public Header[] getHeaders(final String name) {
        return headerGroup != null ? headerGroup.getHeaders(name) : null;
    }

    public AbstractMessageBuilder<T> setHeaders(final Header... headers) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.setHeaders(headers);
        return this;
    }

    public AbstractMessageBuilder<T> setHeaders(final Iterator<Header> it) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        } else {
            headerGroup.clear();
        }
        while (it.hasNext()) {
            headerGroup.addHeader(it.next());
        }
        return this;
    }

    public Header[] getFirstHeaders() {
        return headerGroup != null ? headerGroup.getHeaders() : null;
    }

    public Header getFirstHeader(final String name) {
        return headerGroup != null ? headerGroup.getFirstHeader(name) : null;
    }

    public Header getLastHeader(final String name) {
        return headerGroup != null ? headerGroup.getLastHeader(name) : null;
    }

    public AbstractMessageBuilder<T> addHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.addHeader(header);
        return this;
    }

    public AbstractMessageBuilder<T> addHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public AbstractMessageBuilder<T> removeHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.removeHeader(header);
        return this;
    }

    public AbstractMessageBuilder<T> removeHeaders(final String name) {
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

    public AbstractMessageBuilder<T> setHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.setHeader(header);
        return this;
    }

    public AbstractMessageBuilder<T> setHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.setHeader(new BasicHeader(name, value));
        return this;
    }

    protected abstract T build();

}
