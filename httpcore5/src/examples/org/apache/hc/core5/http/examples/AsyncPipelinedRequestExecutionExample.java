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
package org.apache.hc.core5.http.examples;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.nio.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.impl.nio.bootstrap.PooledClientEndpoint;
import org.apache.hc.core5.http.impl.nio.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.nio.BasicRequestProducer;
import org.apache.hc.core5.http.nio.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;

/**
 * Example of asynchronous HTTP/1.1 request execution.
 */
public class AsyncPipelinedRequestExecutionExample {

    public static void main(String[] args) throws Exception {

        // Create and start requester
        final HttpAsyncRequester requester = RequesterBootstrap.bootstrap().create();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("HTTP requester shutting down");
                requester.shutdown(3, TimeUnit.SECONDS);
            }
        });
        requester.start();

        HttpHost target = new HttpHost("www.apache.org");
        String[] requestUris = new String[] {"/", "/foundation",
                "/foundation/how-it-works.html", "/foundation/getinvolved.html"};

        Future<PooledClientEndpoint> future = requester.connect(target, 5, TimeUnit.SECONDS);
        PooledClientEndpoint clientEndpoint = future.get();

        final CountDownLatch latch = new CountDownLatch(requestUris.length);
        for (final String requestUri: requestUris) {
            clientEndpoint.execute(
                    new BasicRequestProducer("GET", URI.create(requestUri)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> message) {
                            latch.countDown();
                            HttpResponse response = message.getHead();
                            System.out.println(requestUri + "->" + response.getCode());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            latch.countDown();
                            System.out.println(requestUri + "->" + ex);
                        }

                        @Override
                        public void cancelled() {
                            latch.countDown();
                            System.out.println(requestUri + " cancelled");
                        }

                    });
        }

        latch.await();

        // Manually release client endpoint when done !!!
        clientEndpoint.releaseResources();

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

}
