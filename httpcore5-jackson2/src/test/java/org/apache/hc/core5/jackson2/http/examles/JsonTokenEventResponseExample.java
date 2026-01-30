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
package org.apache.hc.core5.jackson2.http.examles;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.jackson2.JsonTokenEventHandler;
import org.apache.hc.core5.jackson2.http.JsonResponseConsumers;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

public class JsonTokenEventResponseExample {

    public static void main(final String... args) throws Exception {
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(5, TimeUnit.SECONDS)
                .build();

        // Create and start requester
        final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP requester shutting down");
            requester.close(CloseMode.GRACEFUL);
        }));
        requester.start();

        final URI uri = URI.create("http://httpbin.org/get");

        System.out.println("Executing GET " + uri);
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final Future<?> future = requester.execute(
                AsyncRequestBuilder.get(uri)
                        .addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString())
                        .build(),
                JsonResponseConsumers.create(
                        objectMapper.getFactory(),
                        messageHead -> System.out.println("Response status: " + messageHead.getCode()),
                        error -> System.out.println("Error: " + error),
                        new JsonTokenEventHandler() {

                            @Override
                            public void objectStart() {
                                System.out.print("object start/");
                            }

                            @Override
                            public void objectEnd() {
                                System.out.print("object end/");
                            }

                            @Override
                            public void arrayStart() {
                                System.out.print("array start/");
                            }

                            @Override
                            public void arrayEnd() {
                                System.out.print("array end/");
                            }

                            @Override
                            public void field(final String name) {
                                System.out.print(name + "=");
                            }

                            @Override
                            public void embeddedObject(final Object object) {
                                System.out.print(object + "/");
                            }

                            @Override
                            public void value(final String value) {
                                System.out.print("\"" + value + "\"/");
                            }

                            @Override
                            public void value(final int value) {
                                System.out.print(value + "/");
                            }

                            @Override
                            public void value(final long value) {
                                System.out.print(value + "/");
                            }

                            @Override
                            public void value(final double value) {
                                System.out.print(value + "/");
                            }

                            @Override
                            public void value(final boolean value) {
                                System.out.print(value + "/");
                            }

                            @Override
                            public void valueNull() {
                                System.out.print("null/");
                            }

                            @Override
                            public void endOfStream() {
                                System.out.println("stream end/");
                            }

                        }),
                Timeout.ofMinutes(1),
                new FutureCallback<Void>() {

                    @Override
                    public void completed(final Void input) {
                        System.out.println("Object received");
                    }

                    @Override
                    public void failed(final Exception ex) {
                        ex.printStackTrace(System.out);
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        future.get();

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

}
