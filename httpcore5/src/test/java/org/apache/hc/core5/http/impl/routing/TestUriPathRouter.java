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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestUriPathRouter {

    @Test
    void testBestMatchWildCardMatching() {
        final UriPathRouter.BestMatcher<Long> matcher = UriPathRouter.BestMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("*", 0L),
                new PathRoute<>("/one/*", 1L),
                new PathRoute<>("/one/two/*", 2L),
                new PathRoute<>("/one/two/three/*", 3L),
                new PathRoute<>("*.view", 4L),
                new PathRoute<>("*.form", 5L));

        Assertions.assertEquals(1L, matcher.apply("/one/request", routes));
        Assertions.assertEquals(2L, matcher.apply("/one/two/request", routes));
        Assertions.assertEquals(3L, matcher.apply("/one/two/three/request", routes));
        Assertions.assertEquals(0L, matcher.apply("default/request", routes));
        Assertions.assertEquals(4L, matcher.apply("that.view", routes));
        Assertions.assertEquals(5L, matcher.apply("that.form", routes));
        Assertions.assertEquals(0L, matcher.apply("whatever", routes));
    }

    @Test
    void testBestMatchWildCardMatchingSuffixPrefixPrecedence() {
        final UriPathRouter.BestMatcher<Long> matcher = UriPathRouter.BestMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("/ma*", 1L),
                new PathRoute<>("*tch", 2L));
        Assertions.assertEquals(1L, matcher.apply("/match", routes));
    }

    @Test
    void testBestMatchWildCardMatchingExactMatch() {
        final UriPathRouter.BestMatcher<Long> matcher = UriPathRouter.BestMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("exact", 1L),
                new PathRoute<>("*", 0L));
        Assertions.assertEquals(1L, matcher.apply("exact", routes));
    }

    @Test
    void testBestMatchWildCardMatchingNoMatch() {
        final UriPathRouter.BestMatcher<Long> matcher = UriPathRouter.BestMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("/this/*", 1L),
                new PathRoute<>("/that/*", 2L));
        Assertions.assertNull(matcher.apply("huh?", routes));
    }

    @Test
    void testOrderedWildCardMatching1() {
        final UriPathRouter.OrderedMatcher<Long> matcher = UriPathRouter.OrderedMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("*", 0L),
                new PathRoute<>("/one/*", 1L),
                new PathRoute<>("/one/two/*", 2L),
                new PathRoute<>("/one/two/three/*", 3L),
                new PathRoute<>("*.view", 4L),
                new PathRoute<>("*.form", 5L));

        Assertions.assertEquals(0L, matcher.apply("/one/request", routes));
        Assertions.assertEquals(0L, matcher.apply("/one/two/request", routes));
        Assertions.assertEquals(0L, matcher.apply("/one/two/three/request", routes));
        Assertions.assertEquals(0L, matcher.apply("default/request", routes));
        Assertions.assertEquals(0L, matcher.apply("that.view", routes));
        Assertions.assertEquals(0L, matcher.apply("that.form", routes));
        Assertions.assertEquals(0L, matcher.apply("whatever", routes));

        final List<PathRoute<String, Long>> routes2 = Arrays.asList(
                new PathRoute<>("/one/two/three/*", 3L),
                new PathRoute<>("/one/two/*", 2L),
                new PathRoute<>("/one/*", 1L),
                new PathRoute<>("*.view", 4L),
                new PathRoute<>("*.form", 5L),
                new PathRoute<>("*", 0L));

        Assertions.assertEquals(3L, matcher.apply("/one/two/three/request", routes2));
        Assertions.assertEquals(2L, matcher.apply("/one/two/request", routes2));
        Assertions.assertEquals(1L, matcher.apply("/one/request", routes2));
        Assertions.assertEquals(0L, matcher.apply("default/request", routes2));
        Assertions.assertEquals(4L, matcher.apply("that.view", routes2));
        Assertions.assertEquals(5L, matcher.apply("that.form", routes2));
        Assertions.assertEquals(0L, matcher.apply("whatever", routes2));
    }

    @Test
    void testOrderedWildCardMatchingSuffixPrefixPrecedence() {
        final UriPathRouter.OrderedMatcher<Long> matcher = UriPathRouter.OrderedMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("/ma*", 1L),
                new PathRoute<>("*tch", 2L));
        Assertions.assertEquals(1L, matcher.apply("/match", routes));
    }

    @Test
    void testOrderedStarAndExact() {
        final UriPathRouter.OrderedMatcher<Long> matcher = UriPathRouter.OrderedMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("*", 0L),
                new PathRoute<>("exact", 1L));
        Assertions.assertEquals(0L, matcher.apply("exact", routes));
    }

    @Test
    void testOrderedExactAndStar() {
        final UriPathRouter.OrderedMatcher<Long> matcher = UriPathRouter.OrderedMatcher.getInstance();
        final List<PathRoute<String, Long>> routes = Arrays.asList(
                new PathRoute<>("exact", 1L),
                new PathRoute<>("*", 0L));
        Assertions.assertEquals(1L, matcher.apply("exact", routes));
    }

    @Test
    void testRegExMatching() {
        final UriPathRouter.RegexMatcher<Long> matcher = UriPathRouter.RegexMatcher.getInstance();
        final List<PathRoute<Pattern, Long>> routes = Arrays.asList(
                new PathRoute<>(Pattern.compile("/one/two/three/.*"), 3L),
                new PathRoute<>(Pattern.compile("/one/two/.*"), 2L),
                new PathRoute<>(Pattern.compile("/one/.*"), 1L),
                new PathRoute<>(Pattern.compile(".*\\.view"), 4L),
                new PathRoute<>(Pattern.compile(".*\\.form"), 5L),
                new PathRoute<>(Pattern.compile(".*"), 0L));

        Assertions.assertEquals(1L, matcher.apply("/one/request", routes));
        Assertions.assertEquals(2L, matcher.apply("/one/two/request", routes));
        Assertions.assertEquals(3L, matcher.apply("/one/two/three/request", routes));
        Assertions.assertEquals(4L, matcher.apply("/that.view", routes));
        Assertions.assertEquals(5L, matcher.apply("/that.form", routes));
        Assertions.assertEquals(0L, matcher.apply("/default/request", routes));
    }

    @Test
    void testRegExWildCardMatchingSuffixPrefixPrecedence() {
        final UriPathRouter.RegexMatcher<Long> matcher = UriPathRouter.RegexMatcher.getInstance();
        final List<PathRoute<Pattern, Long>> routes = Arrays.asList(
                new PathRoute<>(Pattern.compile("/ma.*"), 1L),
                new PathRoute<>(Pattern.compile(".*tch"), 2L));
        Assertions.assertEquals(1L, matcher.apply("/match", routes));
    }

}
