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

package org.apache.hc.core5.http.io;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestUriHttpRequestHandlerMapper {

    @Test
    public void testRegisterUnregister() throws Exception {
        final HttpRequestHandler h = Mockito.mock(HttpRequestHandler.class);

        final UriPatternMatcher<HttpRequestHandler> matcher = Mockito.spy(new UriPatternMatcher<HttpRequestHandler>());
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper(matcher);

        registry.register("/h1", h);
        registry.unregister("/h1");

        Mockito.verify(matcher).register("/h1", h);
        Mockito.verify(matcher).unregister("/h1");
    }

    @Test
    public void testLookup() throws Exception {
        final UriPatternMatcher<HttpRequestHandler> matcher = Mockito.spy(new UriPatternMatcher<HttpRequestHandler>());
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper(matcher);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpCoreContext context = HttpCoreContext.create();
        registry.lookup(request, context);
        registry.unregister("/h1");

        Mockito.verify(matcher).lookup("/");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRegisterNull() throws Exception {
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper();
        registry.register(null, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testLookupNull() throws Exception {
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper();
        registry.register(null, null);
    }

    @Test
    public void testWildCardMatchingWithQuery() throws Exception {
        final HttpRequestHandler h1 = Mockito.mock(HttpRequestHandler.class);
        final HttpRequestHandler h2 = Mockito.mock(HttpRequestHandler.class);
        final HttpRequestHandler def = Mockito.mock(HttpRequestHandler.class);

        final UriPatternMatcher<HttpRequestHandler> matcher = Mockito.spy(new UriPatternMatcher<HttpRequestHandler>());
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper(matcher);
        registry.register("*", def);
        registry.register("*.view", h1);
        registry.register("*.form", h2);

        HttpRequestHandler h;

        final HttpCoreContext context = HttpCoreContext.create();
        h = registry.lookup(new BasicHttpRequest("GET", "/that.view?param=value"), context);
        Assert.assertNotNull(h);
        Assert.assertTrue(h1 == h);

        h = registry.lookup(new BasicHttpRequest("GET", "/that.form?whatever"), context);
        Assert.assertNotNull(h);
        Assert.assertTrue(h2 == h);

        h = registry.lookup(new BasicHttpRequest("GET", "/whatever"), context);
        Assert.assertNotNull(h);
        Assert.assertTrue(def == h);
    }

}
