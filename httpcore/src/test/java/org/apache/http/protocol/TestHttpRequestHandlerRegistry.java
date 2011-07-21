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

package org.apache.http.protocol;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.junit.Test;

public class TestHttpRequestHandlerRegistry {

    private static class DummyHttpRequestHandler implements HttpRequestHandler {

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
        }

    }

    @Test
    public void testRegisterUnregister() throws Exception {
        HttpRequestHandler h1 = new DummyHttpRequestHandler();
        HttpRequestHandler h2 = new DummyHttpRequestHandler();
        HttpRequestHandler h3 = new DummyHttpRequestHandler();

        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register("/h1", h1);
        registry.register("/h2", h2);
        registry.register("/h3", h3);

        Map<String, HttpRequestHandler> handlers = registry.getHandlers();
        Assert.assertEquals(3, handlers.size());

        HttpRequestHandler h;

        h = registry.lookup("/h1");
        Assert.assertNotNull(h);
        Assert.assertTrue(h1 == h);
        h = registry.lookup("/h2");
        Assert.assertNotNull(h);
        Assert.assertTrue(h2 == h);
        h = registry.lookup("/h3");
        Assert.assertNotNull(h);
        Assert.assertTrue(h3 == h);

        registry.unregister("/h1");
        h = registry.lookup("/h1");
        Assert.assertNull(h);

        handlers = registry.getHandlers();
        Assert.assertEquals(2, handlers.size());

        Map<String, HttpRequestHandler> map = new HashMap<String, HttpRequestHandler>();
        map.put("/a1", h1);
        map.put("/a2", h2);
        map.put("/a3", h3);
        registry.setHandlers(map);

        handlers = registry.getHandlers();
        Assert.assertEquals(3, handlers.size());

        h = registry.lookup("/h2");
        Assert.assertNull(h);
        h = registry.lookup("/h3");
        Assert.assertNull(h);

        h = registry.lookup("/a1");
        Assert.assertNotNull(h);
        Assert.assertTrue(h1 == h);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRegisterNull() throws Exception {
        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register(null, null);
    }

    @Test
    public void testWildCardMatching1() throws Exception {
        HttpRequestHandler h1 = new DummyHttpRequestHandler();
        HttpRequestHandler h2 = new DummyHttpRequestHandler();
        HttpRequestHandler h3 = new DummyHttpRequestHandler();
        HttpRequestHandler def = new DummyHttpRequestHandler();

        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register("*", def);
        registry.register("/one/*", h1);
        registry.register("/one/two/*", h2);
        registry.register("/one/two/three/*", h3);

        HttpRequestHandler h;

        h = registry.lookup("/one/request");
        Assert.assertNotNull(h);
        Assert.assertTrue(h1 == h);

        h = registry.lookup("/one/two/request");
        Assert.assertNotNull(h);
        Assert.assertTrue(h2 == h);

        h = registry.lookup("/one/two/three/request");
        Assert.assertNotNull(h);
        Assert.assertTrue(h3 == h);

        h = registry.lookup("default/request");
        Assert.assertNotNull(h);
        Assert.assertTrue(def == h);
    }

    @Test
    public void testWildCardMatching2() throws Exception {
        HttpRequestHandler h1 = new DummyHttpRequestHandler();
        HttpRequestHandler h2 = new DummyHttpRequestHandler();
        HttpRequestHandler def = new DummyHttpRequestHandler();

        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register("*", def);
        registry.register("*.view", h1);
        registry.register("*.form", h2);

        HttpRequestHandler h;

        h = registry.lookup("/that.view");
        Assert.assertNotNull(h);
        Assert.assertTrue(h1 == h);

        h = registry.lookup("/that.form");
        Assert.assertNotNull(h);
        Assert.assertTrue(h2 == h);

        h = registry.lookup("/whatever");
        Assert.assertNotNull(h);
        Assert.assertTrue(def == h);
    }

    @Test
    public void testWildCardMatchingWithQuery() throws Exception {
        HttpRequestHandler h1 = new DummyHttpRequestHandler();
        HttpRequestHandler h2 = new DummyHttpRequestHandler();
        HttpRequestHandler def = new DummyHttpRequestHandler();

        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register("*", def);
        registry.register("*.view", h1);
        registry.register("*.form", h2);

        HttpRequestHandler h;

        h = registry.lookup("/that.view?param=value");
        Assert.assertNotNull(h);
        Assert.assertTrue(h1 == h);

        h = registry.lookup("/that.form?whatever");
        Assert.assertNotNull(h);
        Assert.assertTrue(h2 == h);

        h = registry.lookup("/whatever");
        Assert.assertNotNull(h);
        Assert.assertTrue(def == h);
    }

    @Test
    public void testSuffixPatternOverPrefixPatternMatch() throws Exception {
        HttpRequestHandler h1 = new DummyHttpRequestHandler();
        HttpRequestHandler h2 = new DummyHttpRequestHandler();

        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register("/ma*", h1);
        registry.register("*tch", h2);

        HttpRequestHandler h;

        h = registry.lookup("/match");
        Assert.assertNotNull(h);
        Assert.assertTrue(h1 == h);
    }

    @Test
    public void testInvalidInput() throws Exception {
        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        try {
            registry.register(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            registry.register("", null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        registry.unregister(null);

        try {
            registry.setHandlers(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            registry.lookup(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}
