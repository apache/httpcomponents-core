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

import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestRequestRouter {

    @Test
    void testRequestRouting() throws Exception {
        final RequestRouter<Long> requestRouter = RequestRouter.<Long>builder(UriPatternType.URI_PATTERN)
                .addRoute("somehost.somedomain", "/*", 0L)
                .addRoute("someotherhost.somedomain", "/foo/*", 1L)
                .addRoute("somehost.somedomain", "/foo/*", 2L)
                .addRoute("somehost.somedomain", "/bar/*", 3L)
                .addRoute("somehost.somedomain", "/stuff", 4L)
                .build();

        final HttpCoreContext context = HttpCoreContext.create();
        Assertions.assertEquals(1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://someotherhost.somedomain/foo/blah").build(), context));
        Assertions.assertEquals(2L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost.somedomain/foo/blah").build(), context));
        Assertions.assertEquals(3L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost.somedomain/bar/blah").build(), context));
        Assertions.assertEquals(4L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost.somedomain/stuff").build(), context));
        Assertions.assertEquals(4L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost.somedomain/stuff?huh").build(), context));
        Assertions.assertEquals(0L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost.somedomain/stuffed").build(), context));
        Assertions.assertNull(requestRouter.resolve(
                BasicRequestBuilder.get("http://someotherhost.somedomain/stuff").build(), context));
        Assertions.assertThrows(MisdirectedRequestException.class, () -> requestRouter.resolve(
                        BasicRequestBuilder.get("http://somehere.in.pampa/stuff").build(), context));
    }

    @Test
    void testDefaultAuthorityResolution() throws Exception {
        final RequestRouter<Long> requestRouter = RequestRouter.<Long>builder(UriPatternType.URI_PATTERN)
                .addRoute(new URIAuthority("somehost", -1), "/*", 0L)
                .addRoute(new URIAuthority("somehost", 80), "/*", 1L)
                .addRoute(new URIAuthority("somehost", 8080), "/*", 2L)
                .addRoute(new URIAuthority("someotherhost", 80), "/*", 10L)
                .build();

        final HttpCoreContext context = HttpCoreContext.create();
        Assertions.assertEquals(0L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost/blah").build(), context));
        Assertions.assertEquals(10L, requestRouter.resolve(
                BasicRequestBuilder.get("http://someotherhost:80/blah").build(), context));
        Assertions.assertEquals(1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost:80/blah").build(), context));
        Assertions.assertEquals(2L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost:8080/blah").build(), context));
        Assertions.assertThrows(MisdirectedRequestException.class, () -> requestRouter.resolve(
                BasicRequestBuilder.get("http://somehere.in.pampa/stuff").build(), context));
    }

    @Test
    void testCustomAuthorityResolution() throws Exception {
        final RequestRouter<Long> requestRouter = RequestRouter.<Long>builder(UriPatternType.URI_PATTERN)
                .addRoute(new URIAuthority("somehost", -1), "/*", 1L)
                .addRoute(new URIAuthority("someotherhost", -1), "/*", 2L)
                .resolveAuthority((scheme, authority) -> authority != null ? new URIAuthority(authority.getHostName(), -1) : new URIAuthority("somehost"))
                .build();

        final HttpCoreContext context = HttpCoreContext.create();
        Assertions.assertEquals(1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost/blah").build(), context));
        Assertions.assertEquals(2L, requestRouter.resolve(
                BasicRequestBuilder.get("http://someotherhost:80/blah").build(), context));
        Assertions.assertEquals(1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost:80/blah").build(), context));
        Assertions.assertEquals(1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost:8080/blah").build(), context));
        Assertions.assertEquals(1L, requestRouter.resolve(
                BasicRequestBuilder.get("/blah").build(), context));
        Assertions.assertThrows(MisdirectedRequestException.class, () -> requestRouter.resolve(
                BasicRequestBuilder.get("http://somehere.in.pampa/stuff").build(), context));
    }

    @Test
    void testDownstreamResolution() throws Exception {
        final RequestRouter<Long> requestRouter = RequestRouter.<Long>builder(UriPatternType.URI_PATTERN)
                .addRoute(new URIAuthority("somehost", 80), "/*", 1L)
                .addRoute(new URIAuthority("someotherhost", 80), "/*", 10L)
                .resolveAuthority((scheme, authority) -> authority)
                .downstream((request, context) -> -1L)
                .build();

        final HttpCoreContext context = HttpCoreContext.create();
        Assertions.assertEquals(-1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost/blah").build(), context));
        Assertions.assertEquals(10L, requestRouter.resolve(
                BasicRequestBuilder.get("http://someotherhost:80/blah").build(), context));
        Assertions.assertEquals(1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost:80/blah").build(), context));
        Assertions.assertEquals(-1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehost:8080/blah").build(), context));
        Assertions.assertEquals(-1L, requestRouter.resolve(
                BasicRequestBuilder.get("/blah").build(), context));
        Assertions.assertEquals(-1L, requestRouter.resolve(
                BasicRequestBuilder.get("http://somehere.in.pampa/stuff").build(), context));
    }

}
