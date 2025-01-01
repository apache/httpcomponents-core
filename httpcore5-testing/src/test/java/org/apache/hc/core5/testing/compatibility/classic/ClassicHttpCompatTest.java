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

package org.apache.hc.core5.testing.compatibility.classic;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.testing.Result;
import org.apache.hc.core5.testing.compatibility.ContainerImages;
import org.apache.hc.core5.testing.compatibility.TLSTestContexts;
import org.apache.hc.core5.testing.extension.classic.HttpRequesterResource;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class ClassicHttpCompatTest {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpHost target;
    @RegisterExtension
    private final HttpRequesterResource clientResource;

    public ClassicHttpCompatTest(final HttpHost target) {
        this.target = target;
        this.clientResource = new HttpRequesterResource();
        this.clientResource.configure(bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
                .setSslContext(TLSTestContexts.createClientSSLContext())
        );
    }

    void configure(final Consumer<RequesterBootstrap> customizer) {
        clientResource.configure(customizer);
    }

    HttpRequester client() {
        return clientResource.start();
    }

    @Test
    void test_sequential_requests() throws Exception {
        final HttpRequester requester = client();

        final int n = 20;
        for (int i = 0; i < n; i++) {
            final HttpCoreContext context = HttpCoreContext.create();
            final ClassicHttpRequest request = ClassicRequestBuilder.get("/aaa")
                    .setHttpHost(target)
                    .build();
            requester.execute(target, request, TIMEOUT, context, response -> {
                assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
                final String body1 = EntityUtils.toString(response.getEntity());
                assertThat(body1, CoreMatchers.equalTo(ContainerImages.AAA));
                return null;
            });
        }
    }

    @Test
    void test_multi_threaded_requests() throws Exception {
        final HttpRequester requester = client();

        final int c = 10;
        final AtomicInteger n = new AtomicInteger(20 * c);
        final CountDownLatch countDownLatch = new CountDownLatch(c);
        final Queue<Result<String>> resultQueue = new ConcurrentLinkedQueue<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(c);
        try {
            for (int i = 0; i < c; i++) {
                executorService.execute(() -> {
                    try {
                        while (n.decrementAndGet() > 0) {
                            final HttpCoreContext context = HttpCoreContext.create();
                            final ClassicHttpRequest request = ClassicRequestBuilder.get("/aaa")
                                    .setHttpHost(target)
                                    .build();
                            try {
                                requester.execute(target, request, TIMEOUT, context, response -> {
                                    resultQueue.add(new Result<>(
                                            request,
                                            response,
                                            EntityUtils.toString(response.getEntity())));
                                    return null;
                                });
                            } catch (final Exception ex) {
                                resultQueue.add(new Result<>(request, ex));
                            }
                        }
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }
            Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()), "Request executions have not completed in time");
            for (final Result<String> result : resultQueue) {
                if (result.isOK()) {
                    Assertions.assertNotNull(result.response);
                    Assertions.assertEquals(HttpStatus.SC_OK, result.response.getCode(), "Response message returned non 200 status");
                } else {
                    Assertions.fail(result.exception);
                }
            }
        } finally {
            executorService.shutdownNow();
        }
    }

}
