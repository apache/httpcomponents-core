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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.classic.ClassicToAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.priority.PriorityValue;
import org.apache.hc.core5.http2.protocol.H2RequestPriority;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/**
 * Example: HTTP/2 request that sets RFC 9218 Priority via context and emits the "Priority" header.
 * <p>
 * Requires H2Processors to include H2RequestPriority (client chain) and an HTTP/2 connection.
 */
@Experimental
public class ClassicH2PriorityExample {

    public static void main(final String[] args) throws Exception {

        // Force HTTP/2 and disable push for a cleaner demo
        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();

        // Ensure the client processor chain has H2RequestPriority inside (see H2Processors.customClient)
        final HttpAsyncRequester requester = org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap.bootstrap()
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

        // ---- Request 1: Explicit non-default priority -> header MUST be emitted
        executeWithPriority(clientEndpoint, target, "/httpbin/headers", PriorityValue.of(0, true));

        // ---- Request 2: RFC defaults -> header MUST be omitted by the interceptor
        executeWithPriority(clientEndpoint, target, "/httpbin/user-agent", PriorityValue.defaults());

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

    private static void executeWithPriority(
            final AsyncClientEndpoint endpoint,
            final HttpHost target,
            final String path,
            final PriorityValue priorityValue) throws Exception {

        final ClassicHttpRequest request = ClassicRequestBuilder.get()
                .setHttpHost(target)
                .setPath(path)
                .build();

        // Place the PriorityValue into the context so H2RequestPriority can format the header
        final HttpCoreContext ctx = HttpCoreContext.create();
        ctx.setAttribute(H2RequestPriority.ATTR_HTTP2_PRIORITY_VALUE, priorityValue);

        final ClassicToAsyncRequestProducer requestProducer = new ClassicToAsyncRequestProducer(request, Timeout.ofMinutes(1));
        final ClassicToAsyncResponseConsumer responseConsumer = new ClassicToAsyncResponseConsumer(Timeout.ofMinutes(1));

        endpoint.execute(requestProducer, responseConsumer, ctx, null);

        requestProducer.blockWaiting().execute();
        try (ClassicHttpResponse response = responseConsumer.blockWaiting()) {
            System.out.println(path + " -> " + response.getCode());
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                final ContentType ct = ContentType.parse(entity.getContentType());
                final Charset cs = ContentType.getCharset(ct, StandardCharsets.UTF_8);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), cs))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }
        }
    }
}