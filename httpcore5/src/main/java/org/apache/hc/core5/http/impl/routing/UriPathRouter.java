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

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class UriPathRouter<P, T> implements Function<String, T> {

    private final BiFunction<String, List<PathRoute<P, T>>, T> pathRouter;
    private final List<PathRoute<P, T>> routes;

    UriPathRouter(final Function<String, P> compiler,
                  final BiFunction<String, List<PathRoute<P, T>>, T> pathRouter,
                  final List<PathRoute<String, T>> routes) {
        this.pathRouter = pathRouter;
        this.routes = Collections.unmodifiableList(routes.stream()
                .map(e -> new PathRoute<>(compiler.apply(e.pattern), e.handler))
                .collect(Collectors.toList()));

    }

    @Override
    public T apply(final String path) {
        return pathRouter.apply(path, routes);
    }

    @Override
    public String toString() {
        return routes.toString();
    }

    static <T> UriPathRouter<?, T> bestMatch(final List<PathRoute<String, T>> routes) {
        return new UriPathRouter<>(Function.identity(), BestMatcher.getInstance(), routes);
    }

    static <T> UriPathRouter<?, T> ordered(final List<PathRoute<String, T>> routes) {
        return new UriPathRouter<>(Function.identity(), OrderedMatcher.getInstance(), routes);
    }

    static <T> UriPathRouter<?, T> regEx(final List<PathRoute<String, T>> routes) {
        return new UriPathRouter<>(Pattern::compile, RegexMatcher.getInstance(), routes);
    }

    private static final PathPatternMatcher PATH_PATTERN_MATCHER = PathPatternMatcher.INSTANCE;

    /**
     * Finds a match for the given path from a collection of URI patterns.
     * <p>
     * Patterns may have three formats:
     * </p>
     * <ul>
     * <li>{@code *}</li>
     * <li>{@code *<uri-path>}</li>
     * <li>{@code <uri-path>*}</li>
     * </ul>
     * <p>
     * This class has no instance state.
     * </p>
     */
    final static class BestMatcher<T> implements BiFunction<String, List<PathRoute<String, T>>, T> {

        @SuppressWarnings("rawtypes") // raw by design
        private static final BestMatcher INSTANCE = new BestMatcher();

        @SuppressWarnings({ "cast", "unchecked" }) // cast to call site
        static <T> BestMatcher<T> getInstance() {
            return (BestMatcher<T>) INSTANCE;
        }

        private BestMatcher() {
            // singleton instance only
        }

        @Override
        public T apply(final String path, final List<PathRoute<String, T>> routes) {
            PathRoute<String, T> bestMatch = null;
            for (final PathRoute<String, T> route : routes) {
                if (route.pattern.equals(path)) {
                    return route.handler;
                }
                if (PATH_PATTERN_MATCHER.match(route.pattern, path)) {
                    // we have a match. is it any better?
                    if (bestMatch == null || PATH_PATTERN_MATCHER.isBetter(route.pattern, bestMatch.pattern)) {
                        bestMatch = route;
                    }
                }
            }
            return bestMatch != null ? bestMatch.handler : null;
        }

    }

    /**
     * Finds a match for the given path from an ordered collection of URI patterns.
     * <p>
     * Patterns may have three formats:
     * </p>
     * <ul>
     * <li>{@code *}</li>
     * <li>{@code *<uri-path>}</li>
     * <li>{@code <uri-path>*}</li>
     * </ul>
     * <p>
     * This class has no instance state.
     * </p>
     */
    final static class OrderedMatcher<T> implements BiFunction<String, List<PathRoute<String, T>>, T> {

        @SuppressWarnings("rawtypes") // raw by design
        private static final OrderedMatcher INSTANCE = new OrderedMatcher();

        @SuppressWarnings({ "cast", "unchecked" }) // cast to call site
        static <T> OrderedMatcher<T> getInstance() {
            return (OrderedMatcher<T>) INSTANCE;
        }

        private OrderedMatcher() {
            // singleton instance only
        }

        @Override
        public T apply(final String path, final List<PathRoute<String, T>> routes) {
            for (final PathRoute<String, T> route : routes) {
                final String pattern = route.pattern;
                if (path.equals(pattern)) {
                    return route.handler;
                }
                if (PATH_PATTERN_MATCHER.match(pattern, path)) {
                    return route.handler;
                }
            }
            return null;
        }
    }

    /**
     * Finds a match for the given path from a collection of regular expressions.
     * <p>
     * This class has no instance state.
     * </p>
     */
    final static class RegexMatcher<T> implements BiFunction<String, List<PathRoute<Pattern, T>>, T> {

        @SuppressWarnings("rawtypes") // raw by design
        private static final RegexMatcher INSTANCE = new RegexMatcher();

        @SuppressWarnings({ "cast", "unchecked" }) // cast to call site
        static <T> RegexMatcher<T> getInstance() {
            return (RegexMatcher<T>) INSTANCE;
        }

        private RegexMatcher() {
            // singleton instance only
        }

        @Override
        public T apply(final String path, final List<PathRoute<Pattern, T>> routes) {
            for (final PathRoute<Pattern, T> route : routes) {
                final Pattern pattern = route.pattern;
                if (pattern.matcher(path).matches()) {
                    return route.handler;
                }
            }
            return null;
        }

    }

}
