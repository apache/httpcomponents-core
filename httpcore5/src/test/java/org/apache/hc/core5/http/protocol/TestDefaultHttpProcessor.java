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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDefaultHttpProcessor {

    @Test
    void processesRequestAndResponseInterceptorsInOrder() throws Exception {
        final List<String> events = new ArrayList<>();
        final HttpRequestInterceptor r1 = (request, entity, context) -> events.add("r1");
        final HttpRequestInterceptor r2 = (request, entity, context) -> events.add("r2");
        final HttpResponseInterceptor s1 = (response, entity, context) -> events.add("s1");
        final HttpResponseInterceptor s2 = (response, entity, context) -> events.add("s2");

        final DefaultHttpProcessor processor = new DefaultHttpProcessor(
                new HttpRequestInterceptor[] { r1, r2 },
                new HttpResponseInterceptor[] { s1, s2 });

        processor.process(new BasicHttpRequest("GET", "/"), null, HttpCoreContext.create());
        processor.process(new BasicHttpResponse(200), null, HttpCoreContext.create());

        Assertions.assertEquals(Arrays.asList("r1", "r2", "s1", "s2"), events);
    }

    @Test
    void copiesInterceptorArrays() throws Exception {
        final List<String> events = new ArrayList<>();
        final HttpRequestInterceptor original = (request, entity, context) -> events.add("orig");
        final HttpRequestInterceptor replacement = (request, entity, context) -> events.add("new");
        final HttpRequestInterceptor[] requestInterceptors = new HttpRequestInterceptor[] { original };

        final DefaultHttpProcessor processor = new DefaultHttpProcessor(requestInterceptors, null);
        requestInterceptors[0] = replacement;

        processor.process(new BasicHttpRequest("GET", "/"), null, HttpCoreContext.create());

        Assertions.assertEquals(Arrays.asList("orig"), events);
    }

    @Test
    void handlesEmptyInterceptors() throws Exception {
        final DefaultHttpProcessor processor = new DefaultHttpProcessor((HttpRequestInterceptor[]) null);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(200);
        final EntityDetails entity = null;

        processor.process(request, entity, HttpCoreContext.create());
        processor.process(response, entity, HttpCoreContext.create());
    }

    @Test
    void listConstructorBuildsChains() throws Exception {
        final List<String> events = new ArrayList<>();
        final List<HttpRequestInterceptor> req = Arrays.asList(
                (r, e, c) -> events.add("r1"),
                (r, e, c) -> events.add("r2"));
        final List<HttpResponseInterceptor> res = Arrays.asList(
                (r, e, c) -> events.add("s1"));

        final DefaultHttpProcessor processor = new DefaultHttpProcessor(req, res);
        processor.process(new BasicHttpRequest("GET", "/"), null, HttpCoreContext.create());
        processor.process(new BasicHttpResponse(200), null, HttpCoreContext.create());

        Assertions.assertEquals(Arrays.asList("r1", "r2", "s1"), events);
    }

    @Test
    void exceptionsPropagate() {
        final HttpRequestInterceptor interceptor = new HttpRequestInterceptor() {
            @Override
            public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
                    throws HttpException {
                throw new HttpException("boom");
            }
        };
        final DefaultHttpProcessor processor = new DefaultHttpProcessor(interceptor);

        Assertions.assertThrows(HttpException.class, () ->
                processor.process(new BasicHttpRequest("GET", "/"), null, HttpCoreContext.create()));
    }

}
