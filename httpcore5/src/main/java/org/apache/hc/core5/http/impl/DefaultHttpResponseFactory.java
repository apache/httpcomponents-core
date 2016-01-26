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

package org.apache.hc.core5.http.impl;

import java.util.Locale;

import org.apache.hc.core5.annotation.Immutable;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ReasonPhraseCatalog;
import org.apache.hc.core5.http.StatusLine;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.BasicStatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Default factory for creating {@link HttpResponse} objects.
 *
 * @since 4.0
 */
@Immutable
public class DefaultHttpResponseFactory implements HttpResponseFactory {

    public static final DefaultHttpResponseFactory INSTANCE = new DefaultHttpResponseFactory();

    /** The catalog for looking up reason phrases. */
    protected final ReasonPhraseCatalog reasonCatalog;


    /**
     * Creates a new response factory with the given catalog.
     *
     * @param catalog   the catalog of reason phrases
     */
    public DefaultHttpResponseFactory(final ReasonPhraseCatalog catalog) {
        this.reasonCatalog = Args.notNull(catalog, "Reason phrase catalog");
    }

    /**
     * Creates a new response factory with the default catalog.
     * The default catalog is {@link EnglishReasonPhraseCatalog}.
     */
    public DefaultHttpResponseFactory() {
        this(EnglishReasonPhraseCatalog.INSTANCE);
    }


    // non-javadoc, see interface HttpResponseFactory
    @Override
    public HttpResponse newHttpResponse(final int status, final HttpContext context) {
        final Locale loc = determineLocale(context);
        final String reason   = this.reasonCatalog.getReason(status, loc);
        final StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, status, reason);
        return new BasicHttpResponse(statusline, this.reasonCatalog, loc);
    }


    // non-javadoc, see interface HttpResponseFactory
    @Override
    public HttpResponse newHttpResponse(
            final StatusLine statusline,
            final HttpContext context) {
        Args.notNull(statusline, "Status line");
        return new BasicHttpResponse(statusline, this.reasonCatalog, determineLocale(context));
    }

    /**
     * Determines the locale of the response.
     * The implementation in this class always returns the default locale.
     *
     * @param context   the context from which to determine the locale, or
     *                  {@code null} to use the default locale
     *
     * @return  the locale for the response, never {@code null}
     */
    protected Locale determineLocale(final HttpContext context) {
        return Locale.getDefault();
    }

}
