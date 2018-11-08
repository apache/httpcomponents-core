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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.net.URIAuthority;

/**
 * {@link HttpRequest} wrapper.
 */
public class HttpRequestWrapper extends AbstractMessageWrapper implements HttpRequest {

    private final HttpRequest message;

    public HttpRequestWrapper(final HttpRequest message) {
        super(message);
        this.message = message;
    }

    @Override
    public String getMethod() {
        return message.getMethod();
    }

    @Override
    public String getPath() {
        return message.getPath();
    }

    @Override
    public void setPath(final String path) {
        message.setPath(path);
    }

    @Override
    public String getScheme() {
        return message.getScheme();
    }

    @Override
    public void setScheme(final String scheme) {
        message.setScheme(scheme);
    }

    @Override
    public URIAuthority getAuthority() {
        return message.getAuthority();
    }

    @Override
    public void setAuthority(final URIAuthority authority) {
        message.setAuthority(authority);
    }

    @Override
    public String getRequestUri() {
        return message.getRequestUri();
    }

    @Override
    public URI getUri() throws URISyntaxException {
        return message.getUri();
    }

    @Override
    public void setUri(final URI requestUri) {
        message.setUri(requestUri);
    }

}
