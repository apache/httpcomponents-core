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

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestRequestHandlerRegistry {

    private RequestHandlerRegistry<String> handlerRegistry;
    private HttpContext context;

    @Before
    public void setUp() {
        handlerRegistry = new RequestHandlerRegistry<>("myhost", UriPatternMatcher::new);
        context = new BasicHttpContext();
    }

    @Test
    public void testResolveByRequestUri() throws Exception {
        handlerRegistry.register(null, "/test*", "stuff");
        Assert.assertNotEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, "/"), context));
        Assert.assertNotEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, "/abc"), context));
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, "/test"), context));
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, "/testabc"), context));
    }

    @Test
    public void testByRequestUriWithQuery() throws Exception {
        handlerRegistry.register(null, "/test*", "stuff");
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, "/test?test=a"), context));
        Assert.assertNotEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, "/tes?test=a"), context));
    }

    @Test
    public void testResolveByHostnameAndRequestUri() throws Exception {
        handlerRegistry.register("myhost", "/test*", "stuff");
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, new HttpHost("myhost"), "/test"), context));
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, new HttpHost("MyHost"), "/testabc"), context));
    }

    @Test
    public void testResolveByLocalhostAndRequestUri() throws Exception {
        handlerRegistry.register("myhost", "/test*", "stuff");
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, new HttpHost("localhost"), "/test"), context));
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, new HttpHost("LocalHost"), "/testabc"), context));
        Assert.assertEquals("stuff", handlerRegistry.resolve(new BasicHttpRequest(Method.GET, new HttpHost("127.0.0.1"), "/testabc"), context));
    }

    @Test
    public void testResolveByUnknownHostname() throws Exception {
        handlerRegistry.register("myhost", "/test*", "stuff");
        Assert.assertThrows(MisdirectedRequestException.class, () ->
                handlerRegistry.resolve(new BasicHttpRequest(Method.GET, new HttpHost("otherhost"), "/test"), context));
    }

}
