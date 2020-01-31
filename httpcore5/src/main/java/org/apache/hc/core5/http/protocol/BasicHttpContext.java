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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;

/**
 * Default implementation of {@link HttpContext}.
 * <p>
 * Please note instances of this class can be thread unsafe if the
 * parent context is not thread safe.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class BasicHttpContext implements HttpContext {

    private final HttpContext parentContext;
    private final Map<String, Object> map;

    private ProtocolVersion version;

    public BasicHttpContext() {
        this(null);
    }

    public BasicHttpContext(final HttpContext parentContext) {
        super();
        this.map = new ConcurrentHashMap<>();
        this.parentContext = parentContext;
    }

    @Override
    public Object getAttribute(final String id) {
        Objects.requireNonNull(id, "Id");
        Object obj = this.map.get(id);
        if (obj == null && this.parentContext != null) {
            obj = this.parentContext.getAttribute(id);
        }
        return obj;
    }

    @Override
    public Object setAttribute(final String id, final Object obj) {
        Objects.requireNonNull(id, "Id");
        if (obj != null) {
            return this.map.put(id, obj);
        }
        return this.map.remove(id);
    }

    @Override
    public Object removeAttribute(final String id) {
        Objects.requireNonNull(id, "Id");
        return this.map.remove(id);
    }

    /**
     * @since 5.0
     */
    @Override
    public ProtocolVersion getProtocolVersion() {
        return this.version != null ? this.version : HttpVersion.DEFAULT;
    }

    /**
     * @since 5.0
     */
    @Override
    public void setProtocolVersion(final ProtocolVersion version) {
        this.version = version;
    }

    /**
     * @since 4.2
     */
    public void clear() {
        this.map.clear();
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

}
