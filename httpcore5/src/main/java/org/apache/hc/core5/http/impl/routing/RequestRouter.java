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

package org.apache.hc.core5.http.impl.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

/**
 * Request mapper that can route requests based on their properties to a specific request handler.
 *
 * @param <T> request handler type.
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class RequestRouter<T> implements HttpRequestMapper<T> {

    @Internal
    public final static class Entry<T> {

        public final URIAuthority uriAuthority;
        public final PathRoute<String, T> route;

        public Entry(final URIAuthority uriAuthority, final String pathPattern, final T handler) {
            this.uriAuthority = uriAuthority;
            this.route = new PathRoute<>(pathPattern, handler);
        }

        public Entry(final String hostname, final String pathPattern, final T handler) {
            this(new URIAuthority(hostname), pathPattern, handler);
        }

        public Entry(final String pathPattern, final T handler) {
            this((URIAuthority) null, pathPattern, handler);
        }

        @Override
        public String toString() {
            return uriAuthority + "/" + route;
        }

    }

    static final class SingleAuthorityResolver<T> implements Function<URIAuthority, T> {

        private final URIAuthority singleAuthority;
        private final T router;

        SingleAuthorityResolver(final URIAuthority singleAuthority, final T router) {
            this.singleAuthority = Args.notNull(singleAuthority, "singleAuthority");
            this.router = router;
        }

        @Override
        public T apply(final URIAuthority authority) {
            return singleAuthority.equals(authority) ? router : null;
        }

        @Override
        public String toString() {
            return singleAuthority + " " + router;
        }

    }

    static final <T> Function<URIAuthority, T> noAuthorityResolver() {
       return u -> null;
    }

    @Internal
    public static <T> RequestRouter<T> create(final URIAuthority primaryAuthority,
                                              final UriPatternType patternType,
                                              final List<Entry<T>> handlerEntries,
                                              final BiFunction<String, URIAuthority, URIAuthority> authorityResolver,
                                              final HttpRequestMapper<T> downstream) {
        final Map<URIAuthority, Function<String, T>> authorityMap = handlerEntries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.uriAuthority != null ? e.uriAuthority : primaryAuthority != null ? primaryAuthority : LOCAL_AUTHORITY,
                        Collectors.mapping(e -> e.route,
                                Collectors.collectingAndThen(Collectors.toList(), e -> {
                                    switch (patternType) {
                                        case URI_PATTERN:
                                            return UriPathRouter.bestMatch(e);
                                        case URI_PATTERN_IN_ORDER:
                                            return UriPathRouter.ordered(e);
                                        case REGEX:
                                            return UriPathRouter.regEx(e);
                                        default:
                                            throw new IllegalStateException("Unexpected pattern type: " + patternType);
                                    }
                                }))));
        final Function<URIAuthority, Function<String, T>> authorityFunction;
        if (authorityMap.isEmpty()) {
            authorityFunction = noAuthorityResolver();
        } else if (authorityMap.size() == 1) {
            final Map.Entry<URIAuthority, Function<String, T>> entry = authorityMap.entrySet().iterator().next();
            authorityFunction = new SingleAuthorityResolver<>(entry.getKey(), entry.getValue());
        } else {
            authorityFunction = authorityMap::get;
        }
        return new RequestRouter<>(authorityFunction, authorityResolver, downstream);
    }

    public static <T> Builder<T> builder(final UriPatternType patternType) {
        return new Builder<>(patternType);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>(UriPatternType.URI_PATTERN);
    }

    public static final URIAuthority LOCAL_AUTHORITY = new URIAuthority("localhost");
    public final static BiFunction<String, URIAuthority, URIAuthority> LOCAL_AUTHORITY_RESOLVER = (scheme, authority) -> LOCAL_AUTHORITY;
    public final static BiFunction<String, URIAuthority, URIAuthority> IGNORE_PORT_AUTHORITY_RESOLVER = (scheme, authority) ->
            authority != null && authority.getPort() != -1 ? new URIAuthority(authority.getHostName(), -1) : authority;

    private final Function<URIAuthority, Function<String, T>> authorityRouter;
    private final BiFunction<String, URIAuthority, URIAuthority> authorityResolver;
    private final HttpRequestMapper<T> downstream;

    RequestRouter(final Function<URIAuthority, Function<String, T>> authorityRouter,
                  final BiFunction<String, URIAuthority, URIAuthority> authorityResolver,
                  final HttpRequestMapper<T> downstream) {
        this.authorityRouter = authorityRouter;
        this.authorityResolver = authorityResolver;
        this.downstream = downstream;
    }

    @Override
    public T resolve(final HttpRequest request, final HttpContext context) throws HttpException {
        final URIAuthority authority = authorityResolver != null ?
                authorityResolver.apply(request.getScheme(), request.getAuthority()) : request.getAuthority();
        final Function<String, T> pathRouter = authority != null ?
                authorityRouter.apply(authority) : null;
        if (pathRouter == null) {
            if (downstream != null) {
                return downstream.resolve(request, context);
            }
            throw new MisdirectedRequestException("Not authoritative");
        }
        String path = request.getPath();
        final int i = path.indexOf('?');
        if (i != -1) {
            path = path.substring(0, i);
        }
        return pathRouter.apply(path);
    }

    public static class Builder<T> {

        private final UriPatternType patternType;
        private final List<Entry<T>> handlerEntries;
        private BiFunction<String, URIAuthority, URIAuthority> authorityResolver;
        private HttpRequestMapper<T> downstream;

        Builder(final UriPatternType patternType) {
            this.patternType = patternType != null ? patternType : UriPatternType.URI_PATTERN;
            this.handlerEntries = new ArrayList<>();
        }

        /**
         * Adds a route with given authority and path pattern. Requests with the same authority and matching the path pattern
         * will be routed for execution to the handler.
         */
        public Builder<T> addRoute(final URIAuthority authority, final String pathPattern, final T handler) {
            Args.notNull(authority, "URI authority");
            Args.notBlank(pathPattern, "URI path pattern");
            Args.notNull(handler, "Handler");
            this.handlerEntries.add(new Entry<>(authority, pathPattern, handler));
            return this;
        }

        /**
         * Adds a route with given hostname and path pattern. Requests with the same hostname and matching the path pattern
         * will be routed for execution to the handler.
         */
        public Builder<T> addRoute(final String hostname, final String pathPattern, final T handler) {
            Args.notBlank(hostname, "Hostname");
            Args.notBlank(pathPattern, "URI path pattern");
            Args.notNull(handler, "Handler");
            this.handlerEntries.add(new Entry<>(hostname, pathPattern, handler));
            return this;
        }

        /**
         * Sets custom {@link URIAuthority} resolution {@link Function} that can be used to normalize or re-write
         * the authority specified in incoming requests prior to request routing. The function can return
         * a new {@link URIAuthority} instance representing an identity of the service authoritative to handle
         * the request or {@code null} if an authoritative service cannot be found or is unknown.
         */
        public Builder<T> resolveAuthority(final BiFunction<String, URIAuthority, URIAuthority> authorityResolver) {
            this.authorityResolver = authorityResolver;
            return this;
        }

        /**
         * Sets a downstream request mapper that can be used as a fallback in case no authoritative service can be found
         * to handle an incoming request. Using this method request mappers can be linked to form a chain of responsibility,
         * with each link representing a different authority.
         */
        public Builder<T> downstream(final HttpRequestMapper<T> downstream) {
            this.downstream = downstream;
            return this;
        }

        public RequestRouter<T> build() {
            return RequestRouter.create(null, patternType, handlerEntries, authorityResolver, downstream);
        }

    }

}
