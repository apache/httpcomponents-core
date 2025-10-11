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
package org.apache.hc.core5.http2.examples;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.priority.PriorityFormatter;
import org.apache.hc.core5.http2.priority.PriorityValue;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/2 request that emits RFC 9218 Priority via "Priority" header.
 */
@Experimental
public class H2RequestExecutionWithPriorityExample {

    public static void main(final String[] args) throws Exception {

        // Force HTTP/2 and disable push for a cleaner demo
        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();

        final HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                .setH2Config(h2Config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setHttpProcessor(H2Processors.client()) // includes H2RequestPriority
                .setStreamListener(new H2StreamListener() {
                    @Override
                    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (final Header h : headers) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") << " + h);
                        }
                    }

                    @Override
                    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (final Header h : headers) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") >> " + h);
                        }
                    }

                    @Override
                    public void onFrameInput(final HttpConnection c, final int id, final RawFrame f) {
                    }

                    @Override
                    public void onFrameOutput(final HttpConnection c, final int id, final RawFrame f) {
                    }

                    @Override
                    public void onInputFlowControl(final HttpConnection c, final int id, final int d, final int s) {
                    }

                    @Override
                    public void onOutputFlowControl(final HttpConnection c, final int id, final int d, final int s) {
                    }
                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP requester shutting down");
            requester.close(CloseMode.GRACEFUL);
        }));
        requester.start();

        final HttpHost target = new HttpHost("nghttp2.org");
        final Future<AsyncClientEndpoint> future = requester.connect(target, Timeout.ofSeconds(30));
        final AsyncClientEndpoint clientEndpoint = future.get();

        final CountDownLatch latch = new CountDownLatch(2);

        // ---- Request 1: Explicit non-default priority -> header MUST be emitted
        executeWithPriority(clientEndpoint, target, "/httpbin/headers", PriorityValue.of(0, true), latch);

        // ---- Request 2: RFC defaults -> header MUST be omitted by the interceptor
        executeWithPriority(clientEndpoint, target, "/httpbin/user-agent", PriorityValue.defaults(), latch);

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

    private static void executeWithPriority(
            final AsyncClientEndpoint clientEndpoint,
            final HttpHost target,
            final String requestUri,
            final PriorityValue priorityValue,
            final CountDownLatch latch) throws Exception {

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath(requestUri)
                .build();
        if (!PriorityValue.defaults().equals(priorityValue)) {
            request.addHeader(PriorityFormatter.formatHeader(priorityValue));
        }

        clientEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                new FutureCallback<Message<HttpResponse, String>>() {

                    @Override
                    public void completed(final Message<HttpResponse, String> message) {
                        clientEndpoint.releaseAndReuse();
                        final HttpResponse response = message.getHead();
                        final String body = message.getBody();
                        System.out.println(requestUri + "->" + response.getCode());
                        System.out.println(body);
                        latch.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                        clientEndpoint.releaseAndDiscard();
                        System.out.println(requestUri + "->" + ex);
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        clientEndpoint.releaseAndDiscard();
                        System.out.println(requestUri + " cancelled");
                        latch.countDown();
                    }

                });
    }
}