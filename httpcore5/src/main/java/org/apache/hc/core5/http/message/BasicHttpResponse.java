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

import java.util.Locale;

import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.ReasonPhraseCatalog;
import org.apache.hc.core5.http.impl.EnglishReasonPhraseCatalog;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * Basic implementation of {@link HttpResponse}.
 *
 * @since 4.0
 */
public class BasicHttpResponse extends HeaderGroup implements HttpResponse {

    private static final long serialVersionUID = 1L;

    private final ReasonPhraseCatalog reasonCatalog;

    private ProtocolVersion version;
    private Locale locale;
    private int code;
    private String reasonPhrase;

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
    public BasicHttpResponse(
            final int code,
            final ReasonPhraseCatalog catalog,
            final Locale locale) {
        super();
        this.code = Args.positive(code, "Status code");
        this.reasonCatalog = catalog != null ? catalog : EnglishReasonPhraseCatalog.INSTANCE;
        this.locale = locale;
    }

    /**
     * Creates a new response.
     *
     * @param code          the status code of the response
     * @param reasonPhrase  the reason phrase to the status code, or {@code null}
     */
    public BasicHttpResponse(final int code, final String reasonPhrase) {
        this.code = Args.positive(code, "Status code");
        this.reasonPhrase = reasonPhrase;
        this.reasonCatalog = EnglishReasonPhraseCatalog.INSTANCE;
    }

    /**
     * Creates a new response.
     *
     * @param code          the status code of the response
     */
    public BasicHttpResponse(final int code) {
        this.code = Args.positive(code, "Status code");
        this.reasonPhrase = null;
        this.reasonCatalog = EnglishReasonPhraseCatalog.INSTANCE;
    }

    @Override
    public void addHeader(final String name, final Object value) {
        Args.notNull(name, "Header name");
        addHeader(new BasicHeader(name, value));
    }

    @Override
    public void setHeader(final String name, final Object value) {
        Args.notNull(name, "Header name");
        setHeader(new BasicHeader(name, value));
    }

    @Override
    public void setVersion(final ProtocolVersion version) {
        this.version = version;
    }

    @Override
    public ProtocolVersion getVersion() {
        return this.version;
    }

    @Override
    public int getCode() {
        return this.code;
    }

    @Override
    public Locale getLocale() {
        return this.locale;
    }

    @Override
    public void setCode(final int code) {
        Args.positive(code, "Status code");
        this.code = code;
        this.reasonPhrase = null;
    }

    @Override
    public String getReasonPhrase() {
        return this.reasonPhrase != null ? this.reasonPhrase : getReason(this.code);
    }

    @Override
    public void setReasonPhrase(final String reason) {
        this.reasonPhrase = TextUtils.isBlank(reason) ? null : reason;
    }

    @Override
    public void setLocale(final Locale locale) {
        this.locale = Args.notNull(locale, "Locale");
    }

    /**
     * Looks up a reason phrase.
     * This method evaluates the currently set catalog and locale.
     * It also handles a missing catalog.
     *
     * @param code      the status code for which to look up the reason
     *
     * @return  the reason phrase, or {@code null} if there is none
     */
    protected String getReason(final int code) {
        return this.reasonCatalog != null ? this.reasonCatalog.getReason(code,
                this.locale != null ? this.locale : Locale.getDefault()) : null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.code).append(' ').append(this.reasonPhrase).append(' ').append(this.version);
        return sb.toString();
    }

}
