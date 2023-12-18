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

package org.apache.hc.core5.testing.classic;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class ClassicHttpCoreTransportTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private final URIScheme scheme;

    public ClassicHttpCoreTransportTest(final URIScheme scheme) {
        this.scheme = scheme;
    }

    abstract HttpServer serverStart() throws IOException;

    abstract HttpRequester clientStart() throws IOException;

    @Test
    public void testSequentialRequests() throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response1.getEntity());
            assertThat(body1, CoreMatchers.equalTo("some stuff"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/other-stuff");
        request2.setEntity(new StringEntity("some other stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body2 = EntityUtils.toString(response2.getEntity());
            assertThat(body2, CoreMatchers.equalTo("some other stuff"));
        }
        final ClassicHttpRequest request3 = new BasicClassicHttpRequest(Method.POST, "/more-stuff");
        request3.setEntity(new StringEntity("some more stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response3 = requester.execute(target, request3, TIMEOUT, context)) {
            assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body3 = EntityUtils.toString(response3.getEntity());
            assertThat(body3, CoreMatchers.equalTo("some more stuff"));
        }
    }

    @Test
    public void testSequentialRequestsNonPersistentConnection() throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/no-keep-alive/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response1.getEntity());
            assertThat(body1, CoreMatchers.equalTo("some stuff"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/no-keep-alive/other-stuff");
        request2.setEntity(new StringEntity("some other stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body2 = EntityUtils.toString(response2.getEntity());
            assertThat(body2, CoreMatchers.equalTo("some other stuff"));
        }
        final ClassicHttpRequest request3 = new BasicClassicHttpRequest(Method.POST, "/no-keep-alive/more-stuff");
        request3.setEntity(new StringEntity("some more stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response3 = requester.execute(target, request3, TIMEOUT, context)) {
            assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body3 = EntityUtils.toString(response3.getEntity());
            assertThat(body3, CoreMatchers.equalTo("some more stuff"));
        }
    }

    @Test
    public void testMultiThreadedRequests() throws Exception {
        final HttpServer server = serverStart();
        final HttpRequester requester = clientStart();

        final int c = 10;
        final CountDownLatch latch = new CountDownLatch(c);
        final AtomicLong n = new AtomicLong(c + 100);
        final AtomicReference<AssertionError> exRef = new AtomicReference<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(c);
        try {
            final HttpHost target = new HttpHost(scheme.id, "localhost", server.getLocalPort());
            for (int i = 0; i < c; i++) {
                executorService.execute(() -> {
                    try {
                        while (n.decrementAndGet() > 0) {
                            try {
                                final HttpCoreContext context = HttpCoreContext.create();
                                final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
                                request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
                                requester.execute(target, request1, TIMEOUT, context, response -> {
                                    Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                                    Assertions.assertEquals("some stuff", EntityUtils.toString(response.getEntity()));
                                    return null;
                                });
                            } catch (final Exception ex) {
                                Assertions.fail(ex);
                            }
                        }
                    } catch (final AssertionError ex) {
                        exRef.compareAndSet(null, ex);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            Assertions.assertTrue(latch.await(5, TimeUnit.MINUTES));
        } finally {
            executorService.shutdownNow();
        }

        final AssertionError assertionError = exRef.get();
        if (assertionError != null) {
            throw assertionError;
        }
    }

}
