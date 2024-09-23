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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestPathPatternMatcher {

    @Test
    void testPathMatching() {
        final PathPatternMatcher matcher = PathPatternMatcher.INSTANCE;

        Assertions.assertTrue(matcher.match("/*", "/foo/request"));
        Assertions.assertTrue(matcher.match("/foo/*", "/foo/request"));
        Assertions.assertTrue(matcher.match("/foo/req*", "/foo/request"));
        Assertions.assertTrue(matcher.match("/foo/request", "/foo/request"));
        Assertions.assertFalse(matcher.match("/foo/request", "/foo/requesta"));
        Assertions.assertFalse(matcher.match("/foo/*", "foo/request"));
        Assertions.assertFalse(matcher.match("/foo/*", "/bar/foo"));
        Assertions.assertTrue(matcher.match("*/action.do", "/xxxxx/action.do"));
        Assertions.assertTrue(matcher.match("*/foo/action.do", "/xxxxx/foo/action.do"));
        Assertions.assertFalse(matcher.match("*.do", "foo.dont"));
    }

    @Test
    void testBetterMatch() {
        final PathPatternMatcher matcher = PathPatternMatcher.INSTANCE;

        Assertions.assertTrue(matcher.isBetter("/a*", "/*"));
        Assertions.assertTrue(matcher.isBetter("/a*", "*"));
        Assertions.assertTrue(matcher.isBetter("/*", "*"));
        Assertions.assertTrue(matcher.isBetter("/a/b*", "/a*"));
        Assertions.assertTrue(matcher.isBetter("/a/*", "/a/b"));
    }

}
