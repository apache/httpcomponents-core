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

package org.apache.hc.core5.http.message;

import java.util.Iterator;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;

/**
 * Abstract {@link HttpMessage} wrapper.
 */
public abstract class AbstractMessageWrapper implements HttpMessage {

    private final HttpMessage message;

    public AbstractMessageWrapper(final HttpMessage message) {
        this.message = Args.notNull(message, "Message");
    }

    @Override
    public void setVersion(final ProtocolVersion version) {
        message.setVersion(version);
    }

    @Override
    public ProtocolVersion getVersion() {
        return message.getVersion();
    }

    @Override
    public void addHeader(final Header header) {
        message.addHeader(header);
    }

    @Override
    public void addHeader(final String name, final Object value) {
        message.addHeader(name, value);
    }

    @Override
    public void setHeader(final Header header) {
        message.setHeader(header);
    }

    @Override
    public void setHeader(final String name, final Object value) {
        message.setHeader(name, value);
    }

    @Override
    public void setHeaders(final Header... headers) {
        message.setHeaders(headers);
    }

    @Override
    public boolean removeHeader(final Header header) {
        return message.removeHeader(header);
    }

    @Override
    public boolean removeHeaders(final String name) {
        return message.removeHeaders(name);
    }

    @Override
    public boolean containsHeader(final String name) {
        return message.containsHeader(name);
    }

    @Override
    public int countHeaders(final String name) {
        return message.countHeaders(name);
    }

    @Override
    public Header[] getHeaders(final String name) {
        return message.getHeaders(name);
    }

    @Override
    public Header getHeader(final String name) throws ProtocolException {
        return message.getHeader(name);
    }

    @Override
    public Header getFirstHeader(final String name) {
        return message.getFirstHeader(name);
    }

    @Override
    public Header getLastHeader(final String name) {
        return message.getLastHeader(name);
    }

    @Override
    public Header[] getHeaders() {
        return message.getHeaders();
    }

    @Override
    public Iterator<Header> headerIterator() {
        return message.headerIterator();
    }

    @Override
    public Iterator<Header> headerIterator(final String name) {
        return message.headerIterator(name);
    }

    @Override
    public String toString() {
        return message.toString();
    }

}
