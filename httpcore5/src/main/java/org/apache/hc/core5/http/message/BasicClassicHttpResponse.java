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

import java.io.IOException;
import java.util.Locale;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ReasonPhraseCatalog;
import org.apache.hc.core5.io.Closer;

/**
 * Basic implementation of {@link ClassicHttpResponse}.
 *
 * @since 5.0
 */
public class BasicClassicHttpResponse extends BasicHttpResponse implements ClassicHttpResponse {

    private static final long serialVersionUID = 1L;
    private HttpEntity entity;

    /**
     * Creates a new response.
     *
     * @param code              the status code
     * @param catalog           the reason phrase catalog, or
     *                          {@code null} to disable automatic
     *                          reason phrase lookup
     * @param locale            the locale for looking up reason phrases, or
     *                          {@code null} for the system locale
     */
    public BasicClassicHttpResponse(final int code, final ReasonPhraseCatalog catalog, final Locale locale) {
        super(code, catalog, locale);
    }

    /**
     * Creates a new response.
     *
     * @param code          the status code of the response
     * @param reasonPhrase  the reason phrase to the status code, or {@code null}
     */
    public BasicClassicHttpResponse(final int code, final String reasonPhrase) {
        super(code, reasonPhrase);
    }

    /**
     * Creates a new response.
     *
     * @param code          the status code of the response
     */
    public BasicClassicHttpResponse(final int code) {
        super(code);
    }

    @Override
    public HttpEntity getEntity() {
        return this.entity;
    }

    @Override
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }

    @Override
    public void close() throws IOException {
        Closer.close(entity);
    }

}
