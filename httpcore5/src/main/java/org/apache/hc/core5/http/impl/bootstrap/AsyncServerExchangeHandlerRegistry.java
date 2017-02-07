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

package org.apache.hc.core5.http.impl.bootstrap;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.support.ImmediateResponseExchangeHandler;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

public class AsyncServerExchangeHandlerRegistry implements HandlerFactory<AsyncServerExchangeHandler> {

    private final static String LOCALHOST = "localhost";

    private final String canonicalHostName;
    private final UriPatternMatcher<Supplier<AsyncServerExchangeHandler>> primary;
    private final ConcurrentMap<String, UriPatternMatcher<Supplier<AsyncServerExchangeHandler>>> virtualMap;

    public AsyncServerExchangeHandlerRegistry(final String canonicalHostName) {
        this.canonicalHostName = Args.notNull(canonicalHostName, "Canonical hostname").toLowerCase(Locale.ROOT);
        this.primary = new UriPatternMatcher<>();
        this.virtualMap = new ConcurrentHashMap<>();
    }

    private UriPatternMatcher<Supplier<AsyncServerExchangeHandler>> getPatternMatcher(final String hostname) {
        if (hostname == null) {
            return primary;
        }
        if (hostname.equals(canonicalHostName) || hostname.equals(LOCALHOST)) {
            return primary;
        }
        return virtualMap.get(hostname);
    }

    @Override
    public AsyncServerExchangeHandler create(final HttpRequest request) throws HttpException {
        final URIAuthority authority = request.getAuthority();
        final String key = authority != null ? authority.getHostName().toLowerCase(Locale.ROOT) : null;
        final UriPatternMatcher<Supplier<AsyncServerExchangeHandler>> patternMatcher = getPatternMatcher(key);
        if (patternMatcher == null) {
            return new ImmediateResponseExchangeHandler(HttpStatus.SC_MISDIRECTED_REQUEST, "Not authoritative");
        }
        String path = request.getPath();
        final int i = path.indexOf("?");
        if (i != -1) {
            path = path.substring(0, i - 1);
        }
        final Supplier<AsyncServerExchangeHandler> supplier = patternMatcher.lookup(path);
        if (supplier != null) {
            return supplier.get();
        }
        return new ImmediateResponseExchangeHandler(HttpStatus.SC_NOT_FOUND, "Resource not found");
    }

    public void register(final String hostname, final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        final String key = hostname != null ? hostname.toLowerCase(Locale.ROOT) : null;
        if (hostname == null || hostname.equals(canonicalHostName) || hostname.equals(LOCALHOST)) {
            primary.register(uriPattern, supplier);
        } else {
            UriPatternMatcher<Supplier<AsyncServerExchangeHandler>> matcher = virtualMap.get(key);
            if (matcher == null) {
                final UriPatternMatcher<Supplier<AsyncServerExchangeHandler>> newMatcher = new UriPatternMatcher<>();
                matcher = virtualMap.putIfAbsent(key, newMatcher);
                if (matcher == null) {
                    matcher = newMatcher;
                }
            }
            matcher.register(uriPattern, supplier);
        }
    }

}
