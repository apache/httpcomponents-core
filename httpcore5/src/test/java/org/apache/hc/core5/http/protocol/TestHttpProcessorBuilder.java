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

import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHttpProcessorBuilder {

    @Test
    void buildsRequestChainInOrder() throws Exception {
        final List<String> events = new ArrayList<>();
        final HttpRequestInterceptor r1 = (r, e, c) -> events.add("r1");
        final HttpRequestInterceptor r2 = (r, e, c) -> events.add("r2");
        final HttpRequestInterceptor r3 = (r, e, c) -> events.add("r3");

        final HttpProcessor processor = HttpProcessorBuilder.create()
                .addFirst(r2)
                .addLast(r3)
                .addFirst(r1)
                .add((HttpRequestInterceptor) null)
                .build();

        processor.process(new BasicHttpRequest("GET", "/"), null, HttpCoreContext.create());

        Assertions.assertEquals(Arrays.asList("r1", "r2", "r3"), events);
    }

    @Test
    void buildsResponseChainInOrder() throws Exception {
        final List<String> events = new ArrayList<>();
        final HttpResponseInterceptor s1 = (r, e, c) -> events.add("s1");
        final HttpResponseInterceptor s2 = (r, e, c) -> events.add("s2");

        final HttpProcessor processor = HttpProcessorBuilder.create()
                .addAllFirst(s2)
                .addLast(s1)
                .addAll((HttpResponseInterceptor[]) null)
                .build();

        processor.process(new BasicHttpResponse(200), null, HttpCoreContext.create());

        Assertions.assertEquals(Arrays.asList("s2", "s1"), events);
    }

    @Test
    void buildsEmptyProcessor() throws Exception {
        final HttpProcessor processor = HttpProcessorBuilder.create().build();
        processor.process(new BasicHttpRequest("GET", "/"), null, HttpCoreContext.create());
        processor.process(new BasicHttpResponse(200), null, HttpCoreContext.create());
    }

}
