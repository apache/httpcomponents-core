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

package org.apache.hc.core5.http;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.core5.net.URIAuthority;

/**
 * A request message from a client to a server includes, within the
 * first line of that message, the method to be applied to the resource,
 * the identifier of the resource, and the protocol version in use.
 *
 * @since 4.0
 */
public interface HttpRequest extends HttpMessage {

    /**
     * Returns method of this request message.
     *
     * @return  the request method.
     */
    String getMethod();

    /**
     * Returns URI path of this request message or {@code null} if not set.
     *
     * @return  the request URI or {@code null}.
     */
    String getPath();

    /**
     * Sets URI path of this request message.
     *
     * @since 5.0
     */
    void setPath(String path);

    /**
     * Returns scheme of this request message.
     *
     * @return  the scheme or {@code null}.
     *
     * @since 5.0
     */
    String getScheme();

    /**
     * Sets scheme of this request message.
     *
     * @since 5.0
     */
    void setScheme(String scheme);

    /**
     * Returns authority of this request message.
     *
     * @return  the authority or {@code null}.
     *
     * @since 5.0
     */
    URIAuthority getAuthority();

    /**
     * Sets authority of this request message.
     *
     * @since 5.0
     */
    void setAuthority(URIAuthority authority);

    /**
     * Returns request URI of this request message. It may be an absolute or relative URI.
     * Applicable to HTTP/1.1 version or earlier.
     *
     * @return  the request URI.
     *
     * @since 5.0
     */
    String getRequestUri();

    /**
     * Returns full request URI of this request message.
     *
     * @return  the request URI.
     *
     * @since 5.0
     */
    URI getUri() throws URISyntaxException;

    /**
     * Sets the full request URI of this request message.
     *
     * @param requestUri the request URI.
     *
     * @since 5.0
     */
    void setUri(final URI requestUri);

}
