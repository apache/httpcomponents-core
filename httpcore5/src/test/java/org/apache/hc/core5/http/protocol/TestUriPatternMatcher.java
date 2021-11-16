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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestUriPatternMatcher {

    @Test
    public void testEntrySet() throws Exception {
        final Object h1 = new Object();
        final Object h2 = new Object();
        final Object h3 = new Object();

        final UriPatternMatcher<Object> matcher = new UriPatternMatcher<>();
        Assertions.assertEquals(0, matcher.entrySet().size());
        matcher.register("/h1", h1);
        Assertions.assertEquals(1, matcher.entrySet().size());
        matcher.register("/h2", h2);
        Assertions.assertEquals(2, matcher.entrySet().size());
        matcher.register("/h3", h3);
        Assertions.assertEquals(3, matcher.entrySet().size());
    }

    @Test
    public void testRegisterUnregister() throws Exception {
        final Object h1 = new Object();
        final Object h2 = new Object();
        final Object h3 = new Object();

        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        matcher.register("/h1", h1);
        matcher.register("/h2", h2);
        matcher.register("/h3", h3);

        Object h;

        h = matcher.lookup("/h1");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h1, h);
        h = matcher.lookup("/h2");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h2, h);
        h = matcher.lookup("/h3");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h3, h);

        matcher.unregister("/h1");
        h = matcher.lookup("/h1");
        Assertions.assertNull(h);
    }

    @Test
    public void testRegisterNull() throws Exception {
        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        Assertions.assertThrows(NullPointerException.class, () ->
                matcher.register(null, null));
    }

    @Test
    public void testWildCardMatching1() throws Exception {
        final Object h1 = new Object();
        final Object h2 = new Object();
        final Object h3 = new Object();
        final Object def = new Object();

        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        matcher.register("*", def);
        matcher.register("/one/*", h1);
        matcher.register("/one/two/*", h2);
        matcher.register("/one/two/three/*", h3);

        Object h;

        h = matcher.lookup("/one/request");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h1, h);

        h = matcher.lookup("/one/two/request");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h2, h);

        h = matcher.lookup("/one/two/three/request");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h3, h);

        h = matcher.lookup("default/request");
        Assertions.assertNotNull(h);
        Assertions.assertSame(def, h);
    }

    @Test
    public void testWildCardMatching2() throws Exception {
        final Object h1 = new Object();
        final Object h2 = new Object();
        final Object def = new Object();

        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        matcher.register("*", def);
        matcher.register("*.view", h1);
        matcher.register("*.form", h2);

        Object h;

        h = matcher.lookup("/that.view");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h1, h);

        h = matcher.lookup("/that.form");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h2, h);

        h = matcher.lookup("/whatever");
        Assertions.assertNotNull(h);
        Assertions.assertSame(def, h);
    }

    @Test
    public void testSuffixPatternOverPrefixPatternMatch() throws Exception {
        final Object h1 = new Object();
        final Object h2 = new Object();

        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        matcher.register("/ma*", h1);
        matcher.register("*tch", h2);

        final Object h = matcher.lookup("/match");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h1, h);
    }

    @Test
    public void testRegisterInvalidInput() throws Exception {
        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        Assertions.assertThrows(NullPointerException.class, () ->
                matcher.register(null, null));
    }

    @Test
    public void testLookupInvalidInput() throws Exception {
        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        Assertions.assertThrows(NullPointerException.class, () ->
                matcher.lookup(null));
    }

    @Test
    public void testMatchExact() {
        final Object h1 = new Object();
        final Object h2 = new Object();

        final LookupRegistry<Object> matcher = new UriPatternMatcher<>();
        matcher.register("exact", h1);
        matcher.register("*", h2);

        final Object h = matcher.lookup("exact");
        Assertions.assertNotNull(h);
        Assertions.assertSame(h1, h);
    }

}
