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

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.net.URIAuthority;

import java.net.URI;

/**
 * Basic implementation of {@link ClassicHttpRequest}.
 *
 * @since 5.0
 */
public class BasicClassicHttpRequest extends BasicHttpRequest implements ClassicHttpRequest {

    private static final long serialVersionUID = 1L;

    private HttpEntity entity;

    /**
     * Creates request message with the given method, host and request path.
     *
     * @param method request method.
     * @param scheme request scheme.
     * @param authority request authority.
     * @param path request path.
     *
     * @since 5.1
     */
    public BasicClassicHttpRequest(final String method, final String scheme, final URIAuthority authority, final String path) {
        super(method, scheme, authority, path);
    }

    /**
     * Creates request message with the given method and request path.
     *
     * @param method request method.
     * @param path request path.
     */
    public BasicClassicHttpRequest(final String method, final String path) {
        super(method, path);
    }

    /**
     * Creates request message with the given method, host and request path.
     *
     * @param method request method.
     * @param host request host.
     * @param path request path.
     */
    public BasicClassicHttpRequest(final String method, final HttpHost host, final String path) {
        super(method, host, path);
    }

    /**
     * Creates request message with the given method, request URI.
     *
     * @param method request method.
     * @param requestUri request URI.
     */
    public BasicClassicHttpRequest(final String method, final URI requestUri) {
        super(method, requestUri);
    }

    /**
     * Creates request message with the given method and request path.
     *
     * @param method request method.
     * @param path request path.
     */
    public BasicClassicHttpRequest(final Method method, final String path) {
        super(method, path);
    }

    /**
     * Creates request message with the given method, host and request path.
     *
     * @param method request method.
     * @param host request host.
     * @param path request path.
     */
    public BasicClassicHttpRequest(final Method method, final HttpHost host, final String path) {
        super(method, host, path);
    }

    /**
     * Creates request message with the given method, request URI.
     *
     * @param method request method.
     * @param requestUri request URI.
     */
    public BasicClassicHttpRequest(final Method method, final URI requestUri) {
        super(method, requestUri);
    }

    @Override
    public HttpEntity getEntity() {
        return this.entity;
    }

    @Override
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }

}
