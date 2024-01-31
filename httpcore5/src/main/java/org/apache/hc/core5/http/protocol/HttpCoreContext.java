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

package org.apache.hc.core5.http.protocol;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;

/**
 * Core execution {@link HttpContext}.
 * <p>
 * IMPORTANT: This class is NOT thread-safe and MUST NOT be used concurrently by
 * multiple message exchanges.
 *
 * @since 4.3
 */
public class HttpCoreContext implements HttpContext {

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String CONNECTION_ENDPOINT  = HttpContext.RESERVED_PREFIX + "connection-endpoint";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String SSL_SESSION = HttpContext.RESERVED_PREFIX + "ssl-session";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String HTTP_REQUEST     = HttpContext.RESERVED_PREFIX + "request";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String HTTP_RESPONSE    = HttpContext.RESERVED_PREFIX + "response";

    public static HttpCoreContext create() {
        return new HttpCoreContext();
    }

    /**
     * @deprecated Use {@link #cast(HttpContext)}.
     */
    @Deprecated
    public static HttpCoreContext adapt(final HttpContext context) {
        if (context == null) {
            return new HttpCoreContext();
        }
        if (context instanceof HttpCoreContext) {
            return (HttpCoreContext) context;
        }
        return new HttpCoreContext(context);
    }

    /**
     * Casts the given generic {@link HttpContext} as {@link HttpCoreContext}
     * or creates a new {@link HttpCoreContext} with the given {@link HttpContext}
     * as a parent.
     *
     * @since 5.3
     */
    public static HttpCoreContext cast(final HttpContext context) {
        if (context instanceof HttpCoreContext) {
            return (HttpCoreContext) context;
        }
        return new HttpCoreContext(context);
    }

    private final HttpContext parentContext;
    private Map<String, Object> map;
    private ProtocolVersion version;
    private HttpRequest request;
    private HttpResponse response;
    private EndpointDetails endpointDetails;
    private SSLSession sslSession;

    public HttpCoreContext(final HttpContext parentContext) {
        super();
        this.parentContext = parentContext;
    }

    public HttpCoreContext() {
        super();
        this.parentContext = null;
    }

    /**
     * Represents the protocol version used by the message exchange.
     * <p>
     * This context attribute is expected to be populated by the protocol handler
     * in the course of request execution.
     *
     * @since 5.0
     */
    @Override
    public ProtocolVersion getProtocolVersion() {
        return this.version != null ? this.version : HttpVersion.HTTP_1_1;
    }

    /**
     * @since 5.0
     */
    @Override
    public void setProtocolVersion(final ProtocolVersion version) {
        this.version = version;
    }

    @Override
    public Object getAttribute(final String id) {
        Object o = map != null ? map.get(id) : null;
        if (o == null && parentContext != null) {
            o = parentContext.getAttribute(id);
        }
        return o;
    }

    @Override
    public Object setAttribute(final String id, final Object obj) {
        if (map == null) {
            map = new HashMap<>();
        }
        return map.put(id, obj);
    }

    @Override
    public Object removeAttribute(final String id) {
        if (map != null) {
            return map.remove(id);
        } else {
            return null;
        }
    }

    public <T> T getAttribute(final String id, final Class<T> clazz) {
        Args.notNull(clazz, "Attribute class");
        final Object obj = getAttribute(id);
        if (obj == null) {
            return null;
        }
        return clazz.cast(obj);
    }

    /**
     * Represents current request message head.
     * <p>
     * This context attribute is expected to be populated by the protocol handler
     * in the course of request execution.
     */
    public HttpRequest getRequest() {
        return request;
    }

    /**
     * @since 5.3
     */
    public void setRequest(final HttpRequest request) {
        this.request = request;
    }

    /**
     * Represents current response message head.
     * <p>
     * This context attribute is expected to be populated by the protocol handler
     * in the course of request execution.
     */
    public HttpResponse getResponse() {
        return response;
    }

    /**
     * @since 5.3
     */
    public void setResponse(final HttpResponse response) {
        this.response = response;
    }

    /**
     * Represents current connection endpoint details.
     * <p>
     * This context attribute is expected to be populated by the protocol handler
     * in the course of request execution.
     * @since 5.0
     */
    public EndpointDetails getEndpointDetails() {
        return endpointDetails;
    }

    /**
     * @since 5.3
     */
    public void setEndpointDetails(final EndpointDetails endpointDetails) {
        this.endpointDetails = endpointDetails;
    }

    /**
     * Represents current TLS session details.
     * <p>
     * This context attribute is expected to be populated by the protocol handler
     * in the course of request execution.
     * @since 5.0
     */
    public SSLSession getSSLSession() {
        return sslSession;
    }

    /**
     * @since 5.3
     */
    public void setSSLSession(final SSLSession sslSession) {
        this.sslSession = sslSession;
    }

    @Override
    public String toString() {
        return "HttpCoreContext{" +
                "version=" + version +
                ", request=" + request +
                ", response=" + response +
                ", endpointDetails=" + endpointDetails +
                ", sslSession=" + sslSession +
                '}';
    }

}
